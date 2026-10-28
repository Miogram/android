package cn.ellerotta.core.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.reflect.ComposableMethod
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyPreviewComposes() {
        val previews = mioPreviews()
        assertTrue(previews.isNotEmpty(), "no previews found")
        var current by mutableStateOf<Pair<String, ComposableMethod>?>(null)
        var shown: String? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            current?.let { (name, method) ->
                key(name) {
                    method.invoke(currentComposer, null)
                    SideEffect { shown = name }
                }
            }
        }
        for (preview in previews) {
            try {
                current = preview
                Snapshot.sendApplyNotifications()
                compose.mainClock.advanceTimeBy(500)
                compose.waitForIdle()
            } catch (e: Throwable) {
                throw AssertionError("${preview.first} failed", e)
            }
            assertEquals(preview.first, shown)
        }
    }
}
