package app.miogram.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.miogram.data.telegram.TelegramClientHolder
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class WebAppStore @Inject constructor(private val telegram: TelegramClientHolder) {
    private val dir: File get() = File(telegram.filesDir, DIR)

    suspend fun storage(bot: String, secure: Boolean): Map<String, String> = io {
        val file = read(bot)
        val json = if (secure) file.optString(SECURE).takeIf { it.isNotEmpty() }?.let { JSONObject(decrypt(it)) } else file.optJSONObject(DEVICE)
        json?.let { values -> values.keys().asSequence().associateWith { values.optString(it) } }.orEmpty()
    }

    suspend fun saveStorage(bot: String, secure: Boolean, values: Map<String, String>) = io {
        update(bot) {
            when {
                !secure -> put(DEVICE, JSONObject(values))
                values.isEmpty() -> remove(SECURE)
                else -> put(SECURE, encrypt(JSONObject(values).toString()))
            }
        }
    }

    suspend fun permission(bot: String, kind: String): Boolean? = io {
        read(bot).optJSONObject(PERMISSIONS)?.takeIf { it.has(kind) }?.optBoolean(kind)
    }

    suspend fun setPermission(bot: String, kind: String, granted: Boolean) = io {
        update(bot) { put(PERMISSIONS, (optJSONObject(PERMISSIONS) ?: JSONObject()).put(kind, granted)) }
    }

    suspend fun biometryToken(bot: String): String? = io { read(bot).optString(TOKEN).takeIf { it.isNotEmpty() }?.let(::decrypt) }

    suspend fun saveBiometryToken(bot: String, token: String?) = io {
        update(bot) { if (token == null) remove(TOKEN) else put(TOKEN, encrypt(token)) }
    }

    suspend fun deviceId(): String = io {
        val file = File(dir, DEVICE_ID)
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            dir.mkdirs()
            file.writeText(it)
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { synchronized(this@WebAppStore) { block() } }

    private fun fileOf(bot: String) = File(dir, "${bot.filter { it.isLetterOrDigit() }}.json")

    private fun read(bot: String): JSONObject = fileOf(bot).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: JSONObject()

    private fun update(bot: String, change: JSONObject.() -> Unit) {
        val json = read(bot).apply(change)
        dir.mkdirs()
        val file = fileOf(bot)
        val next = File(dir, "${file.name}.tmp")
        next.writeText(json.toString())
        if (!next.renameTo(file)) error("cannot write ${file.name}")
    }

    private fun encrypt(text: String): String = keystore {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(text.encodeToByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(text: String): String = keystore {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private inline fun <T> keystore(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw UnsupportedOperationException("no keystore", e)
    }

    companion object {
        const val DIR = "webapps"

        private const val DEVICE = "device"
        private const val SECURE = "secure"
        private const val TOKEN = "token"
        private const val PERMISSIONS = "permissions"
        private const val DEVICE_ID = "device_id"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "miogram_web_apps"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
