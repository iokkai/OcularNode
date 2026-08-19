package io.github.iokkai.ocularnode.webrtc.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalSocketSignalingChannelTest {

    @Test
    fun testChannelTypeIsLocalSocket() {
        val channel = LocalSocketSignalingChannel("192.168.1.100", 8080)
        assertEquals(SignalingChannelType.LOCAL_SOCKET, channel.channelType)
        channel.close()
    }

    @Test
    fun testIpv6ChannelInitialization() {
        val channel = LocalSocketSignalingChannel("2001:b400:e123::1", 8080)
        assertNotNull(channel)
        assertEquals(SignalingChannelType.LOCAL_SOCKET, channel.channelType)
        channel.close()
    }
}
