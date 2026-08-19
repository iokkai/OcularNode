package io.github.iokkai.ocularnode.webrtc.pairing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.util.QRCodeUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcPairingFlowTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testGenerateAndParseWebRtcQrCode() {
        val name = "Front Door Camera"
        val ip = "192.168.1.120"
        val ipv6 = "2001:b400:e234:5678::1"
        val port = 8080
        val deviceId = "ocular-cam-001"
        val secret = "a1b2c3d4e5f60718293a4b5c6d7e8f90"

        val qrContent = QRCodeUtils.generateWebRtcQrContent(
            name = name,
            ipAddress = ip,
            port = port,
            deviceId = deviceId,
            deviceSecret = secret,
            ipv6Address = ipv6
        )

        assertNotNull(qrContent)

        // Parse scanned QR
        val scanned = QRCodeUtils.parseScannedQrCode(qrContent)
        assertNotNull(scanned)
        assertEquals(name, scanned!!.name)
        assertEquals(ip, scanned.ipAddress)
        assertEquals(ipv6, scanned.ipv6Address)
        assertEquals(port, scanned.port)
        assertEquals(deviceId, scanned.deviceId)
        assertEquals(secret, scanned.deviceSecret)
    }

    @Test
    fun testLegacyQrFormatFallback() {
        // Legacy format 1: plain JSON {"ip":"192.168.0.50","port":8080,"name":"Backyard"}
        val legacyJson = """{"ip":"192.168.0.50","port":8080,"name":"Backyard"}"""
        val scannedJson = QRCodeUtils.parseScannedQrCode(legacyJson)
        assertNotNull(scannedJson)
        assertEquals("Backyard", scannedJson!!.name)
        assertEquals("192.168.0.50", scannedJson.ipAddress)
        assertEquals(8080, scannedJson.port)
        assertNull(scannedJson.deviceSecret)
        assertNull(scannedJson.ipv6Address)

        // Legacy format 2: URL "http://192.168.0.50:8080?name=Backyard"
        val legacyUrl = "http://192.168.0.50:8080?name=Backyard"
        val scannedUrl = QRCodeUtils.parseScannedQrCode(legacyUrl)
        assertNotNull(scannedUrl)
        assertEquals("Backyard", scannedUrl!!.name)
        assertEquals("192.168.0.50", scannedUrl.ipAddress)
        assertEquals(8080, scannedUrl.port)
    }

    @Test
    fun testSaveScannedCameraToDatabase() = runBlocking {
        val camera = CameraDevice(
            name = "Living Room",
            ipAddress = "192.168.1.100",
            port = 8080,
            deviceSecret = "secret-12345678",
            deviceId = "dev-living-room",
            ipv6Address = "2404:6800:4008:c01::64"
        )

        val id = db.cameraDeviceDao().insertCamera(camera)
        val fetched = db.cameraDeviceDao().getCameraById(id)

        assertNotNull(fetched)
        assertEquals("Living Room", fetched!!.name)
        assertEquals("192.168.1.100", fetched.ipAddress)
        assertEquals("secret-12345678", fetched.deviceSecret)
        assertEquals("dev-living-room", fetched.deviceId)
        assertEquals("2404:6800:4008:c01::64", fetched.ipv6Address)
    }
}
