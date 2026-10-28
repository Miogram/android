package cn.ellerotta.core.ui

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xhdpi")
class LocationIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun locationSymbolsResolve() {
        listOf(
            R.drawable.ic_share_location,
            R.drawable.ic_share_location_filled,
            R.drawable.ic_pin_drop,
            R.drawable.ic_location_on,
            R.drawable.ic_my_location,
            R.drawable.ic_map,
            R.drawable.ic_directions,
            R.drawable.ic_notifications,
            R.drawable.ic_notifications_active_filled,
            R.drawable.ic_distance,
            R.drawable.ic_person_pin_circle_filled,
        ).forEach { id ->
            val drawable = ContextCompat.getDrawable(context, id)
            assertTrue(drawable is VectorDrawable, context.resources.getResourceEntryName(id))
            assertEquals(48, drawable.intrinsicWidth)
            assertEquals(48, drawable.intrinsicHeight)
        }
    }

    @Test
    fun theArrowIsGone() {
        listOf("ic_near_me", "ic_notifications_active").forEach { name ->
            assertEquals(0, context.resources.getIdentifier(name, "drawable", context.packageName), name)
        }
    }
}
