package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReactionsTest {
    private val like = Reaction.Emoji("\uD83D\uDC4D")
    private val fire = Reaction.Emoji("\uD83D\uDD25")

    @Test
    fun aTapChoosesReplacesOrRemovesTheOwnReaction() {
        val counts = listOf(ReactionCount(like, 2, chosen = true), ReactionCount(Reaction.Paid, 5, chosen = true), ReactionCount(fire, 1))
        assertEquals(emptyList(), ownReactionsAfter(counts, like))
        assertEquals(listOf(fire), ownReactionsAfter(counts, fire))
        assertEquals(listOf(like), ownReactionsAfter(listOf(ReactionCount(fire, 1)), like))
    }

    @Test
    fun ownReactionsMoveTheCounts() {
        val counts = listOf(ReactionCount(like, 2, chosen = true), ReactionCount(Reaction.Paid, 5, chosen = true), ReactionCount(fire, 1, chosen = false))
        assertEquals(
            listOf(ReactionCount(like, 1), ReactionCount(Reaction.Paid, 5, chosen = true), ReactionCount(fire, 2, chosen = true, order = 0)),
            withOwnReactions(counts, listOf(fire)),
        )
        assertEquals(listOf(ReactionCount(like, 1, chosen = true, order = 0)), withOwnReactions(listOf(ReactionCount(fire, 1, chosen = true)), listOf(like)))
    }

    @Test
    fun premiumAddsReactionsUpToItsLimitAndThePickerOrdersTopFirst() {
        val heart = Reaction.Emoji("\u2764\uFE0F")
        val two = listOf(ReactionCount(like, 1, chosen = true, order = 0), ReactionCount(fire, 1, chosen = true, order = 1))
        assertEquals(listOf(like, fire, heart), ownReactionsAfter(two, heart, max = 3))
        assertEquals(listOf(fire, heart), ownReactionsAfter(two, heart, max = 2))
        assertEquals(listOf(fire), ownReactionsAfter(two, like, max = 3))
        val custom = Reaction.CustomEmoji(7)
        val art = listOf(ReactionArt(like, "like"), ReactionArt(fire, "fire"), ReactionArt(heart, "heart", premium = true))
        assertEquals(listOf(fire, like), pickerReactions(listOf(fire, custom), art, ChatReactions(custom = true), premium = false))
        assertEquals(listOf(fire, custom, like, heart), pickerReactions(listOf(fire, custom), art, ChatReactions(custom = true), premium = true))
        assertEquals(listOf(fire, custom), pickerReactions(emptyList(), art, ChatReactions(all = false, some = listOf(fire, custom)), premium = true))
    }

    @Test
    fun minUpdatesKeepTheOwnChoice() {
        val before = listOf(ReactionCount(like, 1, chosen = true))
        val fresh = listOf(ReactionCount(like, 3), ReactionCount(fire, 1))
        assertEquals(listOf(ReactionCount(like, 3, chosen = true), ReactionCount(fire, 1)), reactionsUpdated(before, fresh, min = true))
        assertEquals(fresh, reactionsUpdated(before, fresh, min = false))
    }

    @Test
    fun smallCountsWithKnownReactorsShowAvatars() {
        val alice = Reactor(501, "Alice")
        val bob = Reactor(502, "Bob")
        assertTrue(ReactionCount(like, 2, recent = listOf(alice, bob)).showsReactors)
        assertFalse(ReactionCount(like, 3, recent = listOf(alice, bob)).showsReactors)
        assertFalse(ReactionCount(like, 4, recent = listOf(alice, bob, alice, bob)).showsReactors)
    }

    @Test
    fun chatsAllowEverythingSomeOrNothing() {
        val available = listOf(like, fire)
        assertEquals(available, ChatReactions().allowed(available))
        assertEquals(listOf(fire), ChatReactions(all = false, some = listOf(fire)).allowed(available))
        assertEquals(emptyList(), ChatReactions.None.allowed(available))
    }

    @Test
    fun customEmojiReactionsAreAskedForLikeCustomEmojiInTexts() {
        val message = MessageItem(1, "", out = false, date = 0, reactions = listOf(ReactionCount(Reaction.CustomEmoji(9), 1), ReactionCount(like, 1)))
        assertEquals(setOf(9L), customEmojiIds(listOf(message)))
    }

    @Test
    fun theLimitOfDifferentReactionsIsCheckedBeforeSending() {
        val like = Reaction.Emoji("a")
        val heart = Reaction.Emoji("b")
        val fire = Reaction.Emoji("c")
        val two = listOf(ReactionCount(like, 3), ReactionCount(heart, 1, chosen = true, order = 0))
        assertTrue(exceedsReactionLimit(two, withOwnReactions(two + ReactionCount(fire, 2), listOf(fire, heart)), 2))
        assertFalse(exceedsReactionLimit(two, withOwnReactions(two, listOf(like)), 2))
        assertFalse(exceedsReactionLimit(two, withOwnReactions(two, listOf(fire)), 2))
        assertFalse(exceedsReactionLimit(two, withOwnReactions(two, listOf(fire, heart)), null))
        val state = ChatReactionState(allowed = listOf(like, heart, fire), limit = 2)
        assertEquals(listOf(like, heart), state.offeredFor(two).allowed)
        assertEquals(listOf(like, heart, fire), state.offeredFor(two.take(1)).allowed)
        assertEquals(11, AppConfig().uniqueReactions)
    }
}
