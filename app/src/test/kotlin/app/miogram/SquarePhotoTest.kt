package app.miogram

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.Attachments
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquarePhotoTest {
    private val dir = Files.createTempDirectory("square").toFile()
    private val attachments = Attachments(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun photo(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) for (y in 0 until height) bitmap.setPixel(x, y, if (x < width / 3) Color.RED else if (x < 2 * width / 3) Color.GREEN else Color.BLUE)
        val file = File(dir, "photo${width}x$height-$orientation.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun square(uri: Uri, side: Int = 640): Bitmap {
        val bytes = assertNotNull(attachments.squarePhoto(uri, side))
        return assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    @Test
    fun aWidePhotoLosesItsSidesAndKeepsTheMiddle() {
        val shown = square(photo(120, 60))
        assertEquals(60 to 60, shown.width to shown.height)
        assertEquals(true, Color.green(shown.getPixel(30, 30)) > 200 && Color.red(shown.getPixel(30, 30)) < 60)
        assertEquals(true, Color.red(shown.getPixel(3, 30)) > 200)
        assertEquals(true, Color.blue(shown.getPixel(57, 30)) > 200)
    }

    @Test
    fun aTallPhotoKeepsItsMiddleRows() {
        val shown = square(photo(60, 120))
        assertEquals(60 to 60, shown.width to shown.height)
        assertEquals(true, Color.red(shown.getPixel(5, 30)) > 200)
        assertEquals(true, Color.blue(shown.getPixel(55, 30)) > 200)
    }

    @Test
    fun aSidewaysPhotoIsTurnedBeforeItIsCut() {
        val shown = square(photo(120, 60, ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(60 to 60, shown.width to shown.height)
        assertEquals(true, Color.green(shown.getPixel(30, 30)) > 200)
    }

    @Test
    fun aLargePhotoIsScaledToTheLimit() {
        val shown = square(photo(3_000, 2_000), side = 640)
        assertEquals(true, shown.width <= 640 && shown.width == shown.height)
    }

    @Test
    fun anUnreadableFileGivesNothing() {
        assertNull(attachments.squarePhoto(Uri.fromFile(File(dir, "missing.jpg"))))
    }
}
