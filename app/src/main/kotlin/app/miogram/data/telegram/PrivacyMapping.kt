package app.miogram.data.telegram

import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyAbout
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyAddedByPhone
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyBirthday
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyChatInvite
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyForwards
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyPhoneCall
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyPhoneNumber
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyPhoneP2P
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyProfilePhoto
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyStarGiftsAutoSave
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyStatusTimestamp
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyVoiceMessages
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowBots
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowChatParticipants
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowPremium
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowUsers
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowBots
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowChatParticipants
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowUsers
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowBots
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowChatParticipants
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowPremium
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowUsers
import cn.ellerotta.ktproto.api.types.PrivacyValueDisallowBots
import cn.ellerotta.ktproto.api.types.PrivacyValueDisallowChatParticipants
import cn.ellerotta.ktproto.api.types.PrivacyValueDisallowUsers
import cn.ellerotta.ktproto.api.types.TypeInputPrivacyKey
import cn.ellerotta.ktproto.api.types.TypeInputPrivacyRule
import cn.ellerotta.ktproto.api.types.TypeInputUser
import cn.ellerotta.ktproto.api.types.TypePrivacyRule

internal fun inputKeyOf(key: PrivacyKey): TypeInputPrivacyKey = when (key) {
    PrivacyKey.LastSeen -> InputPrivacyKeyStatusTimestamp
    PrivacyKey.PhoneNumber -> InputPrivacyKeyPhoneNumber
    PrivacyKey.FindByPhone -> InputPrivacyKeyAddedByPhone
    PrivacyKey.ProfilePhoto -> InputPrivacyKeyProfilePhoto
    PrivacyKey.Forwards -> InputPrivacyKeyForwards
    PrivacyKey.Calls -> InputPrivacyKeyPhoneCall
    PrivacyKey.PeerToPeer -> InputPrivacyKeyPhoneP2P
    PrivacyKey.Invites -> InputPrivacyKeyChatInvite
    PrivacyKey.VoiceMessages -> InputPrivacyKeyVoiceMessages
    PrivacyKey.Bio -> InputPrivacyKeyAbout
    PrivacyKey.Birthday -> InputPrivacyKeyBirthday
    PrivacyKey.Gifts -> InputPrivacyKeyStarGiftsAutoSave
}

internal fun privacyBaseOf(rules: List<TypePrivacyRule>): PrivacyBase = when {
    PrivacyValueAllowAll in rules -> PrivacyBase.Everybody
    PrivacyValueAllowContacts in rules -> PrivacyBase.Contacts
    PrivacyValueAllowCloseFriends in rules -> PrivacyBase.CloseFriends
    else -> PrivacyBase.Nobody
}

internal fun privacySettingOf(key: PrivacyKey, rules: List<TypePrivacyRule>): PrivacySetting = PrivacySetting(
    key = key,
    base = privacyBaseOf(rules),
    allowed = rules.sumOf { rule ->
        when (rule) {
            is PrivacyValueAllowUsers -> rule.users.size
            is PrivacyValueAllowChatParticipants -> rule.chats.size
            else -> 0
        }
    },
    disallowed = rules.sumOf { rule ->
        when (rule) {
            is PrivacyValueDisallowUsers -> rule.users.size
            is PrivacyValueDisallowChatParticipants -> rule.chats.size
            else -> 0
        }
    },
)

internal fun inputRulesOf(rules: List<TypePrivacyRule>, inputUser: (Long) -> TypeInputUser?, base: PrivacyBase): List<TypeInputPrivacyRule> {
    fun inputUsers(ids: List<Long>) = ids.mapNotNull(inputUser)
    val exceptions = rules.mapNotNull { rule ->
        when (rule) {
            is PrivacyValueAllowUsers -> InputPrivacyValueAllowUsers(inputUsers(rule.users))
            is PrivacyValueDisallowUsers -> InputPrivacyValueDisallowUsers(inputUsers(rule.users))
            is PrivacyValueAllowChatParticipants -> InputPrivacyValueAllowChatParticipants(rule.chats)
            is PrivacyValueDisallowChatParticipants -> InputPrivacyValueDisallowChatParticipants(rule.chats)
            PrivacyValueAllowPremium -> InputPrivacyValueAllowPremium
            PrivacyValueAllowBots -> InputPrivacyValueAllowBots
            PrivacyValueDisallowBots -> InputPrivacyValueDisallowBots
            else -> null
        }
    }
    val last = when (base) {
        PrivacyBase.Everybody -> InputPrivacyValueAllowAll
        PrivacyBase.Contacts -> InputPrivacyValueAllowContacts
        PrivacyBase.CloseFriends -> InputPrivacyValueAllowCloseFriends
        PrivacyBase.Nobody -> InputPrivacyValueDisallowAll
    }
    return exceptions + last
}
