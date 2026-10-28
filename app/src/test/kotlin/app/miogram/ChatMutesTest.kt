package app.miogram

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ChatMutes
import cn.ellerotta.core.database.DialogRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatMutesTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val later = (System.currentTimeMillis() / 1000).toInt() + 3_600

    @Test
    fun explicitSettingsAreKeptAndTheRestForgotten() {
        val mutes = ChatMutes(context)
        mutes.set("u1", later)
        mutes.set("c2", 0)
        mutes.set("ch3", Int.MAX_VALUE)
        mutes.set("u4", 5)
        mutes.set("u5", DialogRow.FOLLOWS_DEFAULT)
        assertEquals(later, mutes.get("u1"))
        assertEquals(0, mutes.get("c2"))
        assertEquals(Int.MAX_VALUE, mutes.get("ch3"))
        assertEquals(0, mutes.get("u4"))
        assertNull(mutes.get("u5"))

        val next = ChatMutes(context)
        assertEquals(later, next.get("u1"))
        assertEquals(Int.MAX_VALUE, next.get("ch3"))
        next.set("u1", DialogRow.FOLLOWS_DEFAULT)
        assertNull(ChatMutes(context).get("u1"), "a chat that follows the default again is forgotten")

        next.clear()
        assertNull(ChatMutes(context).get("ch3"))
    }
}
