package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.GetRecentLocations
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.functions.upload.GetWebFile
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.InputWebFileGeoPointLocation
import cn.ellerotta.ktproto.api.types.storage.FilePng
import cn.ellerotta.ktproto.api.types.upload.WebFile
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputMediaGeoLive
import cn.ellerotta.ktproto.api.types.InputMediaGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaPoll
import cn.ellerotta.ktproto.api.types.InputMediaVenue
import cn.ellerotta.ktproto.api.types.MessageMediaVenue
import cn.ellerotta.ktproto.api.types.InputPollAnswer
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaContact
import cn.ellerotta.ktproto.api.types.MessageMediaGeo
import cn.ellerotta.ktproto.api.types.MessageMediaGeoLive
import cn.ellerotta.ktproto.api.types.MessageMediaPoll
import cn.ellerotta.ktproto.api.types.MessageActionGeoProximityReached
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Poll
import cn.ellerotta.ktproto.api.types.PollAnswer
import cn.ellerotta.ktproto.api.types.PollAnswerVoters
import cn.ellerotta.ktproto.api.types.PollResults
import cn.ellerotta.ktproto.api.types.UpdateEditMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.testing.FakeAnswer
import java.util.concurrent.CopyOnWriteArrayList

class FakeAttach(private val fake: FakeTelegram) {
    val sent: MutableList<SendMedia> = CopyOnWriteArrayList()

    val liveEdits: MutableList<EditMessage> = CopyOnWriteArrayList()

