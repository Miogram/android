package cn.ellerotta.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.ui.components.chats.previewText
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServiceTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addsNameTheAddedAndJoinsDoNot() {
        val lines = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                @Composable fun line(sender: String?, kind: ServiceKind, vararg added: String) {
                    lines += previewText(Preview(sender = sender, service = kind, added = added.toList()), PeerKind.Group).text
                }
                line("Alice", ServiceKind.Joined)
                line("Alice", ServiceKind.Added, "Bob")
                line("Alice", ServiceKind.Added, "Bob", "Carol")
                line("Alice", ServiceKind.Added, "Bob", "")
                line("Alice", ServiceKind.Added, "")
                line("", ServiceKind.Added, "Bob")
                line("Alice", ServiceKind.Added, "Bob", "Carol", "Dave")
                line("Alice", ServiceKind.Added, "Bob", "Carol", "Dave", "Erin")
                line("Alice", ServiceKind.Added)
            }
        }
        compose.waitForIdle()
        assertEquals(
            listOf(
                "Alice joined the group",
                "Alice added Bob",
                "Alice added Bob and Carol",
                "Alice added Bob and you",
                "Alice added you",
                "You added Bob",
                "Alice added Bob, Carol and 1 other",
                "Alice added Bob, Carol and 2 others",
                "Alice added members",
            ),
            lines,
        )
    }
}
