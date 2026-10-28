package cn.ellerotta.core.ui.components.chat

enum class WebAppPermission { Biometry, Location }

data class WebAppLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val course: Double? = null,
    val speed: Double? = null,
    val horizontalAccuracy: Double? = null,
    val verticalAccuracy: Double? = null,
    val courseAccuracy: Double? = null,
    val speedAccuracy: Double? = null,
)

interface WebAppDevice {
    suspend fun canWrite(bot: String): Boolean = false

    suspend fun allowWrite(bot: String): Boolean = false

    suspend fun sharePhone(bot: String): Boolean = false

    suspend fun invokeCustomMethod(bot: String, method: String, params: String): Result<String> = Result.failure(UnsupportedOperationException(UNSUPPORTED))

    suspend fun clipboardAllowed(bot: String): Boolean = false

    suspend fun clipboardText(): String? = null

    suspend fun storage(bot: String, secure: Boolean): Map<String, String> = throw UnsupportedOperationException(UNSUPPORTED)

    suspend fun saveStorage(bot: String, secure: Boolean, values: Map<String, String>): Unit = throw UnsupportedOperationException(UNSUPPORTED)

    suspend fun permission(bot: String, kind: WebAppPermission): Boolean? = null

    suspend fun setPermission(bot: String, kind: WebAppPermission, granted: Boolean) = Unit

    fun biometryType(): String? = null

    suspend fun deviceId(): String = ""

    suspend fun biometryToken(bot: String): String? = null

    suspend fun saveBiometryToken(bot: String, token: String?): Unit = throw UnsupportedOperationException(UNSUPPORTED)

    suspend fun authenticate(title: String, reason: String?): Boolean = false

    fun locationAvailable(): Boolean = false

    suspend fun locationPermission(): Boolean = false

    suspend fun location(): WebAppLocation? = null

    suspend fun cameraPermission(): Boolean = false

    companion object {
        const val UNSUPPORTED = "UNSUPPORTED"

        val None: WebAppDevice = object : WebAppDevice {}
    }
}
