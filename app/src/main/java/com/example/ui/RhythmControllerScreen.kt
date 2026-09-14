package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RhythmControllerScreen(
    viewModel: RhythmControllerViewModel,
    modifier: Modifier = Modifier
) {
    val tilesState by viewModel.tilesState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var hudBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B13))
            .pointerInput(uiState.isSlideEnabled, uiState.isSettingsOpen) {
                if (uiState.isSettingsOpen) return@pointerInput

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val screenWidth = size.width.toFloat()
                        if (screenWidth <= 0f) continue
                        val tileWidth = screenWidth / 4f

                        for (change in event.changes) {
                            val pointerId = change.id.value
                            val pos = change.position
                            val isDown = change.pressed
                            val wasDown = change.previousPressed

                            // If touch starts within the top HUD control area, allow HUD interaction
                            val isInsideHud = hudBoundsInWindow != Rect.Zero &&
                                    pos.x >= hudBoundsInWindow.left &&
                                    pos.x <= hudBoundsInWindow.right &&
                                    pos.y >= hudBoundsInWindow.top &&
                                    pos.y <= hudBoundsInWindow.bottom

                            if (!wasDown && isDown) {
                                if (isInsideHud) {
                                    // Skip tile trigger for HUD button taps
                                    continue
                                }
                                val tileIdx = (pos.x / tileWidth).toInt().coerceIn(0, 3)
                                val tileId = tileIdx + 1
                                viewModel.onTileDown(tileId, pointerId)
                                change.consume()
                            } else if (wasDown && !isDown) {
                                val tileIdx = (pos.x / tileWidth).toInt().coerceIn(0, 3)
                                val tileId = tileIdx + 1
                                viewModel.onTileUp(tileId, pointerId)
                                change.consume()
                            } else if (wasDown && isDown) {
                                if (uiState.isSlideEnabled) {
                                    val currentTileIdx = (pos.x / tileWidth).toInt().coerceIn(0, 3)
                                    val currentTileId = currentTileIdx + 1
                                    viewModel.onPointerMoved(pointerId, currentTileId)
                                }
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // 4 Equal Vertical Tiles filling the entire screen horizontally
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            viewModel.tileConfigs.forEach { config ->
                val state = tilesState[config.id] ?: TileState(config.id, config)
                RhythmTile(
                    tileState = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tile_${config.id}")
                )
            }
        }

        // Top HUD Overlay: Target IP/Port info, telemetry, and settings toggle
        AnimatedVisibility(
            visible = uiState.isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .onGloballyPositioned { coordinates ->
                    hudBoundsInWindow = coordinates.boundsInWindow()
                }
        ) {
            HudPill(
                targetIp = uiState.targetIp,
                targetPort = uiState.targetPort,
                packetsSent = uiState.packetsSent,
                lastMessage = uiState.lastMessage,
                onOpenSettings = { viewModel.openSettings() },
                onToggleHud = { viewModel.toggleHud() }
            )
        }

        // Minimized HUD button when HUD is hidden (for full immersion)
        AnimatedVisibility(
            visible = !uiState.isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .onGloballyPositioned { coordinates ->
                    hudBoundsInWindow = coordinates.boundsInWindow()
                }
        ) {
            IconButton(
                onClick = { viewModel.toggleHud() },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x88111827))
                    .border(1.dp, Color(0x44FFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Show controls overlay",
                    tint = Color(0xCCFFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Settings Dialog Modal
        if (uiState.isSettingsOpen) {
            UdpSettingsDialog(
                currentIp = uiState.targetIp,
                currentPort = uiState.targetPort,
                localIp = uiState.localIp,
                packetsSent = uiState.packetsSent,
                lastMessage = uiState.lastMessage,
                connectionStatus = uiState.connectionStatus,
                isSlideEnabled = uiState.isSlideEnabled,
                isHapticsEnabled = uiState.isHapticsEnabled,
                onSave = { ip, port ->
                    viewModel.updateTarget(ip, port)
                    viewModel.closeSettings()
                },
                onSendTest = { viewModel.sendTestPacket() },
                onResetStats = { viewModel.resetStats() },
                onToggleSlide = { viewModel.toggleSlide(it) },
                onToggleHaptics = { viewModel.toggleHaptics(it) },
                onDismiss = { viewModel.closeSettings() }
            )
        }
    }
}

/**
 * Single Rhythm Tile Composable.
 * On press, opacity is reduced for visual feedback (alpha: 0.35f vs 1.0f).
 * On release, opacity is restored to 1.0f.
 */
@Composable
fun RhythmTile(
    tileState: TileState,
    modifier: Modifier = Modifier
) {
    val config = tileState.config
    val isPressed = tileState.isPressed

    // Requirement 5 & 6: Reduce tile opacity on press, restore on release.
    val tileOpacity = if (isPressed) 0.38f else 1.0f

    val baseGradient = remember(config, isPressed) {
        Brush.verticalGradient(
            colors = listOf(
                config.primaryColor.copy(alpha = if (isPressed) 0.20f else 0.55f),
                config.darkBaseColor,
                config.primaryColor.copy(alpha = if (isPressed) 0.35f else 0.85f)
            )
        )
    }

    Box(
        modifier = modifier
            .alpha(tileOpacity)
            .background(baseGradient)
            .border(
                width = if (isPressed) 2.dp else 1.dp,
                color = if (isPressed) config.glowColor else config.primaryColor.copy(alpha = 0.35f)
            )
    ) {
        // Subtle cyber grid / decorative rhythm lane tick marks
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lane Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = config.label,
                    color = config.glowColor.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(config.primaryColor.copy(alpha = 0.6f))
                )
            }

            // Big Bold Tile ID (1, 2, 3, 4)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${config.id}",
                    color = if (isPressed) Color.White else config.primaryColor,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isPressed) "PRESSED (D,${config.id})" else "READY (U,${config.id})",
                    color = if (isPressed) config.glowColor else Color(0x66FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // Bottom Strike / Judgement Target Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(if (isPressed) 12.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isPressed) config.glowColor else config.primaryColor.copy(alpha = 0.6f)
                    )
            )
        }

        // Instant illuminated hit flash beam when pressed
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                config.glowColor.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Top floating HUD showing target UDP destination, packet stats, and settings trigger.
 */
