package cn.ellerotta.core.model

enum class PrivacyKey {
    LastSeen,
    PhoneNumber,
    FindByPhone,
    ProfilePhoto,
    Forwards,
    Calls,
    PeerToPeer,
    Invites,
    VoiceMessages,
    Bio,
    Birthday,
    Gifts,
    ;

    val choices: List<PrivacyBase>
        get() = if (this == FindByPhone) listOf(PrivacyBase.Everybody, PrivacyBase.Contacts) else listOf(PrivacyBase.Everybody, PrivacyBase.Contacts, PrivacyBase.Nobody)
}

enum class PrivacyBase { Everybody, Contacts, CloseFriends, Nobody }

data class PrivacySetting(val key: PrivacyKey, val base: PrivacyBase, val allowed: Int = 0, val disallowed: Int = 0)

data class GlobalPrivacy(val archiveAndMuteNonContacts: Boolean = false, val hideReadMarks: Boolean = false)

data class PrivacyState(
    val settings: List<PrivacySetting> = emptyList(),
    val global: GlobalPrivacy? = null,
    val accountTtlDays: Int? = null,
    val blockedCount: Int? = null,
    val twoStepEnabled: Boolean? = null,
) {
    companion object {
        val TTL_CHOICES = listOf(30, 90, 180, 365, 548, 730)
    }
}

data class AppPrivacy(
    val lock: Boolean = false,
    val lockAfterMinutes: Int = 1,
    val secureWindow: Boolean = false,
    val hideNotificationContent: Boolean = false,
) {
    companion object {
        val LOCK_AFTER_CHOICES = listOf(0, 1, 5, 15, 60)
    }
}
