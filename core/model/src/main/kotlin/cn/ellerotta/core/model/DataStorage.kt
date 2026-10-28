package cn.ellerotta.core.model

enum class NetworkType { Mobile, Wifi, Roaming }

enum class AutoDownloadKind { Photos, Videos, Files, Voice, Gifs }

val MediaKind.autoDownloadKind: AutoDownloadKind?
    get() = when (this) {
        MediaKind.Photo -> AutoDownloadKind.Photos
        MediaKind.Video -> AutoDownloadKind.Videos
        MediaKind.Voice, MediaKind.Round -> AutoDownloadKind.Voice
        MediaKind.Gif -> AutoDownloadKind.Gifs
        MediaKind.File -> AutoDownloadKind.Files
        else -> null
    }

data class AutoDownloadRule(val kinds: Set<AutoDownloadKind> = emptySet(), val maxBytes: Long = AutoDownloadSettings.SIZES.first())

data class AutoDownloadSettings(
    val mobile: AutoDownloadRule = AutoDownloadRule(setOf(AutoDownloadKind.Voice, AutoDownloadKind.Gifs), 10 * MB),
    val wifi: AutoDownloadRule = AutoDownloadRule(setOf(AutoDownloadKind.Photos, AutoDownloadKind.Videos, AutoDownloadKind.Voice, AutoDownloadKind.Gifs), 10 * MB),
    val roaming: AutoDownloadRule = AutoDownloadRule(emptySet(), 1 * MB),
) {
    fun rule(network: NetworkType): AutoDownloadRule = when (network) {
        NetworkType.Mobile -> mobile
        NetworkType.Wifi -> wifi
        NetworkType.Roaming -> roaming
    }

    fun with(network: NetworkType, rule: AutoDownloadRule): AutoDownloadSettings = when (network) {
        NetworkType.Mobile -> copy(mobile = rule)
        NetworkType.Wifi -> copy(wifi = rule)
        NetworkType.Roaming -> copy(roaming = rule)
    }

    fun allows(media: MediaItem, network: NetworkType?): Boolean {
        val kind = media.kind?.autoDownloadKind ?: return false
        val rule = rule(network ?: return false)
        if (kind !in rule.kinds) return false
        return kind == AutoDownloadKind.Photos || kind == AutoDownloadKind.Voice || (media.size ?: Long.MAX_VALUE) <= rule.maxBytes
    }

    fun encode(): String = NetworkType.entries.joinToString(";") { network ->
        rule(network).let { rule -> rule.kinds.joinToString(",") { it.name } + "|" + rule.maxBytes }
    }

    companion object {
        const val MB = 1024L * 1024

        val SIZES = listOf(1 * MB, 5 * MB, 10 * MB, 50 * MB, 100 * MB, 500 * MB, 2048 * MB)

        fun decode(text: String?): AutoDownloadSettings {
            val parts = text?.split(";")?.takeIf { it.size == NetworkType.entries.size } ?: return AutoDownloadSettings()
            val defaults = AutoDownloadSettings()
            return NetworkType.entries.zip(parts).fold(defaults) { settings, (network, part) ->
                val kinds = part.substringBefore("|").split(",").filter { it.isNotEmpty() }.map { name -> AutoDownloadKind.entries.firstOrNull { it.name == name } ?: return defaults }.toSet()
                val max = part.substringAfter("|", "").toLongOrNull()?.takeIf { it > 0 } ?: return defaults
                settings.with(network, AutoDownloadRule(kinds, max))
            }
        }
    }
}

enum class StorageCategory {
    Media,

    Streams,

    Music,

    Stickers,

    Avatars,
}

data class StorageUsage(val bytes: Map<StorageCategory, Long> = emptyMap(), val free: Long? = null) {
    val total: Long get() = bytes.values.sum()
}

data class DataStorageState(
    val autoDownload: AutoDownloadSettings = AutoDownloadSettings(),
    val usage: StorageUsage? = null,
    val clearing: Set<StorageCategory> = emptySet(),
)
