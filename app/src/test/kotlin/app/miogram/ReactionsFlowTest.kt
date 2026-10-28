package app.miogram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.MessageReactions
import cn.ellerotta.ktproto.api.types.ReactionCount
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ReactionsFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aReactionIsPickedRemovedAndUpdatedLive() {
        val id = fake.addReactedMessage()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)

        val like = "reaction_${id}_${FakeTelegram.THUMBS_UP}"
        val fire = "reaction_${id}_${FakeTelegram.FIRE}"
        compose.waitForTag(like)
        compose.onNodeWithTag("reaction_${id}_paid").assertExists()

        compose.onNodeWithTag("message_$id").performTouchInput { longClick(Offset(centerX, 12f)) }
        compose.waitForTag("react_${FakeTelegram.FIRE}")
        compose.onNodeWithTag("react_${FakeTelegram.FIRE}").performClick()
        compose.waitUntilIdle { fake.lastSendReaction != null }
        assertEquals(listOf(ReactionEmoji(FakeTelegram.FIRE)), fake.lastSendReaction?.reaction)
        assertTrue(fake.lastSendReaction!!.addToRecent)
        compose.waitUntilIdle { selected(fire) }

        compose.onNodeWithTag(fire).performClick()
        compose.waitUntilIdle { fake.lastSendReaction?.reaction == null }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(fire)).fetchSemanticsNodes().isEmpty() }

        val counts = fake.othersReactions().results.map { if (it.reaction == ReactionEmoji(FakeTelegram.THUMBS_UP)) ReactionCount(reaction = it.reaction, count = 7) else it }
        runBlocking { fake.pushReactions(id, MessageReactions(results = counts)) }
        compose.waitUntilIdle { description(like) == "${FakeTelegram.THUMBS_UP}, 7 reactions" }
    }

    private fun selected(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.Selected) == true

    private fun description(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
}
