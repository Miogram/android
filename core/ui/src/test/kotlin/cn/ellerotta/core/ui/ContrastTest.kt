package cn.ellerotta.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.ui.components.chat.GIFT_CHIP_ALPHA
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM
import cn.ellerotta.core.ui.components.chat.PILL_ALPHA
import cn.ellerotta.core.ui.components.chat.REPLY_TINT
import cn.ellerotta.core.ui.components.chat.RANK_TINT
import cn.ellerotta.core.ui.components.chat.bubbleColors
import cn.ellerotta.core.ui.components.chat.senderColor
import cn.ellerotta.core.ui.components.chat.styleFill
import cn.ellerotta.core.ui.components.common.avatarPalettes
import cn.ellerotta.core.ui.components.common.closeFriendsGreens
import cn.ellerotta.core.ui.components.common.savedMessagesPalette
import cn.ellerotta.core.ui.components.profile.SessionBlue
import cn.ellerotta.core.ui.components.profile.SessionGreen
import cn.ellerotta.core.ui.components.profile.SessionGrey
import cn.ellerotta.core.ui.components.profile.SessionOrange
import cn.ellerotta.core.ui.components.profile.legibleTone
import cn.ellerotta.core.ui.components.profile.musicCardSecondary
import cn.ellerotta.core.ui.components.stories.StoryBand
import cn.ellerotta.core.ui.components.stories.StoryHeartFill
import cn.ellerotta.core.ui.components.stories.StoryTrack
import cn.ellerotta.core.ui.theme.GRAPHIC_CONTRAST
import cn.ellerotta.core.ui.theme.Lch
import cn.ellerotta.core.ui.theme.TEXT_CONTRAST
import cn.ellerotta.core.ui.theme.artworkScheme
import cn.ellerotta.core.ui.theme.artworkSeed
import cn.ellerotta.core.ui.theme.contrastRatio
import cn.ellerotta.core.ui.theme.fadedOver
import cn.ellerotta.core.ui.theme.legibleOn
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContrastTest {
    @Test
    fun theFullPlayersColoursFromAnyCoverKeepEveryPair() {
        val seeds = (0 until 360 step 15).flatMap { hue -> listOf(0.0, 12.0, 40.0, 90.0, 140.0).map { Lch(50.0, it, hue.toDouble()) } } +
            listOf(Lch(2.0, 0.0, 0.0), Lch(98.0, 0.0, 0.0), Lch(30.0, 60.0, 280.0), Lch(90.0, 80.0, 100.0))
        for (seed in seeds) for (dark in listOf(false, true)) {
            val s = artworkScheme(seed, dark)
            val text = listOf(
                s.onSurface to s.surface, s.onSurfaceVariant to s.surface,
                s.onSurface to s.surfaceContainerLow, s.onSurfaceVariant to s.surfaceContainerLow,
                s.onPrimary to s.primary, s.onSecondaryContainer to s.secondaryContainer,
                s.onTertiaryContainer to s.tertiaryContainer, s.onSurface to s.surfaceContainer,
                s.onSurfaceVariant to s.surfaceContainerHighest, s.onSurface to s.surfaceContainerHighest,
            )
            text.forEach { (fg, bg) ->
                assertTrue(contrastRatio(fg, bg) >= TEXT_CONTRAST, "$seed dark=$dark: ${contrastRatio(fg, bg)}")
            }
            listOf(s.primary to s.surface, s.primary to s.surfaceContainerLow).forEach { (fg, bg) ->
                assertTrue(contrastRatio(fg, bg) >= GRAPHIC_CONTRAST, "$seed dark=$dark graphic: ${contrastRatio(fg, bg)}")
            }
        }
    }

    @Test
    fun aCoversSeedIsItsMainHueAndAGreyCoverStaysGrey() {
        val orange = 0xFFE8742A.toInt()
        val blue = 0xFF2A5BE8.toInt()
        val pixels = IntArray(48 * 48) { if (it % 48 < 36) orange else blue }
        val seed = assertNotNull(artworkSeed(pixels))
        val orangeHue = assertNotNull(artworkSeed(IntArray(16) { orange })).h
        assertTrue(abs(seed.h - orangeHue) < 15, "seed ${seed.h}, orange $orangeHue")
        assertTrue(seed.c > 40)
        val grey = IntArray(48 * 48) { if (it % 100 == 0) blue else 0xFF777777.toInt() }
        assertEquals(0.0, assertNotNull(artworkSeed(grey)).c)
        assertNull(artworkSeed(IntArray(4)))
        val scheme = artworkScheme(seed, dark = false)
        assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= TEXT_CONTRAST)
    }

    @Test
    fun ratioFollowsWcag() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.01f)
        assertEquals(4.54f, contrastRatio(Color(0xFF767676), Color.White), 0.01f)
        assertEquals(contrastRatio(Color.White, Color(0xFF0062D1)), contrastRatio(Color(0xFF0062D1), Color.White))
    }

    @Test
    fun bubblesKeepAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            for (out in listOf(true, false)) {
                val bubble = bubbleColors(scheme, out)
                val on = bubble.container
                val label = "$name ${if (out) "outgoing" else "incoming"}"
                text(label, "content", bubble.content, on)
                text(label, "accent", bubble.accent, on)
                text(label, "secondary", bubble.secondary.compositeOver(on), on)
                text(label, "on accent", bubble.onAccent, bubble.accent)
                text(label, "reaction", bubble.content, bubble.accent.copy(alpha = 0.12f).compositeOver(on))
                text(label, "sender tag", bubble.content, bubble.accent.copy(alpha = RANK_TINT).compositeOver(on))
                graphic(label, "track", bubble.track.compositeOver(on), on)
                graphic(label, "success", bubble.success, on)
                graphic(label, "danger", bubble.danger, on)
                if (!out) (0L until 7L).forEach { id -> text(label, "sender $id", senderColor(id, on), on) }
                val quoteColors = if (out) listOf(bubble.accent) else (0L until 7L).map { senderColor(it, on) }
                quoteColors.forEachIndexed { index, quote ->
                    val tint = quote.copy(alpha = REPLY_TINT).compositeOver(on)
                    text(label, "reply quote $index title", quote, tint)
                    text(label, "reply quote $index text", bubble.content, tint)
                    graphic(label, "reply quote $index bar", quote, tint)
                }
            }
            text(name, "header status", scheme.onSurfaceVariant, scheme.surface)
            for (wallpaper in listOf(Color.White, Color.Black)) {
                text(name, "gift card", scheme.onSecondaryContainer, scheme.secondaryContainer.copy(alpha = GIFT_CHIP_ALPHA).compositeOver(wallpaper))
            }
        }
    }

    @Test
    fun chatsListKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "unread badge", scheme.onPrimary, scheme.primary)
            text(name, "muted unread badge", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "folder tab", scheme.onSurfaceVariant, scheme.surface)
            text(name, "selected folder tab on its pill", scheme.onSecondaryContainer, scheme.secondaryContainer)
            graphic(name, "online dot", legibleOn(scheme.surface, closeFriendsGreens), scheme.surface)
            text(name, "muted folder count on the pill", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "unread folder count on the pill", scheme.onPrimary, scheme.primary)
            text(name, "chat name", scheme.onSurface, scheme.surface)
            text(name, "chat preview", scheme.onSurfaceVariant, scheme.surface)
            text(name, "chat time and mention mark", scheme.primary, scheme.surface)
            graphic(name, "pin, checks and badges", scheme.primary, scheme.surface)
            graphic(name, "mute mark", scheme.onSurfaceVariant, scheme.surface)
            text(name, "pressed row's name", scheme.onSurface, scheme.surfaceContainerHighest)
            text(name, "pressed row's preview", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "pressed row's time", scheme.primary, scheme.surfaceContainerHighest)
            text(name, "selected row's name", scheme.onSurface, scheme.secondaryContainer)
            text(name, "selected row's preview", scheme.onSurfaceVariant, scheme.secondaryContainer)
            text(name, "selected row's time", scheme.primary, scheme.secondaryContainer)
            text(name, "app bar title", scheme.onSurface, scheme.surface)
            text(name, "search placeholder", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "search text", scheme.onSurface, scheme.surfaceContainerHighest)
            graphic(name, "toolbar's chosen item", scheme.onPrimaryContainer, scheme.primaryContainer)
            graphic(name, "toolbar's other items", scheme.onSurfaceVariant, scheme.surface)
        }
    }

    @Test
    fun groupCreationKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "new group row's label", scheme.onSurface, scheme.surface)
            graphic(name, "new group row's icon", scheme.onPrimaryContainer, scheme.primaryContainer)
            graphic(name, "photo button's camera", scheme.onPrimaryContainer, scheme.primaryContainer)
            text(name, "username answer", scheme.onSurfaceVariant, scheme.surface)
            text(name, "username problem", scheme.error, scheme.surface)
            text(name, "picker's button", scheme.onPrimaryContainer, scheme.primaryContainer)
            text(name, "people count", scheme.onSurfaceVariant, scheme.surface)
            text(name, "picked chip", scheme.onSecondaryContainer, scheme.secondaryContainer)
        }
    }

    @Test
    fun chatHeaderKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "header title", scheme.onSurface, scheme.surfaceContainerLow)
            text(name, "header status", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            text(name, "header online and activity", scheme.primary, scheme.surfaceContainerLow)
            text(name, "header scam and fake tag", scheme.error, scheme.surfaceContainerLow)
            graphic(name, "header verified, premium and the activity's dots", scheme.primary, scheme.surfaceContainerLow)
            graphic(name, "header mute mark", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            text(name, "back badge count", scheme.onPrimary, scheme.primary)
            graphic(name, "back badge on its pill", scheme.primary, scheme.surfaceContainerLow)
            graphic(name, "back badge over the scrim", scheme.primary, scheme.surface)
        }
    }

    @Test
    fun contactsPageKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "contact name", scheme.onSurface, scheme.surface)
            text(name, "last seen and the empty list", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "mutual mark", scheme.primary, scheme.surface)
            text(name, "global search heading", scheme.primary, scheme.surface)
            text(name, "pressed contact's name", scheme.onSurface, scheme.surfaceContainerHighest)
            text(name, "pressed contact's last seen", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            graphic(name, "pressed contact's mutual mark", scheme.primary, scheme.surfaceContainerHighest)
            text(name, "contact to delete, name", scheme.onSurface, scheme.secondaryContainer)
            text(name, "contact to delete, last seen", scheme.onSurfaceVariant, scheme.secondaryContainer)
            graphic(name, "contact to delete, mutual mark", scheme.primary, scheme.secondaryContainer)
            graphic(name, "add button", scheme.onSurface, scheme.surface)
            text(name, "number without an account", scheme.error, scheme.surfaceContainerHigh)
        }
    }

    @Test
    fun settingsPageKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "bar title, own name and row titles", scheme.onSurface, scheme.surface)
            text(name, "section labels, number and row subtitles", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "row icon", scheme.primary, scheme.surface)
            graphic(name, "chevron", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "switch on, thumb", scheme.onPrimary, scheme.primary)
            graphic(name, "switch off, thumb", scheme.outline, scheme.surfaceContainerHighest)
            text(name, "chosen theme", scheme.onSecondaryContainer, scheme.secondaryContainer)
            text(name, "other themes", scheme.onSurface, scheme.surface)
            text(name, "log out", scheme.onErrorContainer, scheme.errorContainer)
            text(name, "log out confirmation", scheme.error, scheme.surfaceContainerHigh)
        }
    }

    @Test
    fun foldersKeepAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "folder names and switch labels", scheme.onSurface, scheme.surfaceContainerLow)
            text(name, "folder names while dragged", scheme.onSurface, scheme.surfaceContainerHigh)
            text(name, "folder summaries and descriptions", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            text(name, "hint under the title, counts", scheme.onSurfaceVariant, scheme.surface)
            text(name, "section labels", scheme.primary, scheme.surface)
            graphic(name, "reorder handle", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            graphic(name, "folder tile's icon", scheme.onSecondaryContainer, scheme.secondaryContainer)
            text(name, "add button, shared note", scheme.onSecondaryContainer, scheme.secondaryContainer)
            text(name, "limit message", scheme.onTertiaryContainer, scheme.tertiaryContainer)
            text(name, "type toggle on", scheme.onPrimary, scheme.primary)
            graphic(name, "chosen icon's ring", scheme.primary, scheme.surface)
            graphic(name, "icon tiles", scheme.onSurface, scheme.surfaceContainerHigh)
            text(name, "delete folder", scheme.error, scheme.surface)
            text(name, "delete in the tab's menu", scheme.error, scheme.surfaceContainer)
            text(name, "menu items", scheme.onSurface, scheme.surfaceContainer)
            text(name, "chat kind in the picker", scheme.onSurfaceVariant, scheme.surface)
            text(name, "delete confirmation", scheme.error, scheme.surfaceContainerHigh)
        }
    }

    @Test
    fun storyRingsKeepAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            for ((where, background) in listOf("list and profile" to scheme.surface, "chat header" to scheme.surfaceContainerHighest)) {
                graphic(name, "unread ring, $where", scheme.primary, background)
                graphic(name, "close friends ring, $where", legibleOn(background, closeFriendsGreens), background)
                graphic(name, "read ring, $where", scheme.outline, background)
            }
        }
    }

    @Test
    fun storyBarKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "unread card name", scheme.onSurface, scheme.surface)
            text(name, "read card name", scheme.onSurfaceVariant, scheme.surface)
            text(name, "my story", scheme.primary, scheme.surface)
            text(name, "more avatars count", scheme.onSurface, scheme.surfaceContainerHighest)
            graphic(name, "unread card border", scheme.primary, scheme.surface)
            graphic(name, "my story card border", scheme.outline, scheme.surface)
            graphic(name, "add plus", scheme.onPrimary, scheme.primary)
            graphic(name, "add circle on the card", scheme.primary, scheme.surfaceContainerHigh)
        }
    }

    @Test
    fun loginStepsKeepAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "inline error", scheme.onErrorContainer, scheme.errorContainer)
            text(name, "step headline and typed text", scheme.onSurface, scheme.surface)
            text(name, "step text, the number's pattern and qr steps", scheme.onSurfaceVariant, scheme.surface)
            text(name, "country name", scheme.primary, scheme.surface)
            text(name, "digit box", scheme.onSurface, scheme.surfaceContainerHigh)
            text(name, "digit box at the cursor", scheme.onPrimaryContainer, scheme.primaryContainer)
            text(name, "digit box of a wrong code", scheme.onErrorContainer, scheme.errorContainer)
            text(name, "qr step number", scheme.onPrimaryContainer, scheme.primaryContainer)
            graphic(name, "phone field border", scheme.outline, scheme.surface)
            graphic(name, "continue arrow", scheme.onPrimary, scheme.primary)
        }
    }

    @Test
    fun pillsOverAnyWallpaperOrPhoto() {
        for (behind in listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF808080), Color.Black)) {
            text("$behind", "glass pill", Color.White, Color.Black.copy(alpha = PILL_ALPHA).compositeOver(behind))
            graphic("$behind", "media scrim", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(behind))
        }
    }

    @Test
    fun pillsAndTilesOverNoWallpaper() {
        for ((name, scheme) in schemes()) {
            text(name, "glass pill on the plain background", Color.White, Color.Black.copy(alpha = PILL_ALPHA).compositeOver(scheme.surface))
            graphic(name, "media scrim on the plain background", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(scheme.surface))
            text(name, "no wallpaper tile's label", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "no wallpaper tile's outline", scheme.outline, scheme.surface)
        }
    }

    @Test
    fun storyViewerOverAnyPhoto() {
        for (behind in listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF808080), Color.Black)) {
            val band = StoryBand.compositeOver(behind)
            text("$behind", "story band", Color.White, band)
            graphic("$behind", "filled progress bar on the band", Color.White, band)
            graphic("$behind", "filled progress bar against its track", Color.White, StoryTrack.compositeOver(band))
            graphic("$behind", "loading spinner", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(behind))
        }
    }

    @Test
    fun storyReplyBarOverAnyPhoto() {
        for (behind in listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF808080), Color.Black)) {
            text("$behind", "reply field", Color.White, Color.Black.copy(alpha = PILL_ALPHA).compositeOver(behind))
            graphic("$behind", "heart on the scrim", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(behind))
        }
        graphic("liked", "heart on its red", Color.White, StoryHeartFill)
    }

    @Test
    fun ownStoriesKeepAa() {
        for (behind in listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF808080), Color.Black)) {
            text("$behind", "views and reactions counts", Color.White, Color.Black.copy(alpha = PILL_ALPHA).compositeOver(behind))
            graphic("$behind", "composer's pick again and play marks", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(behind))
        }
        for ((name, scheme) in schemes()) {
            text(name, "viewers sheet title and names", scheme.onSurface, scheme.surfaceContainerLow)
            text(name, "viewers sheet reactions, times and empty text", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            graphic(name, "viewers sheet spinner", scheme.primary, scheme.surfaceContainerLow)
        }
        text("composer", "title and labels", Color.White, Color.Black)
        text("composer", "white buttons", Color.Black, Color.White)
        val field = Color.White.copy(alpha = 0.12f).compositeOver(Color.Black)
        text("composer", "caption", Color.White, field)
        text("composer", "caption hint", Color.White.fadedOver(field, 0.7f, TEXT_CONTRAST).compositeOver(field), field)
    }

    @Test
    fun botButtonFillsCarryWhiteLabels() {
        for (dark in listOf(false, true)) {
            for (style in listOf(BotButtonStyle.Primary, BotButtonStyle.Danger, BotButtonStyle.Success)) {
                text("dark $dark", "$style", Color.White, styleFill(style, dark).compositeOver(Color.White))
            }
        }
    }

    @Test
    fun placeholderAvatarsCarryTheirInitials() {
        for ((index, palette) in avatarPalettes.withIndex()) {
            text("gradient $index", "initials on top", palette.content, palette.top)
            text("gradient $index", "initials on bottom", palette.content, palette.bottom)
        }
        graphic("saved messages", "star on top", savedMessagesPalette.content, savedMessagesPalette.top)
        graphic("saved messages", "star on bottom", savedMessagesPalette.content, savedMessagesPalette.bottom)
        for (rgb in ForumTopic.COLORS) {
            val fill = Color(0xFF000000.toInt() or rgb)
            text(Integer.toHexString(rgb), "topic letter and swatch check", legibleOn(fill, listOf(Color.White, Color.Black)), fill)
        }
        for ((name, scheme) in schemes()) {
            text(name, "general topic's #", legibleOn(scheme.outline, listOf(Color.White, Color.Black)), scheme.outline)
        }
    }

    @Test
    fun profileTilesAndCardsKeepAa() {
        for ((what, tile) in listOf("green" to SessionGreen, "orange" to SessionOrange, "blue" to SessionBlue, "grey" to SessionGrey)) {
            graphic("session tile", "white icon on $what", Color.White, tile)
        }
        for (cover in listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF00FCFD), Color(0xFFFF516A), Color(0xFF808080), Color(0xFF1E88E5))) {
            val tone = legibleTone(cover)
            text("$cover", "music card title", Color.White, tone)
            text("$cover", "music card performer", musicCardSecondary(tone).compositeOver(tone), tone)
        }
    }

    @Test
    fun sharedMediaKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            text(name, "row title", scheme.onSurface, scheme.surfaceContainerLow)
            text(name, "row subtitle and links", scheme.onSurfaceVariant, scheme.surfaceContainerLow)
            text(name, "month header", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "file and link tile icon", scheme.onPrimaryContainer, scheme.primaryContainer)
            text(name, "tab label", scheme.onSurface, scheme.surfaceContainerHigh)
            text(name, "chosen tab label", scheme.onPrimary, scheme.primary)
            graphic(name, "play and pause button", scheme.onPrimary, scheme.primary)
            graphic(name, "waveform", scheme.onSurface, scheme.surfaceContainerLow)
            graphic(name, "waveform, unplayed", scheme.onSurface.fadedOver(scheme.surfaceContainerLow, 0.38f, GRAPHIC_CONTRAST), scheme.surfaceContainerLow)
            text(name, "menu's delete line", scheme.error, scheme.surfaceContainerLow)
        }
        graphic("any", "play mark", Color.White, Color.Black.copy(alpha = MEDIA_SCRIM).compositeOver(Color.White))
    }

    @Test
    fun musicPlayerKeepsAaInEveryScheme() {
        for ((name, scheme) in schemes()) {
            graphic(name, "shuffle and repeat off", scheme.onSecondaryContainer, scheme.secondaryContainer)
            graphic(name, "shuffle and repeat on", scheme.onSecondary, scheme.secondary)
        }
    }

    private fun text(scheme: String, what: String, color: Color, background: Color) = check(scheme, what, color, background, TEXT_CONTRAST)

    private fun graphic(scheme: String, what: String, color: Color, background: Color) = check(scheme, what, color, background, GRAPHIC_CONTRAST)

    private fun check(scheme: String, what: String, color: Color, background: Color, min: Float) {
        val ratio = contrastRatio(color, background)
        assertTrue(ratio >= min, "$scheme: $what $color on $background is $ratio, needs $min")
    }

    private fun schemes(): List<Pair<String, ColorScheme>> = buildList {
        add("expressive light" to expressiveLightColorScheme())
        add("light" to lightColorScheme())
        add("dark" to darkColorScheme())
        for ((seedName, seed) in seeds) {
            add("$seedName light" to dynamicScheme(seed, dark = false))
            add("$seedName dark" to dynamicScheme(seed, dark = true))
        }
        add("monochrome light" to monochrome(dark = false))
        add("monochrome dark" to monochrome(dark = true))
    }

    private val seeds = listOf(
        "yellow" to Color(0xFFFFEB3B),
        "cyan" to Color(0xFF00BCD4),
        "red" to Color(0xFFF44336),
        "green" to Color(0xFF4CAF50),
        "magenta" to Color(0xFFE91E63),
        "indigo" to Color(0xFF3F51B5),
    )

    private fun dynamicScheme(seed: Color, dark: Boolean): ColorScheme {
        val primary = Palette(seed, 48f)
        val secondary = Palette(seed, 16f)
        val tertiary = Palette(seed, 24f, turn = 60f)
        val neutral = Palette(seed, 8f)
        return if (!dark) {
            lightColorScheme(
                primary = primary(40f), onPrimary = primary(100f), primaryContainer = primary(90f), onPrimaryContainer = primary(10f),
                secondary = secondary(40f), onSecondary = secondary(100f), secondaryContainer = secondary(90f), onSecondaryContainer = secondary(10f),
                tertiary = tertiary(40f), onTertiary = tertiary(100f), tertiaryContainer = tertiary(90f), onTertiaryContainer = tertiary(10f),
                background = neutral(98f), onBackground = neutral(10f), surface = neutral(98f), onSurface = neutral(10f),
                surfaceVariant = neutral(90f), onSurfaceVariant = neutral(30f), outline = neutral(50f), outlineVariant = neutral(80f),
                surfaceContainer = neutral(94f), surfaceContainerHigh = neutral(92f), surfaceContainerHighest = neutral(90f),
                surfaceContainerLow = neutral(96f), surfaceContainerLowest = neutral(100f),
            )
        } else {
            darkColorScheme(
                primary = primary(80f), onPrimary = primary(20f), primaryContainer = primary(30f), onPrimaryContainer = primary(90f),
                secondary = secondary(80f), onSecondary = secondary(20f), secondaryContainer = secondary(30f), onSecondaryContainer = secondary(90f),
                tertiary = tertiary(80f), onTertiary = tertiary(20f), tertiaryContainer = tertiary(30f), onTertiaryContainer = tertiary(90f),
                background = neutral(6f), onBackground = neutral(90f), surface = neutral(6f), onSurface = neutral(90f),
                surfaceVariant = neutral(30f), onSurfaceVariant = neutral(80f), outline = neutral(60f), outlineVariant = neutral(30f),
                surfaceContainer = neutral(12f), surfaceContainerHigh = neutral(17f), surfaceContainerHighest = neutral(22f),
                surfaceContainerLow = neutral(10f), surfaceContainerLowest = neutral(4f),
            )
        }
    }

    private fun monochrome(dark: Boolean): ColorScheme {
        val grey = Palette(Color.Gray, 0f)
        return dynamicScheme(Color.Gray, dark).copy(
            primary = grey(if (dark) 100f else 0f),
            onPrimary = grey(if (dark) 10f else 90f),
            primaryContainer = grey(if (dark) 85f else 25f),
            onPrimaryContainer = grey(if (dark) 0f else 100f),
        )
    }

    private class Palette(seed: Color, private val chroma: Float, turn: Float = 0f) {
        private val hue: Double

        init {
            val (_, a, b) = lab(seed)
            hue = Math.atan2(b, a) + Math.toRadians(turn.toDouble())
        }

        operator fun invoke(tone: Float): Color {
            var c = chroma.toDouble()
            var rgb = srgb(tone.toDouble(), c * Math.cos(hue), c * Math.sin(hue))
            while (c > 0.0 && rgb.any { it !in -0.0001..1.0001 }) {
                c = (c - 0.5).coerceAtLeast(0.0)
                rgb = srgb(tone.toDouble(), c * Math.cos(hue), c * Math.sin(hue))
            }
            return Color(rgb[0].toFloat().coerceIn(0f, 1f), rgb[1].toFloat().coerceIn(0f, 1f), rgb[2].toFloat().coerceIn(0f, 1f))
        }
    }

    private companion object {
        fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

        fun encode(c: Double): Double = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

        fun lab(color: Color): Triple<Double, Double, Double> {
            val r = linear(color.red)
            val g = linear(color.green)
            val b = linear(color.blue)
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
            return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
        }

        fun srgb(l: Double, a: Double, b: Double): List<Double> {
            val fy = (l + 16) / 116
            val fx = fy + a / 500
            val fz = fy - b / 200
            fun inverse(t: Double) = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)
            val x = inverse(fx) * 0.95047
            val y = inverse(fy)
            val z = inverse(fz) * 1.08883
            return listOf(
                3.2406 * x - 1.5372 * y - 0.4986 * z,
                -0.9689 * x + 1.8758 * y + 0.0415 * z,
                0.0557 * x - 0.2040 * y + 1.0570 * z,
            ).map { if (it < 0) it else encode(it) }
        }
    }
}
