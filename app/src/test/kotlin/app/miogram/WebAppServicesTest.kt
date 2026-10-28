package app.miogram

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.DeviceLocation
import app.miogram.data.WebAppServices
import app.miogram.data.WebAppStore
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebAppServicesTest {
    @Test
    fun theStoreTheClipboardAndTheLocation(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val context = ApplicationProvider.getApplicationContext<Application>()
            val store = WebAppStore(repos.telegram)
            val services = WebAppServices(context, repos.bots, repos.appConfig, store, FakeDeviceAuth(), DeviceLocation(context))

            store.saveStorage("u504", false, mapOf("level" to "7"))
            assertEquals(mapOf("level" to "7"), store.storage("u504", false))
            assertEquals(emptyMap(), store.storage("u506", false))
            assertNull(store.permission("u504", "Location"))
            store.setPermission("u504", "Location", true)
            assertEquals(true, store.permission("u504", "Location"))
            assertNull(store.permission("u506", "Location"))
            val device = store.deviceId()
            assertTrue(device.isNotEmpty())
            assertEquals(device, store.deviceId())

            assertFailsWith<UnsupportedOperationException> { store.saveStorage("u504", true, mapOf("pin" to "1234")) }
            assertFailsWith<UnsupportedOperationException> { store.saveBiometryToken("u504", "secret") }
            val file = File(dir, "${WebAppStore.DIR}/u504.json").readText()
            assertFalse("1234" in file || "secret" in file)
            store.saveStorage("u504", true, emptyMap())
            store.saveBiometryToken("u504", null)
            assertEquals(emptyMap(), store.storage("u504", true))
            assertNull(store.biometryToken("u504"))

            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("code", "A1B2"))
            assertEquals("A1B2", services.clipboardText())

            val locations = context.getSystemService(LocationManager::class.java)
            shadowOf(locations).setLocationEnabled(true)
            assertTrue(services.locationAvailable())
            assertNull(services.location())
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            shadowOf(locations).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)
            shadowOf(locations).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            val fix = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 55.75
                longitude = 37.62
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            shadowOf(locations).simulateLocation(fix)
            val asked = CoroutineScope(Dispatchers.Default).async { services.location() }
            while (!asked.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            val found = assertNotNull(asked.await())
            assertEquals(55.75 to 37.62, found.latitude to found.longitude)
            assertEquals(5f, found.accuracy)

            repos.auth.forgetAccount()
            assertEquals(emptyMap(), store.storage("u504", false))
            assertNull(store.permission("u504", "Location"))
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
