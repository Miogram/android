package app.miogram

import app.miogram.data.telegram.activityOf
import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.ktproto.api.types.SendMessageChooseContactAction
import cn.ellerotta.ktproto.api.types.SendMessageChooseStickerAction
import cn.ellerotta.ktproto.api.types.SendMessageGamePlayAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordRoundAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordVideoAction
import cn.ellerotta.ktproto.api.types.SendMessageTypingAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadDocumentAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadPhotoAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadRoundAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadVideoAction
import kotlin.test.assertEquals
import org.junit.Test

class ActivityMappingTest {
    @Test
    fun everyActionTheHeaderShowsHasItsKind() {
        assertEquals(ActivityKind.Typing, activityOf(SendMessageTypingAction))
        assertEquals(ActivityKind.RecordVoice, activityOf(SendMessageRecordAudioAction))
        assertEquals(ActivityKind.UploadVoice, activityOf(SendMessageUploadAudioAction(5)))
        assertEquals(ActivityKind.UploadPhoto, activityOf(SendMessageUploadPhotoAction(5)))
        assertEquals(ActivityKind.UploadVideo, activityOf(SendMessageUploadVideoAction(5)))
        assertEquals(ActivityKind.UploadFile, activityOf(SendMessageUploadDocumentAction(5)))
        assertEquals(ActivityKind.RecordRound, activityOf(SendMessageRecordRoundAction))
        assertEquals(ActivityKind.RecordRound, activityOf(SendMessageRecordVideoAction))
        assertEquals(ActivityKind.UploadRound, activityOf(SendMessageUploadRoundAction(5)))
        assertEquals(ActivityKind.ChooseSticker, activityOf(SendMessageChooseStickerAction))
        assertEquals(ActivityKind.PlayGame, activityOf(SendMessageGamePlayAction))
    }

    @Test
    fun theOthersShowAsTyping() {
        assertEquals(ActivityKind.Typing, activityOf(SendMessageChooseContactAction))
    }
}
