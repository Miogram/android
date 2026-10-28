package app.miogram.data

import android.content.Context
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.Appearance
import cn.ellerotta.core.model.AutoDownloadSettings
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.MusicModes
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.ThemeMode
import cn.ellerotta.core.model.VOICE_SPEEDS
import cn.ellerotta.core.model.decodeRecentEmoji
import cn.ellerotta.core.model.encodeRecentEmoji
import cn.ellerotta.core.model.decodeEmojiTones
import cn.ellerotta.core.model.emojiTonesWith
import cn.ellerotta.core.model.encodeEmojiTones
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        Appearance(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.System,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        ),
    )
    private val privacyState = MutableStateFlow(
        AppPrivacy(
            lock = prefs.getBoolean(KEY_LOCK, false),
            lockAfterMinutes = prefs.getInt(KEY_LOCK_AFTER, AppPrivacy().lockAfterMinutes),
            secureWindow = prefs.getBoolean(KEY_SECURE_WINDOW, false),
            hideNotificationContent = prefs.getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, false),
        ),
    )
    private val autoSaveState = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SAVE_DOWNLOADS, false))

    val appearance: StateFlow<Appearance> = state.asStateFlow()

    val appPrivacy: StateFlow<AppPrivacy> = privacyState.asStateFlow()

    val autoSaveDownloads: StateFlow<Boolean> = autoSaveState.asStateFlow()

    fun setAutoSaveDownloads(on: Boolean) {
        autoSaveState.value = on
        prefs.edit().putBoolean(KEY_AUTO_SAVE_DOWNLOADS, on).apply()
    }

    private val proximityState = MutableStateFlow(prefs.getBoolean(KEY_PROXIMITY_ALERTS, true))

    val proximityAlerts: StateFlow<Boolean> = proximityState.asStateFlow()

    fun setProximityAlerts(on: Boolean) {
        proximityState.value = on
        prefs.edit().putBoolean(KEY_PROXIMITY_ALERTS, on).apply()
    }

    private val autoDownloadState = MutableStateFlow(AutoDownloadSettings.decode(prefs.getString(KEY_AUTO_DOWNLOAD, null)))

    val autoDownload: StateFlow<AutoDownloadSettings> = autoDownloadState.asStateFlow()

    fun setAutoDownload(settings: AutoDownloadSettings) {
        autoDownloadState.value = settings
        prefs.edit().putString(KEY_AUTO_DOWNLOAD, settings.encode()).apply()
    }

    private val voiceSpeedState = MutableStateFlow(prefs.getFloat(KEY_VOICE_SPEED, 1f).takeIf { it in VOICE_SPEEDS } ?: 1f)

    val voiceSpeed: StateFlow<Float> = voiceSpeedState.asStateFlow()

    fun setVoiceSpeed(speed: Float) {
        voiceSpeedState.value = speed
        prefs.edit().putFloat(KEY_VOICE_SPEED, speed).apply()
    }

    private val longSpeedState = MutableStateFlow(prefs.getFloat(KEY_LONG_SPEED, 1f).takeIf { it in VOICE_SPEEDS } ?: 1f)

    val longSpeed: StateFlow<Float> = longSpeedState.asStateFlow()

    fun setLongSpeed(speed: Float) {
        longSpeedState.value = speed
        prefs.edit().putFloat(KEY_LONG_SPEED, speed).apply()
    }

    private val musicModesState = MutableStateFlow(
        MusicModes(
            shuffle = prefs.getBoolean(KEY_MUSIC_SHUFFLE, false),
            reverse = prefs.getBoolean(KEY_MUSIC_REVERSE, false),
            repeat = MusicRepeat.entries.firstOrNull { it.name == prefs.getString(KEY_MUSIC_REPEAT, null) } ?: MusicRepeat.Off,
        ),
    )

    val musicModes: StateFlow<MusicModes> = musicModesState.asStateFlow()

    fun setMusicModes(modes: MusicModes) {
        musicModesState.value = modes
        prefs.edit()
            .putBoolean(KEY_MUSIC_SHUFFLE, modes.shuffle)
            .putBoolean(KEY_MUSIC_REVERSE, modes.reverse)
            .putString(KEY_MUSIC_REPEAT, modes.repeat.name)
            .apply()
    }

    private val recentEmojiState = MutableStateFlow(decodeRecentEmoji(prefs.getString(KEY_RECENT_EMOJI, null)))

    val recentEmoji: StateFlow<List<PickedEmoji>> = recentEmojiState.asStateFlow()

    fun setRecentEmoji(list: List<PickedEmoji>) {
        recentEmojiState.value = list
        prefs.edit().putString(KEY_RECENT_EMOJI, encodeRecentEmoji(list)).apply()
    }

    private val emojiTonesState = MutableStateFlow(decodeEmojiTones(prefs.getString(KEY_EMOJI_TONES, null)))

    val emojiTones: StateFlow<Map<String, String>> = emojiTonesState.asStateFlow()

    fun setEmojiTone(base: String, toned: String) {
        val tones = emojiTonesWith(emojiTonesState.value, base, toned)
        emojiTonesState.value = tones
        prefs.edit().putString(KEY_EMOJI_TONES, encodeEmojiTones(tones)).apply()
    }

    private val keyboardHeightState = MutableStateFlow(prefs.getInt(KEY_KEYBOARD_HEIGHT, 0))

    val keyboardHeight: StateFlow<Int> = keyboardHeightState.asStateFlow()

    fun setKeyboardHeight(dp: Int) {
        if (keyboardHeightState.value == dp) return
        keyboardHeightState.value = dp
        prefs.edit().putInt(KEY_KEYBOARD_HEIGHT, dp).apply()
    }

    fun setAppearance(appearance: Appearance) {
        state.value = appearance
        prefs.edit().putString(KEY_MODE, appearance.mode.name).putBoolean(KEY_DYNAMIC, appearance.dynamicColor).apply()
    }

    fun setAppPrivacy(privacy: AppPrivacy) {
        privacyState.value = privacy
        prefs.edit()
            .putBoolean(KEY_LOCK, privacy.lock)
            .putInt(KEY_LOCK_AFTER, privacy.lockAfterMinutes)
            .putBoolean(KEY_SECURE_WINDOW, privacy.secureWindow)
            .putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, privacy.hideNotificationContent)
            .apply()
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_LOCK = "app_lock"
        const val KEY_LOCK_AFTER = "lock_after_minutes"
        const val KEY_SECURE_WINDOW = "secure_window"
        const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
        const val KEY_AUTO_SAVE_DOWNLOADS = "auto_save_downloads"
        const val KEY_PROXIMITY_ALERTS = "proximity_alerts"
        const val KEY_AUTO_DOWNLOAD = "auto_download"
        const val KEY_VOICE_SPEED = "voice_speed"
        const val KEY_LONG_SPEED = "long_speed"
        const val KEY_MUSIC_SHUFFLE = "music_shuffle"
        const val KEY_MUSIC_REVERSE = "music_reverse"
        const val KEY_MUSIC_REPEAT = "music_repeat"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        const val KEY_EMOJI_TONES = "emoji_tones"
        const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
    }
}
