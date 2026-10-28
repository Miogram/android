package cn.ellerotta.core.model

import java.io.File

data class LogLine(val time: Long, val text: String)

data class ConfigSummary(val thisDc: Int, val testMode: Boolean, val dcs: List<String>)

enum class CodeType { App, Sms, Call, FlashCall, MissedCall, Email, Fragment, Unknown }

data class CodeRequest(
    val phone: String,
    val hash: String,
    val codeLength: Int?,
    val homeDc: Int,
    val type: CodeType = CodeType.Unknown,
    val nextType: CodeType? = null,
    val timeoutSeconds: Int? = null,
    val sentAtMillis: Long = 0,
)

sealed interface UiError {
    data class Rpc(val code: Int, val message: String) : UiError
    data class Message(val text: String) : UiError

    data object MissingApiCredentials : UiError

    data object MicrophoneDenied : UiError

    data object CameraDenied : UiError

    data object LocationDenied : UiError

    data object LocationUnavailable : UiError

    data object PhotoUnreadable : UiError

    data object NoScreenLock : UiError

    data object PrivateChat : UiError

    data object SessionEnded : UiError

    data object JoinBotCheck : UiError

    data class ReactionLimit(val limit: Int) : UiError

    data class AttachAppNotHere(val name: String, val added: Boolean) : UiError
}

data class MediaItem(
    val key: String,
    val fileName: String,
    val size: Long?,
    val dcId: Int,
    val isPhoto: Boolean,
    val kind: MediaKind? = null,
    val emoji: String? = null,
    val animated: Boolean = false,
    val thumb: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val waveform: List<Int> = emptyList(),
    val sizes: List<MediaSize> = emptyList(),
    val mimeType: String? = null,
    val textColor: Boolean = false,
    val outline: String? = null,
    val title: String? = null,
    val performer: String? = null,
    val documentId: Long = 0,
    val spoiler: Boolean = false,
)

data class VoiceRecording(val elapsedMs: Long, val level: Float)

data class VoicePlayback(val key: String, val playing: Boolean, val positionMs: Long, val durationMs: Long, val loading: Boolean = false, val speed: Float = 1f)

sealed interface ChatAction {
    data object Typing : ChatAction

    data object Cancel : ChatAction

    data object RecordVoice : ChatAction

    data class Upload(val kind: UploadKind, val progress: Int) : ChatAction

    data class StopDraft(val randomId: Long) : ChatAction
}

enum class UploadKind { Photo, Video, Voice, File }

data class FoundMessages(val messages: List<MessageItem>, val total: Int)

data class ChatSearchState(
    val query: String = "",
    val results: List<MessageItem> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loading: Boolean = false,
)

data class MessageItem(
    val id: Int,
    val text: String,
    val out: Boolean,
    val date: Int,
    val media: MediaItem? = null,
    val senderName: String? = null,
    val senderId: Long = 0,
    val read: Boolean = false,
    val service: ServiceKind? = null,
    val pending: Boolean = false,
    val replyTo: Int? = null,
    val edited: Boolean = false,
    val link: LinkPreview? = null,
    val gift: GiftAction? = null,
    val entities: List<TextEntity> = emptyList(),
    val reactions: List<ReactionCount> = emptyList(),
    val reactorsListed: Boolean = false,
    val forward: ForwardInfo? = null,
    val topicId: Int? = null,
    val viaBot: String? = null,
    val buttons: List<List<BotButton>> = emptyList(),
    val keyboard: KeyboardChange? = null,
    val article: ArticleMessage? = null,
    val streaming: Boolean = false,
    val replacedDraft: Int? = null,
    val ephemeral: EphemeralInfo? = null,
    val threadId: Int? = null,
    val replies: RepliesInfo? = null,
    val poll: Poll? = null,
    val place: GeoPlace? = null,
    val contact: ContactCard? = null,
    val proximity: ProximityAlert? = null,
    val translation: MessageTranslation? = null,
    val mediaUnread: Boolean = false,
    val groupedId: Long? = null,
    val senderAvatar: String? = null,
    val senderKey: String? = null,
    val call: CallInfo? = null,
    val failed: Boolean = false,
    val wallpaper: WallpaperAction? = null,
    val senderRank: String? = null,
    val senderEmojiStatus: Long? = null,
    val added: List<String> = emptyList(),
)

enum class CallEnd { Missed, Busy, Disconnect, Hangup, Other }

data class CallInfo(val video: Boolean = false, val end: CallEnd? = null, val duration: Int = 0)

data class ForwardInfo(val name: String, val link: String? = null)

data class ProfileInfo(val id: Long, val name: String, val phone: String?, val username: String?, val avatarKey: String?)

sealed interface DownloadState {
    data class Progress(val downloaded: Long, val total: Long?) : DownloadState
    data class Done(val file: File) : DownloadState
}

data class ForwardPreview(val messages: List<MessageItem>, val fromTitle: String)
