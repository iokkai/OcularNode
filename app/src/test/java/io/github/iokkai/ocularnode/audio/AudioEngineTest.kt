package io.github.iokkai.ocularnode.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試 Push-to-Talk 雙向語音半雙工防嘯叫邏輯與 PCM 緩衝運算 (Push-To-Talk Half-Duplex Acoustic Protection & PCM Buffer Math)。
 */
class AudioEngineTest {

    class HalfDuplexAudioController {
        var isSpeakingToNode = false
        var isListeningFromNode = false
        var micMutedDueToSpeaker = false

        fun startSpeaking() {
            isSpeakingToNode = true
            // 防嘯叫：當處於發話模式時，強制靜音/暫停接收端麥克風
            if (isListeningFromNode) {
                micMutedDueToSpeaker = true
            }
        }

        fun stopSpeaking() {
            isSpeakingToNode = false
            micMutedDueToSpeaker = false
        }

        fun startListening() {
            isListeningFromNode = true
            if (isSpeakingToNode) {
                micMutedDueToSpeaker = true
            }
        }

        fun stopListening() {
            isListeningFromNode = false
            micMutedDueToSpeaker = false
        }
    }

    @Test
    fun `push to talk speaking mutes listening to prevent acoustic feedback loop`() {
        val controller = HalfDuplexAudioController()

        // User starts listening to ambient sound
        controller.startListening()
        assertTrue(controller.isListeningFromNode)
        assertFalse(controller.micMutedDueToSpeaker)

        // User presses and holds Push-to-Talk button to speak
        controller.startSpeaking()
        assertTrue(controller.isSpeakingToNode)
        assertTrue("Listening must be muted while speaking to eliminate howling", controller.micMutedDueToSpeaker)

        // User releases Push-to-Talk button
        controller.stopSpeaking()
        assertFalse(controller.isSpeakingToNode)
        assertFalse("Listening un-mutes cleanly after speaking ends", controller.micMutedDueToSpeaker)
    }

    @Test
    fun `calculates correct pcm byte rate for 16kHz 16bit mono`() {
        val sampleRate = 16000 // 16kHz
        val bytesPerSample = 2 // 16-bit = 2 bytes
        val channels = 1       // Mono = 1

        val bytesPerSecond = sampleRate * bytesPerSample * channels
        assertEquals(32000, bytesPerSecond) // 32 KB/s

        // 100ms packet size
        val packet100msBytes = (bytesPerSecond * 0.1).toInt()
        assertEquals(3200, packet100msBytes)
    }
}
