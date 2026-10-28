package app.miogram

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.Attachments
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentsTest {
    private val dir = Files.createTempDirectory("attachments").toFile()
    private val attachments = Attachments(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun photo(orientation: Int): Uri {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) for (y in 0 until 20) bitmap.setPixel(x, y, if (x < 20) Color.RED else Color.BLUE)
        val file = File(dir, "photo$orientation.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun preview(orientation: Int): Bitmap {
        val bytes = assertNotNull(attachments.preview(photo(orientation), video = false))
        return assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun isRed(color: Int) = Color.red(color) > 200 && Color.blue(color) < 60

    @Test
    fun anUprightPhotoStaysAsItIs() {
        val shown = preview(ExifInterface.ORIENTATION_NORMAL)
        assertEquals(40 to 20, shown.width to shown.height)
        assertEquals(true, isRed(shown.getPixel(5, 10)))
    }

    @Test
    fun aPhotoTakenSidewaysIsTurnedUpright() {
        val turned = preview(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(20 to 40, turned.width to turned.height)
        assertEquals(true, isRed(turned.getPixel(10, 5)))
        assertEquals(false, isRed(turned.getPixel(10, 35)))
        val back = preview(ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(20 to 40, back.width to back.height)
        assertEquals(true, isRed(back.getPixel(10, 35)))
        assertEquals(true, isRed(preview(ExifInterface.ORIENTATION_ROTATE_180).getPixel(35, 10)))
        assertEquals(true, isRed(preview(ExifInterface.ORIENTATION_FLIP_HORIZONTAL).getPixel(35, 10)))
    }

    private fun jpeg(name: String, width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL, gps: Boolean = false): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        Canvas(bitmap).drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            if (gps) {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "52/1,31/1,12/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "13/1,24/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            }
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun decode(bytes: ByteArray) = assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    private fun upload(uri: Uri, asFile: Boolean = false): ByteArray = runBlocking {
        val file = attachments.openUpload(uri, asFile)
        file.source.read(0, file.source.size.toInt()).also { file.close() }
    }

    @Test
    fun aLargePhotoIsScaledToTheServersLargestSize() {
        val sent = decode(upload(jpeg("big.jpg", 6000, 4000)))
        assertEquals(2560 to 1707, sent.width to sent.height)
    }

    @Test
    fun aSmallJpegIsSentUnchanged() {
        val uri = jpeg("small.jpg", 1000, 800)
        assertContentEquals(File(uri.path!!).readBytes(), upload(uri))
    }

    @Test
    fun aSmallPhotoIsNeverEnlarged() {
        val file = File(dir, "small.png")
        file.outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sent = decode(upload(Uri.fromFile(file)))
        assertEquals(300 to 200, sent.width to sent.height)
    }

    @Test
    fun aTurnedPhotoIsSentUpright() {
        val sent = decode(upload(jpeg("turned.jpg", 400, 200, ExifInterface.ORIENTATION_ROTATE_90)))
        assertEquals(200 to 400, sent.width to sent.height)
        assertEquals(true, isRed(sent.getPixel(100, 20)))
        assertEquals(false, isRed(sent.getPixel(100, 380)))
    }

    @Test
    fun theLocationIsDropped() {
        val uri = jpeg("located.jpg", 400, 200, ExifInterface.ORIENTATION_ROTATE_90, gps = true)
        assertNotNull(ExifInterface(File(uri.path!!).path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        val sent = upload(uri)
        assertNull(ExifInterface(ByteArrayInputStream(sent)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test
    fun transparencyBecomesWhite() {
        val file = File(dir, "clear.png")
        file.outputStream().use { Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sent = decode(upload(Uri.fromFile(file)))
        assertEquals(true, Color.red(sent.getPixel(10, 10)) > 240 && Color.blue(sent.getPixel(10, 10)) > 240)
    }

    @Test
    fun aFileIsSentWithItsOwnBytes() {
        val uri = jpeg("file.jpg", 6000, 4000, ExifInterface.ORIENTATION_ROTATE_90, gps = true)
        assertContentEquals(File(uri.path!!).readBytes(), upload(uri, asFile = true))
    }
}
