package com.example

import com.example.udp.UdpManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testUdpManagerInitialization() {
    val udpManager = UdpManager(initialHost = "192.168.1.100", initialPort = 5000)
    assertEquals("192.168.1.100", udpManager.host)
    assertEquals(5000, udpManager.port)

    // Test sending down and up packets
    udpManager.send("D,1")
    assertEquals("D,1", udpManager.lastSentMessage.value)

    udpManager.send("U,1")
    assertEquals("U,1", udpManager.lastSentMessage.value)

    udpManager.send("D,4")
    assertEquals("D,4", udpManager.lastSentMessage.value)

    udpManager.send("U,4")
    assertEquals("U,4", udpManager.lastSentMessage.value)

    udpManager.close()
  }

  @Test
  fun testTargetUpdate() {
    val udpManager = UdpManager(initialHost = "127.0.0.1", initialPort = 5000)
    udpManager.updateTarget("192.168.1.200", 5000)
    assertEquals("192.168.1.200", udpManager.host)
    assertEquals(5000, udpManager.port)
    udpManager.close()
  }
}
