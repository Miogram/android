package app.miogram.push

import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatOnScreen @Inject constructor(private val navigator: Navigator, private val appLock: AppLock) {
    @Volatile private var started = false

    fun started(started: Boolean) {
        this.started = started
    }

    fun shows(dialogKey: String): Boolean {
        if (!started || appLock.locked.value) return false
        val route = navigator.current as? Route.Chat ?: return false
        return route.dialogKey == dialogKey && route.topicId == null
    }
}
