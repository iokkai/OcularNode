package io.github.iokkai.ocularnode.webrtc.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignalingModelsTest {

    @Test
    fun testOfferSerializationAndDeserialization() {
        val payload = SignalingPayload.createOffer(
            senderId = "cam-01",
            sessionId = "session-viewer-123",
            sdp = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1...",
            targetId = "viewer-01"
        )

        val json = payload.toJson()
        assertNotNull(json)

        val parsed = SignalingPayload.fromJson(json)
        assertEquals(SignalingType.OFFER, parsed.type)
        assertEquals("cam-01", parsed.senderId)
        assertEquals("viewer-01", parsed.targetId)
        assertEquals("session-viewer-123", parsed.sessionId)
        assertEquals("v=0\r\no=- 12345 2 IN IP4 127.0.0.1...", parsed.sdp)
    }

    @Test
    fun testCandidateSerializationAndDeserialization() {
        val payload = SignalingPayload.createCandidate(
            senderId = "viewer-01",
            sessionId = "session-viewer-123",
            candidate = "candidate:1234 1 udp 1677729535 192.168.1.50 54321 typ host",
            sdpMid = "video",
            sdpMLineIndex = 0
        )

        val json = payload.toJson()
        val parsed = SignalingPayload.fromJson(json)

        assertEquals(SignalingType.CANDIDATE, parsed.type)
        assertEquals("session-viewer-123", parsed.sessionId)
        assertEquals("video", parsed.sdpMid)
        assertEquals(0, parsed.sdpMLineIndex)
        assertEquals("candidate:1234 1 udp 1677729535 192.168.1.50 54321 typ host", parsed.candidate)
    }
}
