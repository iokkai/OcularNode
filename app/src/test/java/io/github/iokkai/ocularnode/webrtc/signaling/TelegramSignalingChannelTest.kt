package io.github.iokkai.ocularnode.webrtc.signaling

import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelegramSignalingChannelTest {

    @Test
    fun testParseIncomingTelegramSignal() {
        val channel = TelegramSignalingChannel("test-token", "123456")
        val secret = "my-secret-key-123"

        val originalPayload = SignalingPayload.createOffer(
            senderId = "viewer-99",
            sessionId = "sess-tg-01",
            sdp = "v=0\r\no=... (Telegram SDP Offer)"
        )

        val encrypted = AesGcmCipher.encrypt(originalPayload.toJson(), secret)
        val telegramMessageText = TelegramSignalingChannel.TELEGRAM_SIGNAL_PREFIX + encrypted

        val parsed = channel.parseIncomingTelegramText(telegramMessageText, secret)
        assertNotNull(parsed)
        assertEquals(SignalingType.OFFER, parsed?.type)
        assertEquals("viewer-99", parsed?.senderId)
        assertEquals("sess-tg-01", parsed?.sessionId)
        assertEquals("v=0\r\no=... (Telegram SDP Offer)", parsed?.sdp)
    }

    @Test
    fun testIgnoreNonSignalTelegramMessage() {
        val channel = TelegramSignalingChannel("test-token", "123456")
        val secret = "my-secret-key-123"

        val regularText = "Hello, this is a normal chat message"
        val parsed = channel.parseIncomingTelegramText(regularText, secret)
        assertNull(parsed)
    }
}
