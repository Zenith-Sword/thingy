package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.udp.UdpManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TileConfig(
    val id: Int,
    val label: String,
    val primaryColor: Color,
    val glowColor: Color,
    val darkBaseColor: Color
)

data class TileState(
    val id: Int,
    val config: TileConfig,
    val isPressed: Boolean = false,
    val activeFingersCount: Int = 0
)

data class ControllerUiState(
    val targetIp: String = "192.168.1.100",
    val targetPort: Int = 5000,
    val localIp: String = "127.0.0.1",
    val isSettingsOpen: Boolean = false,
    val isHudVisible: Boolean = true,
    val isSlideEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val packetsSent: Long = 0L,
    val lastMessage: String? = null,
    val connectionStatus: String = "Ready"
)

class RhythmControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val udpManager = UdpManager(
        initialHost = "192.168.1.100",
        initialPort = 5000
    )

    val tileConfigs = listOf(
        TileConfig(
            id = 1,
            label = "LANE 1",
            primaryColor = Color(0xFF00E5FF),
            glowColor = Color(0xFF84FFFF),
            darkBaseColor = Color(0xFF07212F)
        ),
        TileConfig(
            id = 2,
            label = "LANE 2",
            primaryColor = Color(0xFFFF2A85),
            glowColor = Color(0xFFFF80AB),
            darkBaseColor = Color(0xFF2C0719)
        ),
        TileConfig(
            id = 3,
            label = "LANE 3",
            primaryColor = Color(0xFF00E676),
            glowColor = Color(0xFFB9F6CA),
            darkBaseColor = Color(0xFF082B17)
        ),
        TileConfig(
            id = 4,
            label = "LANE 4",
            primaryColor = Color(0xFFFFD600),
            glowColor = Color(0xFFFFE57F),
            darkBaseColor = Color(0xFF2E2405)
        )
    )

    private val _tilesState = MutableStateFlow(
        tileConfigs.associate { it.id to TileState(id = it.id, config = it) }
    )
    val tilesState: StateFlow<Map<Int, TileState>> = _tilesState.asStateFlow()

    private val _controllerUiState = MutableStateFlow(
        ControllerUiState(
            targetIp = udpManager.host,
            targetPort = udpManager.port,
            localIp = UdpManager.getLocalIpAddress(application)
        )
    )

    val uiState: StateFlow<ControllerUiState> = combine(
        _controllerUiState,
        udpManager.packetsSentCount,
        udpManager.lastSentMessage,
        udpManager.connectionStatus
    ) { baseState, packets, lastMsg, status ->
        baseState.copy(
            packetsSent = packets,
            lastMessage = lastMsg,
            connectionStatus = status
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        _controllerUiState.value
    )

    // Map to keep track of which pointer is pressing which tile
    private val activePointerMap = HashMap<Long, Int>()

    /**
     * Called immediately when a finger contacts a tile.
     * Dispatches UDP packet "D,[tileId]" and updates visual state.
     */
    fun onTileDown(tileId: Int, pointerId: Long? = null) {
        if (pointerId != null) {
            activePointerMap[pointerId] = tileId
        }

        // Send UDP packet: "D,[tile_id]"
        udpManager.send("D,$tileId")

        // Trigger light haptic feedback if enabled
        if (_controllerUiState.value.isHapticsEnabled) {
            performHapticTick()
        }

        // Update tile visual state
        _tilesState.value = _tilesState.value.toMutableMap().apply {
            val current = get(tileId) ?: return@apply
            val newCount = current.activeFingersCount + 1
            put(tileId, current.copy(isPressed = true, activeFingersCount = newCount))
        }
    }

    /**
     * Called immediately when a finger releases or leaves a tile.
     * Dispatches UDP packet "U,[tileId]" and restores visual opacity.
     */
    fun onTileUp(tileId: Int, pointerId: Long? = null) {
        if (pointerId != null) {
            activePointerMap.remove(pointerId)
        }

        // Send UDP packet: "U,[tile_id]"
        udpManager.send("U,$tileId")

        // Update tile visual state
        _tilesState.value = _tilesState.value.toMutableMap().apply {
            val current = get(tileId) ?: return@apply
            val newCount = (current.activeFingersCount - 1).coerceAtLeast(0)
            put(tileId, current.copy(isPressed = newCount > 0, activeFingersCount = newCount))
        }
    }

    /**
     * Called when a pointer slides from one tile to another.
     */
    fun onPointerMoved(pointerId: Long, newTileId: Int) {
        val oldTileId = activePointerMap[pointerId]
        if (oldTileId != null && oldTileId != newTileId) {
            onTileUp(oldTileId, pointerId)
            onTileDown(newTileId, pointerId)
        }
    }

    /**
     * Resets all active pointers and releases any held tiles (e.g., on app pause or cancel).
     */
    fun onCancelAllTouches() {
        val currentlyPressedTiles = _tilesState.value.values.filter { it.isPressed }
        activePointerMap.clear()
        currentlyPressedTiles.forEach { tile ->
            udpManager.send("U,${tile.id}")
        }
        _tilesState.value = tileConfigs.associate {
            it.id to TileState(id = it.id, config = it, isPressed = false, activeFingersCount = 0)
        }
    }

    fun openSettings() {
        _controllerUiState.value = _controllerUiState.value.copy(
            isSettingsOpen = true,
            localIp = UdpManager.getLocalIpAddress(getApplication())
        )
    }

    fun closeSettings() {
        _controllerUiState.value = _controllerUiState.value.copy(isSettingsOpen = false)
    }

    fun updateTarget(host: String, port: Int) {
        udpManager.updateTarget(host, port)
        _controllerUiState.value = _controllerUiState.value.copy(
            targetIp = host,
            targetPort = port
        )
    }

    fun toggleHud() {
        _controllerUiState.value = _controllerUiState.value.copy(
            isHudVisible = !_controllerUiState.value.isHudVisible
        )
    }

    fun toggleSlide(enabled: Boolean) {
        _controllerUiState.value = _controllerUiState.value.copy(isSlideEnabled = enabled)
    }

    fun toggleHaptics(enabled: Boolean) {
        _controllerUiState.value = _controllerUiState.value.copy(isHapticsEnabled = enabled)
    }

    fun sendTestPacket(tileId: Int = 1) {
        udpManager.send("D,$tileId")
        udpManager.send("U,$tileId")
    }

    fun resetStats() {
        udpManager.resetStats()
    }

    private fun performHapticTick() {
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(8)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore haptic failures
        }
    }

    override fun onCleared() {
        super.onCleared()
        onCancelAllTouches()
        udpManager.close()
    }
}
