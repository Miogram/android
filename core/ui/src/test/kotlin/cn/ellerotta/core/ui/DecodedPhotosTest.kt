package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DecodedPhotosTest {
    @get:Rule
    val compose = createComposeRule()

    private val photo: ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 200)) }.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }.toByteArray()

    @Test
    fun aPhotoIsDecodedOnce() {
        DecodedPhotos.clear()
        val before = DecodedPhotos.decodeCount
        var screen by mutableIntStateOf(0)
        compose.setContent {
            if (screen == 0) {
                Column {
                    Avatar("Ada", photo, size = 56)
                    Avatar("Ada", photo, size = 48)
                }
            } else {
                Avatar("Ada", photo, size = 105)
            }
        }
        compose.waitForIdle()
        screen = 1
        compose.waitForIdle()
        assertEquals(1, DecodedPhotos.decodeCount - before)
        assertSame(DecodedPhotos.get(photo), assertNotNull(DecodedPhotos.get(photo)))
        assertEquals(1, DecodedPhotos.decodeCount - before)

        DecodedPhotos.clear()
        DecodedPhotos.get(photo)
        assertEquals(2, DecodedPhotos.decodeCount - before)
    }
}
