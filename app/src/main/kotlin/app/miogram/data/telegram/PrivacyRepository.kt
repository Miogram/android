package app.miogram.data.telegram

import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.ktproto.api.functions.account.GetAccountTTL
import cn.ellerotta.ktproto.api.functions.account.GetGlobalPrivacySettings
import cn.ellerotta.ktproto.api.functions.account.GetPrivacy
import cn.ellerotta.ktproto.api.functions.account.SetAccountTTL
import cn.ellerotta.ktproto.api.functions.account.SetGlobalPrivacySettings
import cn.ellerotta.ktproto.api.functions.account.SetPrivacy
import cn.ellerotta.ktproto.api.types.AccountDaysTTL
import cn.ellerotta.ktproto.api.types.GlobalPrivacySettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    fun stored(): PrivacyState? = telegram.memory["privacy"]

    fun remember(state: PrivacyState) {
        telegram.memory["privacy"] = state
    }

    suspend fun privacy(key: PrivacyKey): PrivacySetting =
        privacySettingOf(key, telegram.client().invoke(GetPrivacy(inputKeyOf(key))).rules)

    suspend fun setPrivacy(key: PrivacyKey, base: PrivacyBase): PrivacySetting {
        val client = telegram.client()
        val input = inputKeyOf(key)
        val current = client.invoke(GetPrivacy(input))
        val updated = client.invoke(SetPrivacy(input, inputRulesOf(current.rules, telegram.peers::inputUser, base)))
        return privacySettingOf(key, updated.rules)
    }

    suspend fun globalPrivacy(): GlobalPrivacy = globalOf(telegram.client().invoke(GetGlobalPrivacySettings))

    suspend fun setGlobalPrivacy(global: GlobalPrivacy): GlobalPrivacy {
        val client = telegram.client()
        val current = client.invoke(GetGlobalPrivacySettings)
        val settings = GlobalPrivacySettings(
            archiveAndMuteNewNoncontactPeers = global.archiveAndMuteNonContacts,
            keepArchivedUnmuted = current.keepArchivedUnmuted,
            keepArchivedFolders = current.keepArchivedFolders,
            hideReadMarks = global.hideReadMarks,
            newNoncontactPeersRequirePremium = current.newNoncontactPeersRequirePremium,
            displayGiftsButton = current.displayGiftsButton,
            noncontactPeersPaidStars = current.noncontactPeersPaidStars,
            disallowedGifts = current.disallowedGifts,
        )
        return globalOf(client.invoke(SetGlobalPrivacySettings(settings)))
    }

    suspend fun accountTtl(): Int = telegram.client().invoke(GetAccountTTL).days

    suspend fun setAccountTtl(days: Int) {
        telegram.client().invoke(SetAccountTTL(AccountDaysTTL(days)))
    }

    private fun globalOf(settings: GlobalPrivacySettings) =
        GlobalPrivacy(archiveAndMuteNonContacts = settings.archiveAndMuteNewNoncontactPeers, hideReadMarks = settings.hideReadMarks)
}
