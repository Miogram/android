package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.InviteLink
import cn.ellerotta.core.model.InviteLinks
import cn.ellerotta.core.model.JoinRequest
import cn.ellerotta.core.model.NewInviteLink
import cn.ellerotta.core.ui.components.groups.InviteLinksScreen
import cn.ellerotta.core.ui.components.groups.JoinRequestsScreen
import cn.ellerotta.core.ui.components.groups.MembersScreen
import cn.ellerotta.core.ui.components.groups.TopicsScreen
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.MemberRole
import cn.ellerotta.core.model.Presence
import androidx.compose.ui.test.assertTextEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GroupsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun requestsAndRemovedGroupsShowANoteInsteadOfTheComposer() {
        var membership by mutableStateOf<Membership?>(Membership.RequestToJoin)
        var joins = 0
        compose.setContent { MioTheme { ChatScreen("Kotlin jobs", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, membership = membership, onJoin = { joins++ }) } }
        compose.onNodeWithText("Request to join").performClick()
        assertEquals(1, joins)
        membership = Membership.RequestSent
        compose.onNodeWithTag("requestSent").assertIsNotEnabled()
        membership = Membership.Removed
        compose.onNodeWithTag("notMember").assertIsNotEnabled()
        membership = null
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("messageInput")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("joinChat")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theChatMenuMutesArchivesAndLeavesAfterAsking() {
        val dialog = DialogItem("ch1", "Kotlin devs", kind = PeerKind.Group, muted = true)
        val actions = mutableListOf<ChatAction>()
        var left = 0
        compose.setContent {
            MioTheme {
                ChatScreen("Kotlin devs", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog, onDialogAction = { actions += it }, onLeave = { left++ })
            }
        }
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_Unmute").performClick()
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_Archive").performClick()
        assertEquals(listOf(ChatAction.Unmute, ChatAction.Archive), actions)

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_leave").performClick()
        compose.onNodeWithText("Leave Kotlin devs? You can join again if it is public or someone invites you").assertExists()
        assertEquals(0, left)
        compose.onNodeWithTag("confirmLeave").performClick()
        assertEquals(1, left)
    }

    @Test
    fun inviteLinksShowUsageRevokeAfterAskingAndCreateWithOptions() {
        val links = InviteLinks(
            primary = InviteLink("https://t.me/+Primary", primary = true, usage = 12),
            others = listOf(InviteLink("https://t.me/+Meetup", title = "meetup", usageLimit = 50, usage = 3, requested = 2)),
            revoked = listOf(InviteLink("https://t.me/+Old", revoked = true)),
        )
        val revoked = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val created = mutableListOf<NewInviteLink>()
        var requests = 0
        compose.setContent {
            MioTheme {
                InviteLinksScreen(links, requestsPending = 4, busy = false, onBack = {}, onCreate = { created += it }, onRevoke = { revoked += it }, onDelete = { deleted += it }, onOpenRequests = { requests++ })
            }
        }
        compose.onNodeWithText("t.me/+Primary").assertExists()
        compose.onNodeWithText("12 joined").assertExists()
        compose.onNodeWithText("4 requests waiting", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("joinRequests").performClick()
        assertEquals(1, requests)

        compose.onNodeWithTag("revokePrimary").performClick()
        compose.onNodeWithText("The link stops working and a new primary link replaces it").assertExists()
        compose.onNodeWithTag("confirmRevoke").performClick()
        assertEquals(listOf("https://t.me/+Primary"), revoked)

        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("link_https://t.me/+Meetup"))
        compose.onNodeWithText("3 of 50 joined · 2 requests waiting", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("link_https://t.me/+Meetup").performClick()
        compose.onNodeWithTag("revokeLink").performClick()
        compose.onNodeWithTag("confirmRevoke").performClick()
        assertEquals("https://t.me/+Meetup", revoked.last())

        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("delete_https://t.me/+Old"))
        compose.onNodeWithTag("delete_https://t.me/+Old").performClick()
        assertEquals(listOf("https://t.me/+Old"), deleted)

        compose.onNodeWithTag("inviteLinkList").performScrollToNode(hasTestTag("createLink"))
        compose.onNodeWithTag("createLink").performClick()
        compose.onNodeWithTag("inviteName").performTextInput("team")
        compose.onNodeWithTag("expiry_604800").performClick()
        compose.onNodeWithTag("limit_100").performClick()
        compose.onNodeWithTag("requestNeeded").performClick()
        compose.onNodeWithTag("limit_10").assertIsNotEnabled()
        compose.onNodeWithTag("confirmCreate").performClick()
        assertEquals(listOf(NewInviteLink("team", 604_800, null, requestNeeded = true)), created)
    }

    @Test
    fun joinRequestsApproveDeclineAndOpenTheProfile() {
        val carol = JoinRequest("u503", "Carol Writer", date = 1_790_000_000, about = "i write about kotlin")
        val decided = mutableListOf<Pair<String, Boolean>>()
        val opened = mutableListOf<String>()
        var requests by mutableStateOf(listOf(carol, JoinRequest("u701", "Requester 1", date = 1_789_999_940)))
        compose.setContent {
            MioTheme {
                JoinRequestsScreen(requests, emptyMap(), more = false, loaded = true, busy = false, onBack = {}, onDecide = { request, approve -> decided += request.userKey to approve }, onMore = {}, onOpenProfile = { opened += it.userKey })
            }
        }
        compose.onNodeWithText("i write about kotlin").assertExists()
        compose.onNodeWithText("Carol Writer").performClick()
        compose.onNodeWithTag("approve_u503").performClick()
        compose.onNodeWithTag("decline_u701").performClick()
        assertEquals(listOf("u503" to true, "u701" to false), decided)
        assertEquals(listOf("u503"), opened)
        requests = emptyList()
        compose.onNodeWithTag("noRequests").assertExists()
    }

    @Test
    fun membersShowRolesAndRemoveOrdinaryMembersAfterAsking() {
        val members = listOf(
            ChatMember("u1", "Test User", role = MemberRole.Owner, self = true, presence = Presence.Online),
            ChatMember("u2", "Alice", role = MemberRole.Admin, rank = "maintainer"),
            ChatMember("u3", "Bob"),
            ChatMember("u4", "Helper", bot = true, presence = Presence.Bot),
        )
        var canBan by mutableStateOf(true)
        var basicGroup by mutableStateOf(false)
        val removed = mutableListOf<Pair<String, Boolean>>()
        val queries = mutableListOf<String>()
        val opened = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                MembersScreen(
                    "Members", members, emptyMap(), query = "", more = false, loaded = true, busy = false, canBan = canBan, basicGroup = basicGroup,
                    onBack = {}, onQuery = { queries += it }, onMore = {}, onOpenProfile = { opened += it.userKey }, onRemove = { member, ban -> removed += member.userKey to ban },
                )
            }
        }
        compose.onNodeWithTag("role_u1", useUnmergedTree = true).assertTextEquals("owner")
        compose.onNodeWithTag("role_u2", useUnmergedTree = true).assertTextEquals("maintainer")
        compose.onNodeWithText("bot", useUnmergedTree = true).assertExists()
        assertTrue(listOf("u1", "u2").all { compose.onAllNodes(hasTestTag("memberMenu_$it")).fetchSemanticsNodes().isEmpty() })

        compose.onNodeWithTag("memberMenu_u3").performClick()
        compose.onNodeWithTag("ban_u3").performClick()
        compose.onNodeWithText("Bob is removed and cannot join again until an admin lifts the ban").assertExists()
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.onNodeWithTag("memberMenu_u4").performClick()
        compose.onNodeWithTag("remove_u4").performClick()
        compose.onNodeWithTag("confirmRemove").performClick()
        assertEquals(listOf("u3" to true, "u4" to false), removed)

        basicGroup = true
        compose.onNodeWithTag("memberMenu_u3").performClick()
        compose.onNodeWithTag("remove_u3").assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("ban_u3")).fetchSemanticsNodes().isEmpty())
        canBan = false
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("memberMenu_u3")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("memberSearch").performTextInput("al")
        assertEquals(listOf("al"), queries)
        compose.onNodeWithTag("member_u2").performClick()
        assertEquals(listOf("u2"), opened)
    }

    @Test
    fun topicsShowMarksOpenAndCreateTopics() {
        val topics = listOf(
            ForumTopic(10, "Releases", pinned = true, unread = 2, preview = Preview(text = "compose too", sender = "Bob")),
            ForumTopic(1, "General", preview = Preview(text = "general chatter")),
            ForumTopic(20, "Help", closed = true, pinned = true),
        )
        var canCreate by mutableStateOf(true)
        var shown by mutableStateOf(topics)
        val opened = mutableListOf<Int>()
        val created = mutableListOf<Pair<String, Int>>()
        compose.setContent {
            MioTheme {
                TopicsScreen(
                    "Kotlin forum", shown, emptyMap(), emptyMap(), more = false, loaded = true, busy = false, canCreate = canCreate,
                    onBack = {}, onOpenTopic = { opened += it.id }, onMore = {}, onCreate = { name, color -> created += name to color }, onOpenProfile = {},
                )
            }
        }
        compose.onNodeWithTag("topicUnread_10", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("pinned_10"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pinned_20", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("closed_20", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Bob: compose too", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("#", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("topic_1").performClick()
        assertEquals(listOf(1), opened)

        compose.onNodeWithTag("createTopic").performClick()
        compose.onNodeWithTag("confirmCreateTopic").assertIsNotEnabled()
        compose.onNodeWithTag("topicName").performTextInput("Kotlin 2.4")
        compose.onNodeWithTag("topicColor_ff93b2").performClick()
        compose.onNodeWithTag("confirmCreateTopic").performClick()
        assertEquals(listOf("Kotlin 2.4" to 0xFF93B2), created)

        canCreate = false
        shown = emptyList()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("createTopic")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("noTopics").assertExists()
    }
}