@Composable
fun HudPill(
    targetIp: String,
    targetPort: Int,
    packetsSent: Long,
    lastMessage: String?,
    onOpenSettings: () -> Unit,
    onToggleHud: () -> Unit
) {
    Surface(
        color = Color(0xDD0D1117),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // UDP Connection Indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )

            // Target destination
            Text(
                text = "UDP: $targetIp:$targetPort",
                color = Color(0xEEFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )

            // Packets Sent Counter
            Text(
                text = "PKTS: $packetsSent",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            if (lastMessage != null) {
                Text(
                    text = "[$lastMessage]",
                    color = Color(0xFFFFD600),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open UDP Settings",
                    tint = Color(0xCCFFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Hide HUD for distraction-free gaming
            IconButton(
                onClick = onToggleHud,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hide HUD",
                    tint = Color(0x88FFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Configuration dialog for UDP destination target, local IP, and controls.
 */
@Composable
fun UdpSettingsDialog(
    currentIp: String,
    currentPort: Int,
    localIp: String,
    packetsSent: Long,
    lastMessage: String?,
    connectionStatus: String,
    isSlideEnabled: Boolean,
    isHapticsEnabled: Boolean,
    onSave: (String, Int) -> Unit,
    onSendTest: () -> Unit,
    onResetStats: () -> Unit,
    onToggleSlide: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember { mutableStateOf(currentIp) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B22),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFC9D1D9),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF)
                )
                Text("UDP Controller Setup", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Device Network Info
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Device Local IP: $localIp",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: $connectionStatus",
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                }

                // IP Address input field
                OutlinedTextField(
                    value = ipText,
                    onValueChange = {
                        ipText = it
                        inputError = null
                    },
                    label = { Text("Target IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color(0xFF8B949E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ip_input")
                )

                // Port input field (defaults to 5000)
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        inputError = null
                    },
                    label = { Text("Target UDP Port (Default: 5000)") },
                    placeholder = { Text("5000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color(0xFF8B949E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("port_input")
                )

                if (inputError != null) {
                    Text(
                        text = inputError!!,
                        color = Color(0xFFFF7B72),
                        fontSize = 12.sp
                    )
                }

                HorizontalDivider(color = Color(0xFF30363D))

                // Preferences & Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Slide / Drag Across Lanes", fontSize = 13.sp, color = Color.White)
                        Text("Switches lane when finger slides", fontSize = 11.sp, color = Color(0xFF8B949E))
                    }
                    Switch(
                        checked = isSlideEnabled,
                        onCheckedChange = onToggleSlide,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Haptic Feedback on Tap", fontSize = 13.sp, color = Color.White)
                        Text("Vibrate lightly on note hit", fontSize = 11.sp, color = Color(0xFF8B949E))
                    }
                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = onToggleHaptics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E676)
                        )
                    )
                }

                // Packet Telemetry & Test Ping
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Packets: $packetsSent ${if (lastMessage != null) "($lastMessage)" else ""}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8B949E)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onSendTest,
                            modifier = Modifier.testTag("test_packet_button")
                        ) {
                            Text("Send Test", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = onResetStats,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset packet counter",
                                tint = Color(0xFF8B949E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull()
                    if (ipText.isBlank()) {
                        inputError = "Please enter a valid IP address"
                        return@Button
                    }
                    if (port == null || port <= 0 || port > 65535) {
                        inputError = "Port must be between 1 and 65535"
                        return@Button
                    }
                    onSave(ipText.trim(), port)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Apply Target", color = Color(0xFF070B13), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B949E))
            }
        }
    )
}
