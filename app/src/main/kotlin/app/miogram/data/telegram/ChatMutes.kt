package app.miogram.data.telegram

import android.content.Context
import cn.ellerotta.core.database.DialogRow
import cn.ellerotta.core.model.NotifyScope
import java.util.concurrent.ConcurrentHashMap

class ChatMutes(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val known by lazy { ConcurrentHashMap<String, Int>().also { map -> prefs.all.forEach { (key, value) -> (value as? Int)?.let { map[key] = it } } } }

    fun get(key: String): Int? = known[key]

    fun set(key: String, muteUntil: Int) {
        val now = System.currentTimeMillis() / 1000
        val value = when {
            muteUntil == DialogRow.FOLLOWS_DEFAULT -> null
            muteUntil <= now -> 0
            else -> muteUntil
        }
        if (known[key] == value) return
        if (value == null) known.remove(key) else known[key] = value
        prefs.edit().apply { if (value == null) remove(key) else putInt(key, value) }.apply()
    }

    fun default(scope: NotifyScope): Int? = known[defaultKey(scope)]

    fun setDefault(scope: NotifyScope, muteUntil: Int) = set(defaultKey(scope), muteUntil)

    fun clear() {
        known.clear()
        prefs.edit().clear().apply()
    }

    private fun defaultKey(scope: NotifyScope) = "@${scope.name}"

    private companion object {
        const val PREFS = "chat_mutes"
    }
}
