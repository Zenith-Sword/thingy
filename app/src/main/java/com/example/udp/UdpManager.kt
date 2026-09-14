package com.example.udp

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.LinkedBlockingQueue

/**
 * High-performance, low-latency UDP client for rhythm game touch events.
 * Uses a dedicated high-priority daemon thread with a LinkedBlockingQueue
 * to ensure zero UI thread latency and immediate packet dispatch.
 */
class UdpManager(
    initialHost: String = "192.168.1.100",
    initialPort: Int = 5000
) {
    private val TAG = "UdpManager"

    @Volatile
    var host: String = initialHost
        private set

    @Volatile
    var port: Int = initialPort
        private set

    private var socket: DatagramSocket? = null
    private var resolvedAddress: InetAddress? = null

    // Queue for ultra-fast handoff from UI thread to network thread
    private val packetQueue = LinkedBlockingQueue<ByteArray>(2048)

    @Volatile
    private var isRunning = true

    private val _packetsSentCount = MutableStateFlow(0L)
    val packetsSentCount: StateFlow<Long> = _packetsSentCount.asStateFlow()

    private val _lastSentMessage = MutableStateFlow<String?>(null)
    val lastSentMessage: StateFlow<String?> = _lastSentMessage.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ready ($initialHost:$initialPort)")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val senderThread = Thread {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        initializeSocket()

        while (isRunning) {
            try {
                val data = packetQueue.take()
                var target = resolvedAddress
                if (target == null) {
                    try {
                        target = InetAddress.getByName(host)
                        resolvedAddress = target
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve address: $host", e)
                        _connectionStatus.value = "Resolve Error: ${e.message}"
                        continue
                    }
                }

                val currentSocket = socket
                if (currentSocket != null && !currentSocket.isClosed) {
                    val packet = DatagramPacket(data, data.size, target, port)
                    currentSocket.send(packet)
                    _packetsSentCount.value += 1
                } else {
                    initializeSocket()
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error sending UDP packet", e)
                _connectionStatus.value = "Send Error: ${e.message}"
            }
        }
    }.apply {
        name = "UdpSenderThread"
        isDaemon = true
        start()
    }

    private fun initializeSocket() {
        try {
            socket?.close()
            socket = DatagramSocket().apply {
                sendBufferSize = 64 * 1024
                trafficClass = 0x10 // IPTOS_LOWDELAY for minimal packet latency
            }
            resolvedAddress = InetAddress.getByName(host)
            _connectionStatus.value = "Transmitting to $host:$port"
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error", e)
            _connectionStatus.value = "Init Error: ${e.message}"
        }
    }

    /**
     * Instantly queues a message to be dispatched over UDP.
     * Guaranteed not to block the UI thread.
     */
    fun send(message: String) {
        val payload = message.toByteArray(Charsets.UTF_8)
        val offered = packetQueue.offer(payload)
        if (offered) {
            _lastSentMessage.value = message
        } else {
            Log.w(TAG, "UDP Queue full, dropping packet: $message")
        }
    }

    /**
     * Update target IP destination and port.
     */
    fun updateTarget(newHost: String, newPort: Int) {
        val cleanedHost = newHost.trim()
        val cleanedPort = newPort.coerceIn(1, 65535)

        if (this.host != cleanedHost || this.port != cleanedPort) {
            this.host = cleanedHost
            this.port = cleanedPort
            _connectionStatus.value = "Reconnecting to $cleanedHost:$cleanedPort..."

            // Re-resolve in background thread
            Thread {
                try {
                    resolvedAddress = InetAddress.getByName(cleanedHost)
                    _connectionStatus.value = "Transmitting to $cleanedHost:$cleanedPort"
                } catch (e: Exception) {
                    resolvedAddress = null
                    _connectionStatus.value = "Invalid Address: $cleanedHost"
                }
            }.start()
        }
    }

    fun resetStats() {
        _packetsSentCount.value = 0L
        _lastSentMessage.value = null
    }

    fun close() {
        isRunning = false
        senderThread.interrupt()
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        /**
         * Resolves the local IPv4 address of this Android device for display in settings.
         */
        fun getLocalIpAddress(context: Context): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress
                            if (ip != null && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("UdpManager", "Could not determine local IP", e)
            }
            return "127.0.0.1"
        }
    }
}
