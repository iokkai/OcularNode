package io.github.iokkai.ocularnode.webrtc.signaling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignalingChannelTest {

    private class MockSignalingChannel(
        override val channelType: SignalingChannelType = SignalingChannelType.MQTT
    ) : SignalingChannel {
        val sentMessages = mutableListOf<SignalingPayload>()

        override suspend fun startListening(
            channelKey: String,
            secret: String,
            onMessage: (SignalingPayload) -> Unit
        ) {}

        override suspend fun sendMessage(
            channelKey: String,
            secret: String,
            message: SignalingPayload
        ): Boolean {
            sentMessages.add(message)
            return true
        }

        override fun close() {
            sentMessages.clear()
        }
    }

    @Test
    fun testSignalingChannelHelperMethods() = runBlocking {
        val channel = MockSignalingChannel()
        val channelKey = "test-device-123"
        val secret = "test-secret-456"

        val offer = SignalingPayload.createOffer("cam", "sess-1", "v=0...")
        val answer = SignalingPayload.createAnswer("viewer", "sess-1", "v=0...")
        val candidate = SignalingPayload.createCandidate("cam", "sess-1", "candidate:...", "video", 0)

        assertTrue(channel.sendOffer(channelKey, secret, offer))
        assertTrue(channel.sendAnswer(channelKey, secret, answer))
        assertTrue(channel.sendIceCandidate(channelKey, secret, candidate))

        assertEquals(3, channel.sentMessages.size)
        assertEquals(SignalingType.OFFER, channel.sentMessages[0].type)
        assertEquals(SignalingType.ANSWER, channel.sentMessages[1].type)
        assertEquals(SignalingType.CANDIDATE, channel.sentMessages[2].type)
    }
}
