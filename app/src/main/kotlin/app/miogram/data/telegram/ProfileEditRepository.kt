package app.miogram.data.telegram

import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.ktproto.api.functions.account.CheckUsername
import cn.ellerotta.ktproto.api.functions.account.ReorderUsernames
import cn.ellerotta.ktproto.api.functions.account.ToggleUsername
import cn.ellerotta.ktproto.api.functions.account.UpdateBirthday
import cn.ellerotta.ktproto.api.functions.account.UpdatePersonalChannel
import cn.ellerotta.ktproto.api.functions.account.UpdateProfile
import cn.ellerotta.ktproto.api.functions.account.UpdateUsername
import cn.ellerotta.ktproto.api.functions.channels.GetAdminedPublicChannels
import cn.ellerotta.ktproto.api.functions.photos.DeletePhotos
import cn.ellerotta.ktproto.api.functions.photos.UpdateProfilePhoto
import cn.ellerotta.ktproto.api.functions.photos.UploadProfilePhoto
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.InputChannelEmpty
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.types.messages.ChatsSlice
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import cn.ellerotta.ktproto.api.types.Birthday as TlBirthday

@Singleton
class ProfileEditRepository @Inject constructor(
    private val telegram: TelegramClientHolder,
    private val profiles: ProfileRepository,
    private val appConfig: AppConfigRepository,
) {
    private val registry get() = telegram.peers

    suspend fun aboutLimit(premium: Boolean): Int = appConfig.config().aboutLimit(premium)

    private suspend fun refreshed(): ProfileDetails = profiles.profile(null)

    suspend fun updateProfile(firstName: String?, lastName: String?, about: String?): ProfileDetails {
        registry.rememberUsers(listOf(telegram.client().invoke(UpdateProfile(firstName = firstName, lastName = lastName, about = about))))
        return refreshed()
    }

    suspend fun checkUsername(username: String): UsernameCheck = try {
        if (telegram.client().invoke(CheckUsername(username))) UsernameCheck.Free else UsernameCheck.Taken
    } catch (e: RpcException) {
        when (e.errorMessage) {
            "USERNAME_INVALID" -> UsernameCheck.Invalid(null)
            "USERNAME_OCCUPIED" -> UsernameCheck.Taken
            "USERNAME_PURCHASE_AVAILABLE" -> UsernameCheck.ForSale
            else -> throw e
        }
    }

    suspend fun updateUsername(username: String): ProfileDetails {
        try {
            registry.rememberUsers(listOf(telegram.client().invoke(UpdateUsername(username))))
        } catch (e: RpcException) {
            if (e.errorMessage != "USERNAME_NOT_MODIFIED") throw e
        }
        return refreshed()
    }

    suspend fun toggleUsername(username: String, active: Boolean): ProfileDetails {
        telegram.client().invoke(ToggleUsername(username, active))
        return refreshed()
    }

    suspend fun reorderUsernames(order: List<String>): ProfileDetails {
        telegram.client().invoke(ReorderUsernames(order))
        return refreshed()
    }

    suspend fun setBirthday(birthday: Birthday?): ProfileDetails {
        telegram.client().invoke(UpdateBirthday(birthday?.let { TlBirthday(it.day, it.month, it.year) }))
        return refreshed()
    }

    suspend fun personalChannelChoices(): List<PersonalChannel> {
        val chats = when (val answer = telegram.client().invoke(GetAdminedPublicChannels(forPersonal = true))) {
            is Chats -> answer.chats
            is ChatsSlice -> answer.chats
        }
        registry.rememberChats(chats)
        registry.remember(chats)
        return chats.filterIsInstance<Channel>().map { channel ->
            val key = "ch${channel.id}"
            PersonalChannel(key, channel.title, registry.avatarKey(photoRef(key, channel.photo)), channel.verified, channel.participantsCount)
        }
    }

    suspend fun setPersonalChannel(key: String?): ProfileDetails {
        telegram.client().invoke(UpdatePersonalChannel(if (key == null) InputChannelEmpty else registry.channel(key)))
        return refreshed()
    }

    suspend fun uploadPhoto(jpeg: ByteArray): ProfileDetails {
        val client = telegram.client()
        var uploaded: TypeInputFile? = null
        client.uploadFile(PHOTO_NAME, UploadSource.of(jpeg)).collect { step -> if (step is Upload.Done) uploaded = step.file }
        val answer = client.invoke(UploadProfilePhoto(file = uploaded ?: error("the upload ended without a file")))
        registry.rememberUsers(answer.users)
        return photosChanged()
    }

    suspend fun setMainPhoto(mediaKey: String): ProfileDetails {
        withPhoto(mediaKey) { photo -> telegram.client().invoke(UpdateProfilePhoto(id = photo)).also { registry.rememberUsers(it.users) } }
        return photosChanged()
    }

    suspend fun deletePhoto(mediaKey: String): ProfileDetails {
        withPhoto(mediaKey) { photo -> telegram.client().invoke(DeletePhotos(listOf(photo))) }
        return photosChanged()
    }

    private suspend fun photosChanged(): ProfileDetails {
        telegram.userId?.let { id -> telegram.cache?.changeProfile("u$id") { it.copy(photos = null) } }
        return refreshed()
    }

    private suspend fun <T> withPhoto(mediaKey: String, call: suspend (cn.ellerotta.ktproto.api.types.InputPhoto) -> T): T {
        fun photo() = registry.inputPhotos[mediaKey] ?: error("unknown photo $mediaKey")
        return try {
            call(photo())
        } catch (e: RpcException) {
            if (!e.errorMessage.startsWith("FILE_REFERENCE_") || !telegram.refreshReference(mediaKey)) throw e
            call(photo())
        }
    }

    private companion object {
        const val PHOTO_NAME = "profile.jpg"
    }
}
