package app.miogram.data.telegram

import java.util.concurrent.ConcurrentHashMap

class ScreenMemory {
    private val values = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: String): T? = values[key] as? T

    operator fun set(key: String, value: Any) {
        values[key] = value
    }

    fun clear() = values.clear()
}
