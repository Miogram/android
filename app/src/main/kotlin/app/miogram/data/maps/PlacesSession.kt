package app.miogram.data.maps

import java.util.UUID

class PlacesSession(private val newToken: () -> String = { UUID.randomUUID().toString() }) {
    private var current: String? = null

    @Synchronized
    fun token(): String = current ?: newToken().also { current = it }

    @Synchronized
    fun end() {
        current = null
    }
}
