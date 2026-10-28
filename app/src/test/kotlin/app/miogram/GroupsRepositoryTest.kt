package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_HASH
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeGroups.Companion.FAMILY_ID
import app.miogram.FakeGroups.Companion.JOBS_ID
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.Membership
import cn.ellerotta.ktproto.api.types.InputNotifyPeer
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputUserSelf
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import cn.ellerotta.core.model.NewInviteLink
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.ChannelParticipantsRecent
import cn.ellerotta.ktproto.api.types.ChannelParticipantsSearch
import cn.ellerotta.core.model.MemberRole
import cn.ellerotta.core.model.Presence
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel as KotlinChannel
import app.miogram.FakeForum.Companion.FORUM_ID
import app.miogram.FakeForum.Companion.HELP
import app.miogram.FakeForum.Companion.RELEASES
import app.miogram.FakeTelegram.Companion.EMOJI_STAR
import app.miogram.data.telegram.ChatEvent
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import kotlinx.coroutines.withTimeout
import org.junit.Test

class GroupsRepositoryTest {
    private fun withRepos(block: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            fake.groups.inDialogs = true
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                block(fake, repos)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun joinsLeavesAndMutes() = withRepos { fake, repos ->
        val dialogs = repos.dialogs.dialogs().dialogs.associateBy { it.key }
        val devs = assertNotNull(dialogs["ch$DEVS_ID"])
        val family = assertNotNull(dialogs["c$FAMILY_ID"])

        val devsInfo = assertNotNull(repos.channels.info(devs.key))
        assertEquals(Membership.Member, devsInfo.membership)
        assertTrue(devsInfo.creator && devsInfo.admin && devsInfo.canInvite && devsInfo.canBan)
        val familyInfo = assertNotNull(repos.channels.info(family.key))
        assertEquals(Membership.Member to true, familyInfo.membership to familyInfo.basicGroup)
        assertTrue(!familyInfo.admin && !familyInfo.canBan)
        assertNull(repos.channels.info("u${fake.selfId}"))

        val jobs = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/kotlinjobs")).dialog
        assertEquals(Membership.RequestToJoin, repos.channels.info(jobs.key)?.membership)
        assertEquals(Membership.RequestSent, repos.channels.join(jobs.key))
        assertTrue(JOBS_ID in fake.groups.joinRequests)

        repos.dialogs.setMuted(devs.key, true)
        val notify = assertIs<InputNotifyPeer>(fake.lastNotifySettings?.peer)
        assertEquals(DEVS_ID to DEVS_HASH, assertIs<InputPeerChannel>(notify.peer).let { it.channelId to it.accessHash })
        assertEquals(Int.MAX_VALUE, fake.lastNotifySettings?.settings?.muteUntil)
        assertTrue(DEVS_ID in fake.muted)

        repos.channels.leave(devs.key)
        assertEquals(listOf(DEVS_ID), fake.groups.leaveCalls)
        repos.channels.leave(family.key)
        val deleted = fake.groups.deleteChatUserCalls.single()
        assertEquals(FAMILY_ID to InputUserSelf, deleted.chatId to deleted.userId)
        assertEquals(Membership.Removed, repos.channels.info(family.key)?.membership)
        assertEquals(Membership.CanJoin, repos.channels.info(devs.key)?.membership)
        assertTrue(repos.dialogs.dialogs().dialogs.none { it.key == devs.key || it.key == family.key })
    }

    @Test
    fun managesInviteLinksAndJoinRequests() = withRepos { fake, repos ->
        val devs = "ch$DEVS_ID"
        repos.dialogs.dialogs()
        val invites = fake.groups.invites

        val links = repos.invites.links(devs)
        assertEquals("https://t.me/+DevsPrimary" to 12, links.primary?.link to links.primary?.usage)
        val meetup = links.others.single()
        assertEquals(Triple("meetup", 50, 3), Triple(meetup.title, meetup.usageLimit, meetup.usage))
        assertTrue(links.revoked.isEmpty())
        assertTrue(repos.profiles.profile(devs).let { it.canInvite && it.requestsPending == 35 })

        val before = (System.currentTimeMillis() / 1000).toInt()
        repos.invites.create(devs, NewInviteLink(" team ", expiresIn = 86_400, usageLimit = 10))
        val export = assertNotNull(invites.lastExport)
        assertEquals(Triple("team", 10, false), Triple(export.title, export.usageLimit, export.requestNeeded))
        assertTrue(assertNotNull(export.expireDate) in before + 86_400..before + 86_460)
        repos.invites.create(devs, NewInviteLink(usageLimit = 5, requestNeeded = true))
        assertEquals(true to null, invites.lastExport?.requestNeeded to invites.lastExport?.usageLimit)
        assertNull(invites.lastExport?.expireDate)

        repos.invites.revoke(devs, "https://t.me/+DevsPrimary")
        assertEquals(true, invites.editCalls.single().revoked)
        val after = repos.invites.links(devs)
        assertEquals("https://t.me/+DevsPrimary3", after.primary?.link)
        assertEquals(listOf("https://t.me/+DevsPrimary"), after.revoked.map { it.link })
        assertEquals(3, after.others.size)
        repos.invites.delete(devs, "https://t.me/+DevsPrimary")
        assertTrue(repos.invites.links(devs).revoked.isEmpty())

        val first = repos.invites.requests(devs)
        assertEquals(30 to 35, first.requests.size to first.total)
        assertEquals(Triple("u${fake.carol.id}", "Carol Writer", "i write about kotlin"), first.requests[0].let { Triple(it.userKey, it.name, it.about) })
        assertTrue(invites.importerCalls.last().let { it.requested && it.offsetDate == 0 && it.limit == 30 })
        val second = repos.invites.requests(devs, after = first.requests.last())
        assertEquals(5, second.requests.size)
        val paged = invites.importerCalls.last()
        assertEquals(first.requests.last().date to 729L, paged.offsetDate to assertIs<InputUser>(paged.offsetUser).userId)

        repos.invites.decide(devs, "u${fake.carol.id}", approve = true)
        repos.invites.decide(devs, second.requests.last().userKey, approve = false)
        assertEquals(listOf(true to fake.carol.id, false to 734L), invites.hideCalls.map { it.approved to assertIs<InputUser>(it.userId).userId })
        assertEquals(33, repos.invites.requests(devs).total)
    }

    @Test
    fun listsSearchesAndRemovesMembers() = withRepos { fake, repos ->
        val devs = "ch$DEVS_ID"
        val family = "c$FAMILY_ID"
        repos.dialogs.dialogs()
        val members = fake.groups.members
        assertTrue(repos.profiles.profile(devs).canViewMembers && repos.profiles.profile(family).canViewMembers)

        val first = repos.channels.members(devs)
        assertEquals(50 to 65, first.members.size to first.total)
        assertTrue(members.participantCalls.last().let { it.filter == ChannelParticipantsRecent && it.offset == 0 && it.limit == 50 })
        assertEquals(Triple(MemberRole.Owner, true, Presence.Online), first.members[0].let { Triple(it.role, it.self, it.presence) })
        assertEquals(Triple("Alice Example", MemberRole.Admin, "maintainer"), first.members[1].let { Triple(it.name, it.role, it.rank) })
        assertTrue(first.members[2].let { it.bot && it.presence == Presence.Bot && it.role == MemberRole.Member })
        val second = repos.channels.members(devs, offset = 50)
        assertEquals(15, second.members.size)
        assertEquals(50, members.participantCalls.last().offset)

        val found = repos.channels.members(devs, " carol ")
        assertEquals(listOf("u${fake.carol.id}"), found.members.map { it.userKey })
        assertEquals("carol", assertIs<ChannelParticipantsSearch>(members.participantCalls.last().filter).q)

        repos.channels.removeMember(devs, "u${fake.bob.id}", ban = false)
        assertEquals(listOf(true, false), members.bannedCalls.map { it.bannedRights.viewMessages })
        assertEquals(fake.bob.id, assertIs<InputPeerUser>(members.bannedCalls[0].participant).userId)
        assertTrue(members.bannedCalls.all { it.bannedRights.untilDate == 0 })
        repos.channels.removeMember(devs, "u${fake.carol.id}", ban = true)
        assertEquals(3, members.bannedCalls.size)
        assertEquals(setOf(fake.carol.id), members.banned)
        assertEquals(63, repos.channels.members(devs).total)

        val familyMembers = repos.channels.members(family)
        assertEquals(listOf(MemberRole.Owner, MemberRole.Member, MemberRole.Member), familyMembers.members.map { it.role })
        assertEquals(listOf("Bob"), repos.channels.members(family, "bo").members.map { it.name })
        repos.channels.removeMember(family, "u${fake.bob.id}", ban = false)
        assertEquals(fake.bob.id, assertIs<InputUser>(fake.groups.deleteChatUserCalls.single().userId).userId)
        assertEquals(2, repos.channels.members(family).total)
    }

    @Test
    fun forumTopicsPageSendIntoTopicsAndFollowThem() = withRepos { fake, repos ->
        val key = "ch$FORUM_ID"
        val forum = fake.groups.forum
        assertTrue(repos.dialogs.dialogs().dialogs.single { it.key == key }.forum)
        assertTrue(assertNotNull(repos.channels.info(key)).canManageTopics)
        assertTrue(!assertNotNull(repos.channels.info("ch$DEVS_ID")).canManageTopics)

        val first = repos.forum.topics(key)
        assertEquals(30 to 33, first.topics.size to first.total)
        assertEquals(listOf(RELEASES, ForumTopic.GENERAL_TOPIC, HELP), first.topics.take(3).map { it.id })
        val releases = first.topics[0]
        assertTrue(releases.pinned && releases.unread == 2 && releases.iconEmojiId == EMOJI_STAR)
        assertEquals("compose too" to "Bob", releases.preview.text to releases.preview.sender)
        assertTrue(first.topics[2].closed && first.topics[2].iconColor == 0xFB6F5F)
        assertTrue(forum.topicCalls.last().let { it.offsetDate == 0 && it.offsetId == 0 && it.offsetTopic == 0 && it.limit == 30 })
        val last = first.topics.last()
        assertEquals(3, repos.forum.topics(key, after = last).topics.size)
        assertEquals(Triple(last.date, last.topMessage, last.id), forum.topicCalls.last().let { Triple(it.offsetDate, it.offsetId, it.offsetTopic) })

        val history = repos.messages.history(key, topicId = RELEASES)
        assertEquals(listOf(10, 11, 12), history.map { it.id })
        assertTrue(history.all { it.topicId == RELEASES && it.replyTo == null })
        assertEquals(RELEASES to 0, forum.replyCalls.last().let { it.msgId to it.offsetId })
        assertEquals(listOf(2, 3), repos.messages.history(key, topicId = ForumTopic.GENERAL_TOPIC).map { it.id })
        repos.messages.historyAround(key, 11, topicId = RELEASES)
        assertEquals(Triple(RELEASES, 12, -25), forum.replyCalls.last().let { Triple(it.msgId, it.offsetId, it.addOffset) })

        repos.forum.read(key, RELEASES, 12)
        assertEquals(RELEASES to 12, forum.readCalls.single().let { it.msgId to it.readMaxId })
        assertEquals(0, repos.forum.topics(key).topics.first { it.id == RELEASES }.unread)

        coroutineScope {
            val live = KotlinChannel<ChatEvent>(KotlinChannel.UNLIMITED)
            val listening = launch { repos.updates.events().collect { live.send(it) } }
            delay(300)
            fun reply() = forum.sendCalls.last().replyTo as? InputReplyToMessage
            repos.messages.send(key, "into releases", topicId = RELEASES)
            assertEquals(RELEASES to null, reply()?.let { it.replyToMsgId to it.topMsgId })
            repos.messages.send(key, "an answer", replyTo = 11, topicId = RELEASES)
            assertEquals(11 to RELEASES, reply()?.let { it.replyToMsgId to it.topMsgId })
            repos.messages.send(key, "hello general", topicId = ForumTopic.GENERAL_TOPIC)
            assertNull(forum.sendCalls.last().replyTo)
            repos.messages.send(key, "general answer", replyTo = 3, topicId = ForumTopic.GENERAL_TOPIC)
            assertEquals(3 to null, reply()?.let { it.replyToMsgId to it.topMsgId })
            val sent = withTimeout(10_000) { nextNew(live) }
            assertEquals(Triple(key, "into releases", RELEASES), Triple(sent.key, sent.message.text, sent.message.topicId))
            val answer = withTimeout(10_000) { nextNew(live) }
            assertEquals(Triple(RELEASES, 11, true), Triple(answer.message.topicId, answer.message.replyTo, answer.message.out))

            withTimeout(10_000) { repeat(2) { nextNew(live) } }
            forum.push(HELP, "use collect")
            assertEquals(HELP to "use collect", withTimeout(10_000) { nextNew(live) }.message.let { it.topicId to it.text })
            forum.push(ForumTopic.GENERAL_TOPIC, "general news")
            assertNull(withTimeout(10_000) { nextNew(live) }.message.topicId)
            listening.cancel()
        }

        val created = repos.forum.create(key, " Kotlin 2.4 ", ForumTopic.COLORS[3])
        assertEquals("Kotlin 2.4" to 0x8EEE98, forum.createCalls.single().let { it.title to it.iconColor })
        assertEquals("Kotlin 2.4", created.title)
        assertTrue(forum.topics.any { it.id == created.id })

        val linked = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/kotlinforum/10/11"))
        assertEquals(Triple(RELEASES, 11, "Releases"), Triple(linked.topicId, linked.messageId, linked.topicTitle))
        val topicOnly = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/kotlinforum/20"))
        assertEquals(HELP to null, topicOnly.topicId to topicOnly.messageId)
        val inHelp = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/c/$FORUM_ID/21"))
        assertEquals(HELP to 21, inHelp.topicId to inHelp.messageId)
        val general = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/kotlinforum/3"))
        assertEquals(Triple(ForumTopic.GENERAL_TOPIC, 3, "General"), Triple(general.topicId, general.messageId, general.topicTitle))
        val private = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/c/$FORUM_ID/10/12"))
        assertEquals(RELEASES to 12, private.topicId to private.messageId)
        assertTrue(private.dialog.forum)
    }
}

private suspend fun nextNew(events: KotlinChannel<ChatEvent>): ChatEvent.NewMessage {
    while (true) {
        val event = events.receive()
        if (event is ChatEvent.NewMessage) return event
    }
}
