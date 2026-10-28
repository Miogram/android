package app.miogram

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import app.miogram.data.cropSquare
import cn.ellerotta.core.model.CropRect
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCropTest {
    private val photo: ByteArray = ByteArrayOutputStream().also { out ->
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until 400) for (y in 0 until 200) bitmap.setPixel(x, y, if (x < 200) Color.RED else Color.BLUE)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }.toByteArray()

    private fun decode(bytes: ByteArray?): Bitmap = assertNotNull(BitmapFactory.decodeByteArray(assertNotNull(bytes), 0, bytes.size))

    @Test
    fun theChosenSquareIsCutOutAndSquare() {
        val left = decode(cropSquare(photo, CropRect(left = 0, top = 0, side = 200)))
        assertEquals(200 to 200, left.width to left.height)
        assertTrue(Color.red(left.getPixel(100, 100)) > 200 && Color.blue(left.getPixel(100, 100)) < 60)
        val right = decode(cropSquare(photo, CropRect(left = 200, top = 0, side = 200)))
        assertTrue(Color.blue(right.getPixel(100, 100)) > 200 && Color.red(right.getPixel(100, 100)) < 60)
        val middle = decode(cropSquare(photo, CropRect(left = 150, top = 50, side = 100)))
        assertEquals(100 to 100, middle.width to middle.height)
        assertTrue(Color.red(middle.getPixel(10, 50)) > 200)
        assertTrue(Color.blue(middle.getPixel(90, 50)) > 200)
    }

    @Test
    fun aWindowPastTheEdgeIsPulledInsideAndNoPictureIsNull() {
        val pulled = decode(cropSquare(photo, CropRect(left = 350, top = 0, side = 200)))
        assertEquals(200 to 200, pulled.width to pulled.height)
        assertTrue(Color.blue(pulled.getPixel(100, 100)) > 200)
        assertNull(cropSquare(byteArrayOf(1, 2, 3), CropRect(0, 0, 10)))
    }
}
