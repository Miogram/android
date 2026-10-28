package cn.ellerotta.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object PollLimits {
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 10
    const val QUESTION = 255
    const val OPTION = 100
    const val EXPLANATION = 200
}

data class PollOptionDraft(val id: Int, val text: String = "")

data class PollDraft(
    val kind: PeerKind = PeerKind.Group,
    val question: String = "",
    val options: List<PollOptionDraft> = listOf(PollOptionDraft(0), PollOptionDraft(1)),
    val anonymous: Boolean = true,
    val multiple: Boolean = false,
    val quiz: Boolean = false,
    val correct: Int? = null,
    val explanation: String = "",
    val nextId: Int = 2,
) {
    val canChooseAnonymity: Boolean get() = kind == PeerKind.Group

    val publicVoters: Boolean get() = canChooseAnonymity && !anonymous

    val canAddOption: Boolean get() = options.size < PollLimits.MAX_OPTIONS

    val canRemoveOption: Boolean get() = options.size > PollLimits.MIN_OPTIONS

    fun withQuestion(text: String) = copy(question = text.take(PollLimits.QUESTION))

    fun withOption(id: Int, text: String) = copy(options = options.map { if (it.id == id) it.copy(text = text.take(PollLimits.OPTION)) else it })

    fun addOption(): PollDraft = if (!canAddOption) this else copy(options = options + PollOptionDraft(nextId), nextId = nextId + 1)

    fun removeOption(id: Int): PollDraft {
        if (!canRemoveOption) return this
        return copy(options = options.filterNot { it.id == id }, correct = correct.takeIf { it != id })
    }

    fun move(id: Int, to: Int): PollDraft {
        val from = options.indexOfFirst { it.id == id }
        if (from < 0 || to !in options.indices || from == to) return this
        return copy(options = options.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun withAnonymous(on: Boolean) = copy(anonymous = on)

    fun withMultiple(on: Boolean) = if (on) copy(multiple = true, quiz = false, correct = null) else copy(multiple = false)

    fun withQuiz(on: Boolean) = if (on) copy(quiz = true, multiple = false) else copy(quiz = false, correct = null, explanation = "")

    fun withCorrect(id: Int) = copy(correct = id)

    fun withExplanation(text: String) = copy(explanation = text.take(PollLimits.EXPLANATION))

    val filled: List<PollOptionDraft> get() = options.filter { it.text.isNotBlank() }

    val canSend: Boolean
        get() = question.isNotBlank() && filled.size >= PollLimits.MIN_OPTIONS &&
            (!quiz || (correct != null && filled.any { it.id == correct }))

    val correctIndex: Int? get() = if (quiz) filled.indexOfFirst { it.id == correct }.takeIf { it >= 0 } else null

    val started: Boolean get() = question.isNotBlank() || options.any { it.text.isNotBlank() } || explanation.isNotBlank()
}

data class GeoPlace(
    val latitude: Double,
    val longitude: Double,
    val title: String? = null,
    val address: String? = null,
    val live: LiveInfo? = null,
    val venue: Venue? = null,
    val accessHash: Long? = null,
    val accuracy: Int? = null,
) {
    val coordinates: String get() = String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude)

    val geoUri: String
        get() {
            val point = String.format(java.util.Locale.ROOT, "%.6f,%.6f", latitude, longitude)
            val label = title?.takeIf { it.isNotBlank() }
            return if (label == null) "geo:$point?q=$point" else "geo:$point?q=$point(${java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")})"
        }

    val directionsUri: String
        get() = String.format(java.util.Locale.ROOT, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", latitude, longitude)

    val webUri: String
        get() = String.format(java.util.Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitude, longitude)

    val shareText: String get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, address?.takeIf { it.isNotBlank() }, webUri).joinToString("\n")

    fun distanceTo(other: GeoPlace): Double = distanceMeters(latitude, longitude, other.latitude, other.longitude)
}

data class Venue(val provider: String, val id: String, val type: String = "") {
    companion object {
        const val GOOGLE = "gplaces"
    }
}

data class LiveInfo(
    val period: Int = 0,
    val heading: Int? = null,
    val radius: Int? = null,
    val expires: Int? = null,
    val stopped: Boolean = false,
    val updated: Int? = null,
    val senderKey: String? = null,
    val senderName: String? = null,
    val senderAvatar: String? = null,
) {
    fun isOver(now: Int): Boolean = stopped || (expires != null && expires <= now)
}

data class LiveSharer(val messageId: Int, val self: Boolean, val place: GeoPlace) {
    val live: LiveInfo get() = place.live ?: LiveInfo()
    val key: String get() = live.senderKey ?: "m$messageId"
    val name: String get() = live.senderName.orEmpty()
}

fun liveSharers(messages: List<MessageItem>, now: Int): List<LiveSharer> =
    currentSharers(messages.mapNotNull { message -> message.place?.takeIf { it.live != null }?.let { LiveSharer(message.id, message.out, it) } }, now)

fun currentSharers(all: Collection<LiveSharer>, now: Int): List<LiveSharer> =
    all.filter { !it.live.isOver(now) }
        .groupBy { it.key }.values.map { same -> same.maxBy { it.messageId } }
        .sortedWith(compareByDescending<LiveSharer> { it.self }.thenByDescending { it.live.updated ?: 0 })

object LivePeriods {
    val offered = listOf(15 * 60, 60 * 60, 8 * 60 * 60, 0)

    const val FOREVER = 0x7FFFFFFF

    fun periodOf(seconds: Int): Int = if (seconds <= 0) FOREVER else seconds
}

object ProximityRadii {
    const val MIN = 50
    const val MAX = 100_000

    const val DEFAULT = 500

    val metric: List<Int> = listOf(50, 100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 900) +
        listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0).map { (it * 1000).roundToInt() }

    val imperial: List<Int> = listOf(200, 300, 400, 500, 600, 700, 800, 900).map { DistanceAmount.feet(it.toDouble()).roundToInt() } +
        listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0).map { DistanceAmount.miles(it).roundToInt() }

    fun steps(units: DistanceUnits): List<Int> = if (units == DistanceUnits.Imperial) imperial else metric

    fun nearest(steps: List<Int>, metres: Int): Int = steps.indices.minBy { abs(steps[it] - metres) }
}

data class ProximityAlert(val from: String, val to: String, val distance: Int)

data class ContactCard(
    val firstName: String,
    val lastName: String = "",
    val phone: String,
    val userId: Long = 0,
    val vcard: String = "",
) {
    val name: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    val canSend: Boolean get() = phone.any { it.isDigit() } && firstName.isNotBlank()
}

fun splitName(display: String): Pair<String, String> {
    val name = display.trim().replace(Regex("\\s+"), " ")
    val space = name.lastIndexOf(' ')
    return if (space < 0) name to "" else name.substring(0, space) to name.substring(space + 1)
}

fun ContactItem.card(): ContactCard? {
    val number = phone?.takeIf { it.any(Char::isDigit) } ?: return null
    val (first, last) = splitName(name)
    return ContactCard(first, last, if (number.startsWith("+")) number else "+$number", userId)
}

private const val EARTH_RADIUS = 6371000.0

fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    fun rad(deg: Double) = deg * PI / 180
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS * asin(sqrt(a))
}
