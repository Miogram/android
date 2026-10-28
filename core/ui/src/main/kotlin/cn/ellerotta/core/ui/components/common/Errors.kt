package cn.ellerotta.core.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.ui.R

@Composable
fun errorText(error: UiError): String = when (error) {
    is UiError.Message -> error.text
    UiError.MissingApiCredentials -> stringResource(R.string.error_api_missing)
    UiError.MicrophoneDenied -> stringResource(R.string.error_microphone_denied)
    UiError.CameraDenied -> stringResource(R.string.error_camera_denied)
    UiError.LocationDenied -> stringResource(R.string.error_location_denied)
    UiError.LocationUnavailable -> stringResource(R.string.error_location_unavailable)
    UiError.PhotoUnreadable -> stringResource(R.string.error_photo_unreadable)
    UiError.NoScreenLock -> stringResource(R.string.error_no_screen_lock)
    UiError.PrivateChat -> stringResource(R.string.error_private_chat)
    UiError.SessionEnded -> stringResource(R.string.error_session_ended)
    UiError.JoinBotCheck -> stringResource(R.string.error_join_bot_check)
    is UiError.ReactionLimit -> pluralStringResource(R.plurals.error_reaction_limit, error.limit, error.limit)
    is UiError.AttachAppNotHere -> stringResource(if (error.added) R.string.error_attach_app_added else R.string.error_attach_app_not_here, error.name)
    is UiError.Rpc -> {
        val wait = error.message.substringAfterLast('_').toIntOrNull()
        when {
            error.message == "USERNAME_NOT_OCCUPIED" || error.message == "USERNAME_INVALID" -> stringResource(R.string.error_username_unknown)
            error.message == "USERNAME_OCCUPIED" -> stringResource(R.string.profile_edit_username_taken)
            error.message == "USERNAME_PURCHASE_AVAILABLE" -> stringResource(R.string.profile_edit_username_for_sale)
            error.message == "USERNAMES_ACTIVE_TOO_MUCH" -> stringResource(R.string.error_usernames_too_much)
            error.message == "FIRSTNAME_INVALID" -> stringResource(R.string.error_name_invalid)
            error.message == "ABOUT_TOO_LONG" -> stringResource(R.string.error_about_too_long)
            error.message == "BIRTHDAY_INVALID" -> stringResource(R.string.error_birthday_invalid)
            error.message == "PHOTO_CROP_SIZE_SMALL" || error.message == "PHOTO_INVALID_DIMENSIONS" -> stringResource(R.string.error_photo_small)
            error.message == "PHONE_NOT_OCCUPIED" -> stringResource(R.string.error_phone_unknown)
            error.message == "INVITE_HASH_EXPIRED" || error.message == "INVITE_HASH_INVALID" || error.message == "INVITE_HASH_EMPTY" -> stringResource(R.string.error_invite_invalid)
            error.message == "INVITE_REQUEST_SENT" -> stringResource(R.string.error_invite_request_sent)
            error.message == "STICKERSET_INVALID" -> stringResource(R.string.error_sticker_set_unknown)
            error.message == "CHANNELS_TOO_MUCH" -> stringResource(R.string.error_channels_too_much)
            error.message.startsWith("FLOOD_WAIT_") && wait != null -> floodText(wait)
            error.message == "PHONE_NUMBER_INVALID" -> stringResource(R.string.error_phone_invalid)
            error.message == "PHONE_NUMBER_BANNED" -> stringResource(R.string.error_phone_banned)
            error.message == "PHONE_NUMBER_FLOOD" -> stringResource(R.string.error_phone_flood)
            error.message == "PHONE_CODE_INVALID" -> stringResource(R.string.error_code_invalid)
            error.message == "PHONE_CODE_EXPIRED" -> stringResource(R.string.error_code_expired)
            error.message == "PASSWORD_HASH_INVALID" -> stringResource(R.string.error_password_invalid)
            error.message == "CODE_INVALID" || error.message == "CODE_EMPTY" -> stringResource(R.string.error_code_invalid)
            error.message == "EMAIL_HASH_EXPIRED" -> stringResource(R.string.error_code_expired)
            error.message == "EMAIL_INVALID" -> stringResource(R.string.error_email_invalid)
            error.message == "NEW_SETTINGS_INVALID" || error.message == "NEW_SALT_INVALID" -> stringResource(R.string.error_two_step_settings)
            error.message == "PASSWORD_RECOVERY_NA" || error.message == "PASSWORD_RECOVERY_EXPIRED" -> stringResource(R.string.error_two_step_no_email)
            error.message == "API_ID_INVALID" || error.message == "API_ID_PUBLISHED_FLOOD" -> stringResource(R.string.error_api_id)
            error.message == "TO_LANG_INVALID" -> stringResource(R.string.error_translate_language)
            error.message == "QUOTA_EXCEEDED" -> stringResource(R.string.error_translate_quota)
            else -> stringResource(R.string.error_rpc, error.code, error.message)
        }
    }
}

@Composable
private fun floodText(seconds: Int): String = when {
    seconds >= 3600 -> pluralStringResource(R.plurals.error_flood_hours, seconds / 3600, seconds / 3600)
    seconds >= 60 -> pluralStringResource(R.plurals.error_flood_minutes, seconds / 60, seconds / 60)
    else -> pluralStringResource(R.plurals.error_flood_seconds, seconds, seconds)
}
