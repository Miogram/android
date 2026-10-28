package cn.ellerotta.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
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
import cn.ellerotta.core.ui.theme.brandScheme
import cn.ellerotta.core.ui.theme.contrastRatio
import cn.ellerotta.core.ui.theme.fadedOver
import cn.ellerotta.core.ui.theme.legibleOn
import cn.ellerotta.core.ui.theme.lchOf
import kotlin.math.abs
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
    fun theBrandSchemeIsANavyWithASlateSecondaryAndABlushTertiary() {
        for (dark in listOf(false, true)) {
            val brand = brandScheme(dark)
            val primary = lchOf(brand.primary.toArgb())
            val secondary = lchOf(brand.secondary.toArgb())
            val tertiary = lchOf(brand.tertiary.toArgb())
            assertTrue(primary.h in 280.0..300.0 && primary.c > 25, "primary $primary")
            assertTrue(secondary.h in 265.0..295.0 && secondary.c in 8.0..20.0, "secondary $secondary")
            assertTrue((tertiary.h + 20) % 360 in 5.0..40.0 && tertiary.c > 20, "tertiary $tertiary")
            for (surface in listOf(brand.surface, brand.surfaceContainer, brand.surfaceContainerHighest)) {
                val lch = lchOf(surface.toArgb())
                assertTrue(lch.c < 6 && lch.h in 250.0..320.0, "surface $lch")
            }
        }
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
            text(name, "unread badge", scheme.onTertiary, scheme.tertiary)
            text(name, "muted unread badge", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "folder tab", scheme.onSurfaceVariant, scheme.surface)
            text(name, "selected folder tab on its pill", scheme.onSecondaryContainer, scheme.secondaryContainer)
            graphic(name, "online dot", legibleOn(scheme.surface, closeFriendsGreens), scheme.surface)
            text(name, "muted folder count on the pill", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "unread folder count on the pill", scheme.onTertiary, scheme.tertiary)
            text(name, "chat name", scheme.onSurface, scheme.surface)
            text(name, "chat preview", scheme.onSurfaceVariant, scheme.surface)
            text(name, "unread chat's time", scheme.primary, scheme.surface)
            text(name, "read chat's time", scheme.onSurfaceVariant, scheme.surface)
            text(name, "mention mark", scheme.tertiary, scheme.surface)
            graphic(name, "pin, checks and badges", scheme.primary, scheme.surface)
            graphic(name, "mute mark", scheme.onSurfaceVariant, scheme.surface)
            text(name, "pressed row's name", scheme.onSurface, scheme.surfaceContainerHighest)
            text(name, "pressed row's preview", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "pressed row's time", scheme.primary, scheme.surfaceContainerHighest)
            text(name, "pressed row's mention mark", scheme.tertiary, scheme.surfaceContainerHighest)
            text(name, "selected row's name", scheme.onSurface, scheme.secondaryContainer)
            text(name, "selected row's preview", scheme.onSurfaceVariant, scheme.secondaryContainer)
            text(name, "selected row's time", scheme.primary, scheme.secondaryContainer)
            text(name, "selected row's mention mark", scheme.tertiary, scheme.secondaryContainer)
            text(name, "app bar title", scheme.onSurface, scheme.surface)
            text(name, "search placeholder", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
            text(name, "search text", scheme.onSurface, scheme.surfaceContainerHighest)
            graphic(name, "toolbar's chosen item", scheme.onPrimaryContainer, scheme.primaryContainer)
            graphic(name, "toolbar's other items", scheme.onSurfaceVariant, scheme.surfaceContainer)
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
            text(name, "back badge count", scheme.onTertiary, scheme.tertiary)
            graphic(name, "back badge on its pill", scheme.tertiary, scheme.surfaceContainerLow)
            graphic(name, "back badge over the scrim", scheme.tertiary, scheme.surface)
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
            text(name, "row titles on their card", scheme.onSurface, scheme.surfaceContainer)
            text(name, "row subtitles on their card", scheme.onSurfaceVariant, scheme.surfaceContainer)
            graphic(name, "row icon on its tile", scheme.onSecondaryContainer, scheme.secondaryContainer)
            graphic(name, "storage rows' icons on their tile", scheme.primary, scheme.secondaryContainer)
            graphic(name, "storage rows' icons on their tile", scheme.tertiary, scheme.secondaryContainer)
            graphic(name, "storage rows' icons on their tile", scheme.secondary, scheme.secondaryContainer)
            graphic(name, "storage rows' icons on their tile", scheme.onSurfaceVariant, scheme.secondaryContainer)
            graphic(name, "storage rows' icons on their tile", scheme.onSecondaryContainer, scheme.secondaryContainer)
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
            text(name, "folder names and switch labels", scheme.onSurface, scheme.surfaceContainer)
            text(name, "folder names while dragged", scheme.onSurface, scheme.surfaceContainerHighest)
            text(name, "folder summaries and descriptions", scheme.onSurfaceVariant, scheme.surfaceContainer)
            text(name, "hint under the title, counts", scheme.onSurfaceVariant, scheme.surface)
            text(name, "section labels", scheme.primary, scheme.surface)
            graphic(name, "reorder handle", scheme.onSurfaceVariant, scheme.surfaceContainer)
            graphic(name, "folder tile's icon", scheme.onSecondaryContainer, scheme.secondaryContainer)
            text(name, "add button, shared note", scheme.onSecondaryContainer, scheme.secondaryContainer)
            text(name, "limit message", scheme.onTertiaryContainer, scheme.tertiaryContainer)
            text(name, "type toggle on", scheme.onPrimary, scheme.primary)
            graphic(name, "chosen icon's ring", scheme.primary, scheme.surface)
            graphic(name, "icon tiles", scheme.onSurface, scheme.surfaceContainerHighest)
            text(name, "delete folder", scheme.error, scheme.surface)
            text(name, "delete in the tab's menu", scheme.error, scheme.surfaceContainer)
            text(name, "menu items", scheme.onSurface, scheme.surfaceContainer)
            text(name, "chat kind in the picker", scheme.onSurfaceVariant, scheme.surface)
            text(name, "delete confirmation", scheme.error, scheme.surfaceContainerHighest)
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
            text(name, "day and service chips on the theme's background", scheme.onSecondaryContainer, scheme.secondaryContainer)
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
            text(name, "row title", scheme.onSurface, scheme.surfaceContainer)
            text(name, "row subtitle and links", scheme.onSurfaceVariant, scheme.surfaceContainer)
            text(name, "month header", scheme.onSurfaceVariant, scheme.surface)
            graphic(name, "file and link tile icon", scheme.onPrimaryContainer, scheme.primaryContainer)
            text(name, "tab label", scheme.onSurface, scheme.surfaceContainerHigh)
            text(name, "chosen tab label", scheme.onPrimary, scheme.primary)
            graphic(name, "play and pause button", scheme.onPrimary, scheme.primary)
            graphic(name, "waveform", scheme.onSurface, scheme.surfaceContainer)
            graphic(name, "waveform, unplayed", scheme.onSurface.fadedOver(scheme.surfaceContainer, 0.38f, GRAPHIC_CONTRAST), scheme.surfaceContainer)
            text(name, "menu's delete line", scheme.error, scheme.surfaceContainer)
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
        for (dark in listOf(false, true)) {
            val side = if (dark) "dark" else "light"
            add("brand $side" to brandScheme(dark))
            for ((seedName, seed) in wallpaperSeeds) add("$seedName $side" to wallpaperScheme(seed, dark))
            add("monochrome $side" to monochromeScheme(dark))
        }
    }
}
