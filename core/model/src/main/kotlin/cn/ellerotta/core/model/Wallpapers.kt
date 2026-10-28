package cn.ellerotta.core.model

enum class WallpaperKind {
    Image,

    Pattern,

    Fill,

    Plain,
}

data class Wallpaper(
    val key: String,
    val kind: WallpaperKind,
    val colors: List<Int> = emptyList(),
    val rotation: Int = 0,
    val intensity: Int = DEFAULT_PATTERN_INTENSITY,
    val blur: Boolean = false,
    val motion: Boolean = false,
    val dark: Boolean = false,
    val isDefault: Boolean = false,
    val slug: String = "",
)

const val DEFAULT_PATTERN_INTENSITY = 50

const val NO_WALLPAPER = "none"

val PLAIN_WALLPAPER = Wallpaper(NO_WALLPAPER, WallpaperKind.Plain)

data class ChatWallpaper(val wallpaper: Wallpaper, val file: String? = null)

data class WallpaperEdit(val wallpaper: Wallpaper, val file: String? = null)

data class WallpaperAction(val wallpaper: Wallpaper, val same: Boolean = false, val forBoth: Boolean = false)

val WALLPAPER_SWATCHES = listOf(
    0xDBDDBB, 0x6BA587, 0xD5D88D, 0x88B884, 0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5,
    0x7FA381, 0xFFF5C5, 0x4F9DE0, 0x2B2B2B,
)

data class WallpaperChoices(
    val wallpapers: List<Wallpaper> = emptyList(),
    val previews: Map<String, String> = emptyMap(),
    val chosen: String? = null,
)
