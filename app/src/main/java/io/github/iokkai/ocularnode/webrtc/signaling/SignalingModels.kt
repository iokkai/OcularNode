package io.github.iokkai.ocularnode.webrtc.signaling

import org.json.JSONObject

enum class SignalingType {
    OFFER,
    ANSWER,
    CANDIDATE,
    REQUEST_STREAM,
    BYE,
    PING,
    PONG,
    CONTROL
}

/**
 * Standard unified data model for all WebRTC signaling messages.
 * Includes sessionId (viewerSessionId) to natively isolate and route multiple concurrent viewers.
 */
data class SignalingPayload(
    val type: SignalingType,
    val senderId: String,
    val targetId: String? = null,
    val sessionId: String,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val command: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {

    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type.name)
        json.put("senderId", senderId)
        if (targetId != null) json.put("targetId", targetId)
        json.put("sessionId", sessionId)
        if (sdp != null) json.put("sdp", sdp)
        if (candidate != null) json.put("candidate", candidate)
        if (sdpMid != null) json.put("sdpMid", sdpMid)
        if (sdpMLineIndex != null) json.put("sdpMLineIndex", sdpMLineIndex)
        if (command != null) json.put("command", command)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SignalingPayload {
            val json = JSONObject(jsonStr)
            return SignalingPayload(
                type = SignalingType.valueOf(json.getString("type")),
                senderId = json.getString("senderId"),
                targetId = json.optString("targetId", "").ifBlank { null },
                sessionId = json.getString("sessionId"),
                sdp = json.optString("sdp", "").ifBlank { null },
                candidate = json.optString("candidate", "").ifBlank { null },
                sdpMid = json.optString("sdpMid", "").ifBlank { null },
                sdpMLineIndex = if (json.has("sdpMLineIndex")) json.getInt("sdpMLineIndex") else null,
                command = json.optString("command", "").ifBlank { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }

        fun createOffer(senderId: String, sessionId: String, sdp: String, targetId: String? = null): SignalingPayload {
            return SignalingPayload(
                type = SignalingType.OFFER,
                senderId = senderId,
                targetId = targetId,
                sessionId = sessionId,
                sdp = sdp
            )
        }

        fun createAnswer(senderId: String, sessionId: String, sdp: String, targetId: String? = null): SignalingPayload {
            return SignalingPayload(
                type = SignalingType.ANSWER,
                senderId = senderId,
                targetId = targetId,
                sessionId = sessionId,
                sdp = sdp
            )
        }

        fun createCandidate(
            senderId: String,
            sessionId: String,
            candidate: String,
            sdpMid: String?,
            sdpMLineIndex: Int?,
            targetId: String? = null
        ): SignalingPayload {
            return SignalingPayload(
                type = SignalingType.CANDIDATE,
                senderId = senderId,
                targetId = targetId,
                sessionId = sessionId,
                candidate = candidate,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex
            )
        }

        fun createRequestStream(senderId: String, sessionId: String, targetId: String? = null): SignalingPayload {
            return SignalingPayload(
                type = SignalingType.REQUEST_STREAM,
                senderId = senderId,
                targetId = targetId,
                sessionId = sessionId
            )
        }

        fun createBye(senderId: String, sessionId: String, targetId: String? = null): SignalingPayload {
            return SignalingPayload(
                type = SignalingType.BYE,
                senderId = senderId,
                targetId = targetId,
                sessionId = sessionId
            )
        }
    }
}
