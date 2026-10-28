package app.miogram.data.telegram

import cn.ellerotta.core.model.ContactCard
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.liveSharers
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.GetRecentLocations
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputMediaGeoLive
import cn.ellerotta.ktproto.api.types.InputMediaVenue
import cn.ellerotta.ktproto.api.types.InputMediaGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaPoll
import cn.ellerotta.ktproto.api.types.InputPollAnswer
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.api.types.Poll
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.UpdateMessageID
import cn.ellerotta.ktproto.api.types.UpdateShortSentMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AttachRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun sendPoll(dialogKey: String, draft: PollDraft, replyTo: Int? = null, topicId: Int? = null) {
        val explanation = draft.explanation.trim().takeIf { draft.quiz && it.isNotEmpty() }
        val poll = Poll(
            id = 0,
            publicVoters = draft.publicVoters,
            multipleChoice = draft.multiple,
            quiz = draft.quiz,
            question = TextWithEntities(draft.question.trim(), emptyList()),
            answers = draft.filled.map { InputPollAnswer(TextWithEntities(it.text.trim(), emptyList())) },
            hash = 0,
        )
        val media = InputMediaPoll(
            poll = poll,
            correctAnswers = draft.correctIndex?.let(::listOf),
            solution = explanation,
            solutionEntities = explanation?.let { emptyList() },
        )
        send(dialogKey, media, replyTo, topicId)
    }

    suspend fun sendPlace(dialogKey: String, place: GeoPlace, replyTo: Int? = null, topicId: Int? = null) {
        val point = InputGeoPoint(place.latitude, place.longitude)
        val venue = place.venue
        val media = if (venue == null) {
            InputMediaGeoPoint(point)
        } else {
            InputMediaVenue(point, place.title.orEmpty(), place.address.orEmpty(), venue.provider, venue.id, venue.type)
        }
        send(dialogKey, media, replyTo, topicId)
    }

    suspend fun sendContact(dialogKey: String, card: ContactCard, replyTo: Int? = null, topicId: Int? = null) {
        send(dialogKey, InputMediaContact(card.phone, card.firstName, card.lastName, card.vcard), replyTo, topicId)
    }

    suspend fun startLive(dialogKey: String, place: GeoPlace, heading: Int?, seconds: Int, radius: Int?, replyTo: Int? = null, topicId: Int? = null): Int? {
        val randomId = Random.nextLong()
        val media = InputMediaGeoLive(
            geoPoint = InputGeoPoint(place.latitude, place.longitude),
            heading = heading?.let(::headingOf),
            period = LivePeriods.periodOf(seconds),
            proximityNotificationRadius = radius,
        )
        val sent = telegram.client().invoke(
            SendMedia(peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = media, message = "", randomId = randomId),
        )
        return sentIdOf(sent, randomId)
    }

    suspend fun updateLive(dialogKey: String, id: Int, place: GeoPlace, heading: Int?, radius: Int? = null) {
        edit(dialogKey, id, InputMediaGeoLive(geoPoint = InputGeoPoint(place.latitude, place.longitude), heading = heading?.let(::headingOf), proximityNotificationRadius = radius))
    }

    suspend fun stopLive(dialogKey: String, id: Int, place: GeoPlace) {
        edit(dialogKey, id, InputMediaGeoLive(stopped = true, geoPoint = InputGeoPoint(place.latitude, place.longitude)))
    }

    suspend fun recentLocations(dialogKey: String): List<LiveSharer> {
        val answer = telegram.client().invoke(GetRecentLocations(peer = registry.peer(dialogKey), limit = RECENT_LIMIT, hash = 0))
        val (messages, users, chats) = when (answer) {
            is Messages -> Triple(answer.messages, answer.users, answer.chats)
            is MessagesSlice -> Triple(answer.messages, answer.users, answer.chats)
            is ChannelMessages -> Triple(answer.messages, answer.users, answer.chats)
            else -> return emptyList()
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        return liveSharers(messages.mapNotNull { registry.itemOf(it, dialogKey) }, (System.currentTimeMillis() / 1000).toInt())
    }

    private suspend fun edit(dialogKey: String, id: Int, media: TypeInputMedia) {
        telegram.client().invoke(EditMessage(peer = registry.peer(dialogKey), id = id, media = media))
    }

    private suspend fun send(dialogKey: String, media: TypeInputMedia, replyTo: Int?, topicId: Int?) {
        telegram.client().invoke(
            SendMedia(peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = media, message = "", randomId = Random.nextLong()),
        )
    }

    private fun headingOf(degrees: Int): Int = (((degrees % 360) + 360) % 360).let { if (it == 0) 360 else it }

    private fun sentIdOf(updates: TypeUpdates, randomId: Long): Int? = when (updates) {
        is Updates -> updates.updates.firstNotNullOfOrNull { (it as? UpdateMessageID)?.takeIf { id -> id.randomId == randomId }?.id }
        is UpdatesCombined -> updates.updates.firstNotNullOfOrNull { (it as? UpdateMessageID)?.takeIf { id -> id.randomId == randomId }?.id }
        is UpdateShortSentMessage -> updates.id
        else -> null
    }

    private companion object {
        const val RECENT_LIMIT = 100
    }
}
