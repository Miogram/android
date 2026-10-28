package app.miogram

import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeGroups.Companion.FAMILY_ID
import app.miogram.FakeGroups.Companion.JOBS_ID
import app.miogram.FakeForum.Companion.FORUM_ID
import app.miogram.FakeForum.Companion.HELP
import app.miogram.FakeForum.Companion.RELEASES
import androidx.compose.ui.test.hasText
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import app.miogram.FakeForum.Companion.inTopic
import app.miogram.FakeTelegram.Companion.NOW
import androidx.compose.ui.test.onNodeWithText
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerUser
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.ChannelParticipantsSearch
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
class GroupsFlowTest {
    private val fake = FakeTelegram().apply { groups.inDialogs = true }
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

    private fun deliver(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), compose.activity, MainActivity::class.java)
        compose.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, intent) }
    }

    private fun openDialog(key: String) {
        compose.waitForTag("dialogs")
        compose.onNodeWithTag("dialogs").performScrollToKey(key)
        compose.onNodeWithTag("dialog_$key").performClick()
    }

    @Test
    fun aGroupWithJoinRequestsShowsTheSentRequest() {
        compose.logIn()
        deliver("https://t.me/kotlinjobs")
        compose.waitForText("Request to join")
        compose.onNodeWithTag("joinChat").performClick()
        compose.waitUntilIdle { JOBS_ID in fake.groups.joinRequests }
        compose.waitForTag("requestSent")
        assertTrue(compose.onAllNodes(hasTestTag("messageInput")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theChatMenuMutesAndLeaves() {
        compose.logIn()
        openDialog("ch$DEVS_ID")
        compose.waitForTag("messageInput")

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_Mute").performClick()
        compose.waitUntilIdle { DEVS_ID in fake.muted }

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.waitForTag("menu_Unmute")
        compose.onNodeWithTag("menu_leave").performClick()
        compose.onNodeWithTag("confirmLeave").performClick()
        compose.waitUntilIdle { fake.groups.leaveCalls == listOf(DEVS_ID) }
        compose.waitForTag("openChats")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("dialog_ch$DEVS_ID")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aBasicGroupIsLeftFromItsProfile() {
        compose.logIn()
        openDialog("c$FAMILY_ID")
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("leaveChat"))
        compose.onNodeWithTag("leaveChat").performClick()
        compose.onNodeWithTag("confirmLeave").performClick()
        compose.waitUntilIdle { fake.groups.deleteChatUserCalls.isNotEmpty() }
        val call = fake.groups.deleteChatUserCalls.single()
        assertEquals(FAMILY_ID to InputUserSelf, call.chatId to call.userId)
        compose.waitForTag("openChats")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("dialog_c$FAMILY_ID")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aSendersPhotoOpensTheirProfile() {
        compose.logIn()
        openDialog("ch$DEVS_ID")
        compose.waitForTag("senderAvatar_5")
        compose.onNodeWithTag("senderAvatar_5").performClick()
        compose.waitForTag("peerProfile")
        compose.waitForTag("personalChannel")
    }

    @Test
    fun adminsManageInviteLinksAndJoinRequests() {
        val invites = fake.groups.invites
        compose.logIn()
        openDialog("ch$DEVS_ID")
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("inviteLinks"))
        compose.waitForText("35 requests waiting")
        compose.onNodeWithTag("inviteLinks").performClick()

        compose.waitForText("t.me/+DevsPrimary")
        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("link_https://t.me/+DevsMeetup"))
        compose.waitForText("3 of 50 joined")
        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("copyPrimary"))
        compose.onNodeWithTag("copyPrimary").performClick()
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        compose.waitUntilIdle { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "https://t.me/+DevsPrimary" }

        compose.onNodeWithTag("createLink").performClick()
        compose.onNodeWithTag("inviteName").performTextInput("team")
        compose.onNodeWithTag("expiry_86400").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("limit_10").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("confirmCreate").performClick()
        compose.waitUntilIdle { invites.lastExport != null }
        assertEquals(Triple("team", 10, false), invites.lastExport?.let { Triple(it.title, it.usageLimit, it.requestNeeded) })
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("link_https://t.me/+DevsNew1")) }.isSuccess }
        compose.waitForText("team")

        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("revokePrimary"))
        compose.onNodeWithTag("revokePrimary").performClick()
        compose.onNodeWithTag("confirmRevoke").performClick()
        compose.waitUntilIdle { invites.editCalls.isNotEmpty() }
        compose.waitForText("t.me/+DevsPrimary2")

        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("joinRequests"))
        compose.onNodeWithTag("joinRequests").performClick()
        compose.waitForText("i write about kotlin")
        compose.onNodeWithTag("approve_u${fake.carol.id}").performClick()
        compose.waitUntilIdle { invites.hideCalls.isNotEmpty() }
        assertEquals(true, invites.hideCalls.single().approved)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("request_u${fake.carol.id}")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun adminsSearchAndRemoveMembers() {
        val members = fake.groups.members
        compose.logIn()
        openDialog("ch$DEVS_ID")
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("members"))
        compose.waitForText("65 members")
        compose.onNodeWithTag("members").performClick()

        compose.waitForTag("member_u${fake.alice.id}")
        compose.waitForText("maintainer")
        compose.waitForText("owner")
        compose.onNodeWithTag("memberList").performScrollToNode(hasTestTag("member_u945"))
        compose.waitUntilIdle { members.participantCalls.any { it.offset == 50 } }

        compose.onNodeWithTag("memberList").performScrollToNode(hasTestTag("memberSearch"))
        compose.onNodeWithTag("memberSearch").performTextInput("carol")
        compose.waitUntilIdle { (members.participantCalls.last().filter as? ChannelParticipantsSearch)?.q == "carol" }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("member_u${fake.bob.id}")).fetchSemanticsNodes().isEmpty() }
        compose.waitForTag("member_u${fake.carol.id}")
        compose.onNodeWithTag("memberMenu_u${fake.carol.id}").performClick()
        compose.onNodeWithTag("ban_u${fake.carol.id}").performClick()
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.waitUntilIdle { members.bannedCalls.isNotEmpty() }
        assertEquals(fake.carol.id to true, members.bannedCalls.single().let { (it.participant as InputPeerUser).userId to it.bannedRights.viewMessages })
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("member_u${fake.carol.id}")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("memberSearch").performTextClearance()
        compose.onNodeWithTag("memberSearch").performTextInput("alice")
        compose.waitForTag("member_u${fake.alice.id}")
        assertTrue(compose.onAllNodes(hasTestTag("memberMenu_u${fake.alice.id}")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("member_u${fake.alice.id}").performClick()
        compose.waitForText("Alice Example")
        compose.waitForTag("peerProfile")
    }

    @Test
    fun aForumOpensItsTopicsAndEachTopicItsOwnMessages() {
        val forum = fake.groups.forum
        compose.logIn()
        openDialog("ch$FORUM_ID")

        compose.waitForTag("topic_$RELEASES")
        compose.onNodeWithTag("topicUnread_$RELEASES", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("closed_$HELP", useUnmergedTree = true).assertExists()
        compose.waitUntilIdle { compose.onAllNodes(hasText("compose too", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("topicList").performScrollToNode(hasTestTag("topic_1027"))
        compose.waitUntilIdle { forum.topicCalls.any { it.offsetTopic == 1027 } }
        compose.onNodeWithTag("topicList").performScrollToNode(hasTestTag("topic_1030"))

        compose.onNodeWithTag("topicList").performScrollToNode(hasTestTag("topic_$RELEASES"))
        compose.onNodeWithTag("topic_$RELEASES").performClick()
        compose.waitForText("kotlin 2.3 is out")
        compose.onNode(hasText("Releases") and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Kotlin forum") and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasText("general chatter")).fetchSemanticsNodes().isEmpty())
        compose.waitUntilIdle { forum.readCalls.any { it.msgId == RELEASES && it.readMaxId == 12 } }
        val pageReady = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        forum.messages += Message(id = 4_999, fromId = PeerUser(fake.alice.id), peerId = PeerChannel(FORUM_ID), date = NOW, message = "missed while away", replyTo = inTopic(RELEASES))
        forum.beforeReplies = {
            pageReady.complete(Unit)
            release.await()
        }
        compose.onNodeWithTag("messageInput").performTextInput("thanks for the news")
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { forum.sendCalls.isNotEmpty() }
        val reply = forum.sendCalls.single().replyTo as InputReplyToMessage
        assertEquals(RELEASES to null, reply.replyToMsgId to reply.topMsgId)
        compose.waitUntilIdle { pageReady.isCompleted }

        runBlocking {
            forum.push(HELP, "help is elsewhere")
            forum.push(RELEASES, "kotlin 2.4 soon")
        }
        compose.waitForText("kotlin 2.4 soon")
        forum.beforeReplies = null
        release.complete(Unit)
        compose.waitForText("missed while away")
        compose.onNodeWithText("kotlin 2.4 soon").assertExists()
        assertTrue(compose.onAllNodes(hasText("help is elsewhere")).fetchSemanticsNodes().isEmpty())

        deliver("https://t.me/kotlinforum/$HELP/21")
        compose.waitForText("how do flows work")
        compose.waitForText("help is elsewhere")
        compose.waitForText("Help")
        assertTrue(compose.onAllNodes(hasText("kotlin 2.4 soon")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("createTopic")
        compose.onNodeWithTag("createTopic").performClick()
        compose.onNodeWithTag("topicName").performTextInput("Kotlin 2.4")
        compose.onNodeWithTag("topicColor_8eee98").performClick()
        compose.onNodeWithTag("confirmCreateTopic").performClick()
        compose.waitUntilIdle { forum.createCalls.isNotEmpty() }
        assertEquals("Kotlin 2.4" to 0x8EEE98, forum.createCalls.single().let { it.title to it.iconColor })
        compose.waitForTag("messageInput")
        compose.waitForText("Kotlin 2.4")
    }
}
