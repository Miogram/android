package cn.ellerotta.core.ui.components.attach

import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.core.model.PollOptionDraft
import cn.ellerotta.core.model.Presence

internal object AttachPreviewData {
    val emptyPoll = PollDraft()

    val poll = PollDraft(
        question = "Where do we meet on Friday?",
        options = listOf(PollOptionDraft(0, "The old pier"), PollOptionDraft(1, "Behind the station"), PollOptionDraft(2, "")),
        anonymous = false,
        nextId = 3,
    )

    val quiz = PollDraft(
        question = "What is the capital of Australia?",
        options = listOf(PollOptionDraft(0, "Sydney"), PollOptionDraft(1, "Canberra"), PollOptionDraft(2, "Melbourne")),
        quiz = true,
        correct = 1,
        explanation = "Canberra was built as a compromise between the two bigger cities.",
        nextId = 3,
    )

    val full = PollDraft(
        kind = PeerKind.Channel,
        question = "Pick your favourite",
        options = (0 until 10).map { PollOptionDraft(it, "Option number ${it + 1}") },
        multiple = true,
        nextId = 10,
    )

    val found = LocationShareState(LocationStatus.Found, GeoPlace(51.50735, -0.12776), accuracy = 8)

    val contacts = listOf(
        ContactItem(key = "u1", userId = 1, name = "Ada Lovelace", phone = "447946000123", presence = Presence.Online),
        ContactItem(key = "u2", userId = 2, name = "Grace Hopper", phone = "12025550147"),
        ContactItem(key = "u3", userId = 3, name = "Alan Turing", phone = "447700900461"),
    )
}