    val mapCalls: MutableList<Pair<Int, InputWebFileGeoPointLocation>> = CopyOnWriteArrayList()

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetWebFile.CONSTRUCTOR_ID) { request ->
            val call = GetWebFile.decode(request)
            val location = call.location as? InputWebFileGeoPointLocation ?: return@on FakeAnswer.Error(400, "LOCATION_INVALID")
            mapCalls += server.dcId to location
            if (location.accessHash != GEO_HASH) return@on FakeAnswer.Error(400, "LOCATION_INVALID")
            val start = minOf(call.offset, MAP_PICTURE.size)
            val bytes = MAP_PICTURE.copyOfRange(start, minOf(start + call.limit, MAP_PICTURE.size))
            FakeAnswer.Result(WebFile(MAP_PICTURE.size, "image/png", FilePng, 0, bytes))
        }
    }

    fun accept(call: SendMedia): FakeAnswer? {
        val media = when (val input = call.media) {
            is InputMediaPoll -> MessageMediaPoll(pollOf(input), results(input))
            is InputMediaGeoPoint -> MessageMediaGeo(geoOf(input.geoPoint as InputGeoPoint))
            is InputMediaVenue -> MessageMediaVenue(geoOf(input.geoPoint as InputGeoPoint), input.title, input.address, input.provider, input.venueId, input.venueType)
            is InputMediaGeoLive -> MessageMediaGeoLive(geo = geoOf(input.geoPoint as InputGeoPoint), heading = input.heading, period = input.period ?: 0, proximityNotificationRadius = input.proximityNotificationRadius)
            is InputMediaContact -> if (call.peer == InputPeerSelf) MessageMediaContact(input.phoneNumber, input.firstName, input.lastName, input.vcard, 0) else return null
            else -> return null
        }
        sent += call
        val id = fake.addSaved { id -> Message(out = true, id = id, peerId = PeerUser(fake.selfId), date = NOW, message = "", media = media) }
        val own = Message(out = true, id = id, fromId = PeerUser(fake.selfId), peerId = fake.peerOf(call.peer), date = fake.sentDate(), message = "", media = media)
        return FakeAnswer.Result(fake.sentUpdates(listOf(own to call.randomId)))
    }

    fun edit(call: EditMessage): FakeAnswer? {
        val input = call.media as? InputMediaGeoLive ?: return null
        val old = fake.savedMessage(call.id) ?: return FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
        val live = old.media as? MessageMediaGeoLive ?: return FakeAnswer.Error(400, "MEDIA_INVALID")
        liveEdits += call
        val media = if (input.stopped) {
            MessageMediaGeo(geoOf(input.geoPoint as InputGeoPoint))
        } else {
            MessageMediaGeoLive(
                geo = geoOf(input.geoPoint as InputGeoPoint),
                heading = input.heading,
                period = live.period,
                proximityNotificationRadius = input.proximityNotificationRadius ?: live.proximityNotificationRadius,
            )
        }
        fake.replaceSaved(Message(out = true, id = old.id, peerId = old.peerId, date = old.date, message = "", editDate = NOW, media = media))
        return FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
    }

    fun addPlace(latitude: Double, longitude: Double, period: Int? = null, heading: Int? = null): Int = fake.addSaved { id ->
        val geo = GeoPoint(longitude, latitude, GEO_HASH)
        Message(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, message = "", media = if (period == null) MessageMediaGeo(geo) else MessageMediaGeoLive(geo = geo, heading = heading, period = period))
    }

    fun addContact(first: String, last: String, phone: String, userId: Long = 0): Int = fake.addSaved { id ->
        Message(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, message = "", media = MessageMediaContact(phone, first, last, "", userId))
    }

    fun addProximity(distance: Int): Int = fake.addSavedService { id ->
        MessageService(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, action = MessageActionGeoProximityReached(PeerUser(fake.alice.id), PeerUser(fake.selfId), distance))
    }

    val recentCalls: MutableList<GetRecentLocations> = CopyOnWriteArrayList()

    val aliceLive: MutableList<Message> = CopyOnWriteArrayList()

    fun addAliceLive(latitude: Double, longitude: Double): Int {
        val id = ALICE_LIVE_ID + aliceLive.size
        aliceLive += Message(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.alice.id), date = NOW, message = "", media = MessageMediaGeoLive(geo = GeoPoint(longitude, latitude, GEO_HASH), period = Int.MAX_VALUE))
        return id
    }

    fun recent(call: GetRecentLocations): FakeAnswer {
        recentCalls += call
        val live = when {
            call.peer == InputPeerSelf -> fake.savedMessages().filter { it.media is MessageMediaGeoLive }.reversed()
            (call.peer as? InputPeerUser)?.userId == fake.alice.id -> aliceLive.toList()
            else -> emptyList()
        }
        return FakeAnswer.Result(Messages(live, emptyList(), emptyList(), listOf(fake.alice)))
    }

    suspend fun pushMove(id: Int, latitude: Double, longitude: Double, heading: Int? = null) {
        val old = fake.savedMessage(id) ?: error("no message $id")
        val live = old.media as MessageMediaGeoLive
        val moved = Message(id = id, fromId = old.fromId, peerId = old.peerId, date = old.date, message = "", editDate = NOW + 60, media = MessageMediaGeoLive(geo = GeoPoint(longitude, latitude, GEO_HASH), heading = heading, period = live.period))
        fake.replaceSaved(moved)
        fake.cluster.updates.push(Updates(listOf(UpdateEditMessage(moved, fake.cluster.updates.pts, 0)), listOf(fake.alice), emptyList(), NOW, 0))
    }

    private fun geoOf(point: InputGeoPoint) = GeoPoint(point.long, point.lat, GEO_HASH)

    private fun pollOf(input: InputMediaPoll): Poll = Poll(
        id = POLL_ID, publicVoters = input.poll.publicVoters, multipleChoice = input.poll.multipleChoice, quiz = input.poll.quiz,
        question = input.poll.question,
        answers = input.poll.answers.mapIndexed { index, answer -> PollAnswer((answer as InputPollAnswer).text, "$index".toByteArray()) },
        hash = POLL_ID,
    )

    private fun results(input: InputMediaPoll) = PollResults(
        results = input.poll.answers.indices.map { PollAnswerVoters(option = "$it".toByteArray(), voters = 0, recentVoters = emptyList()) },
        totalVoters = 0,
    )

    companion object {
        private const val POLL_ID = 8_000L
        private const val ALICE_LIVE_ID = 9_000

        const val GEO_HASH = 0x6E0_5EEDL

        val MAP_PICTURE: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(200 * 1024) { (it % 251).toByte() }
    }
}
