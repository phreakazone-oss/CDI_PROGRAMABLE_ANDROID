package id.ns200.cdir7.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ns200.cdir7.CdiProtocol
import id.ns200.cdir7.CdiViewModel
import id.ns200.cdir7.ui.theme.*

@SuppressLint("MissingPermission")
@Composable
fun BleHexScreen(viewModel: CdiViewModel) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val isScanning by viewModel.isBleScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredBleDevices.collectAsState()
    val rawPacket by viewModel.rawPacket.collectAsState()
    val packetRate by viewModel.packetRateHz.collectAsState()
    val crcPercent by viewModel.crcValidPercent.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val logs by viewModel.terminalLogs.collectAsState()

    var commandInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // BLE GATT STATUS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isConnected) RacingLime else BorderSubtle,
                    RoundedCornerShape(14.dp)
                )
                .testTag("ble_status_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) RacingLime else RaceRedline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "BLE ONLINE • 20 Hz" else "BLE OFFLINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isConnected) RacingLime else RaceRedline
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Demo / Simulation mode toggle button
                        OutlinedButton(
                            onClick = { viewModel.toggleSimulation() },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isSimulation) MotecOrange else TextSecondary
                            )
                        ) {
                            Text(
                                text = if (isSimulation) "SIMULASI ON" else "DEMO MODE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Connect / Disconnect button
                        Button(
                            onClick = { viewModel.toggleConnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) RaceRedline else RacingLime
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isConnected) "DISCONNECT" else "CONNECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CarbonDark,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionStatus,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // GATT Specs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfacePanel, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GattSpecRow("SERVICE UUID", "7a8f1000-6c9d-4e40-a45f-0b4b4e533230")
                    GattSpecRow("TELEMETRY CHAR", "7a8f1001-... (Notify 20Hz • 32 Bytes)")
                    GattSpecRow("COMMAND CHAR", "7a8f1002-... (Write Without Response)")
                    GattSpecRow("RESPONSE CHAR", "7a8f1003-... (Notify ASCII Stream)")
                    GattSpecRow("CRC16 INTEGRITY", "%.1f%% VALID (Polynomial 0x1021)".format(crcPercent))
                }
            }
        }

        // HARDWARE BLE SCANNER & PAIRING CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .testTag("ble_scanner_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BLE HARDWARE CDI SCANNER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotecOrange,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Target: NS200-CDI-R7 / STM32WB55",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopBleScan() else viewModel.startBleScan()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) RaceRedline else MotecOrange
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isScanning) "STOP SCAN" else "SCAN BLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CarbonDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfacePanel, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "Sedang mencari perangkat CDI sekitar..." else "Tekan 'SCAN BLE' untuk mencari modul CDI hardware nyata.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (item in discoveredDevices) {
                            val devName = item.name.ifBlank { "BLE Device" }
                            val devAddr = item.address
                            val isTarget = item.isCdiCandidate

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isTarget) CardHover else SurfacePanel, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isTarget) RacingLime else BorderSubtle, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = devName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTarget) RacingLime else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "$devAddr  (RSSI: ${item.rssi} dBm)",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = { viewModel.connectBleDevice(item.device) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTarget) RacingLime else MotecOrange
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "KONEK",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CarbonDark,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PIN",
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PIN Pairing: 123456 • Timeout GATT 8 ditangani retry otomatis",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // REAL-TIME 32-BYTE RAW HEXADECIMAL PACKET INSPECTOR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .testTag("hex_packet_inspector"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "32-BYTE RAW HEX PACKET STREAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RATE: $packetRate Hz",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RacingLime,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hex Grid Matrix (32 bytes shown in 4 rows of 8 bytes)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfacePanel, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "%02X:".format(row * 8),
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            for (col in 0..7) {
                                val byteIndex = row * 8 + col
                                val byteVal = if (byteIndex < rawPacket.size) rawPacket[byteIndex].toInt() and 0xFF else 0
                                val color = when (byteIndex) {
                                    0, 1, 2 -> ElectricCyan        // Header 15 CD 02
                                    3 -> RacingLime                // Flags
                                    4, 5 -> SensorAmber            // Sequence
                                    6, 7 -> MotecOrange            // RPM
                                    8, 9 -> TechPurple             // TPS
                                    10, 11 -> ElectricCyan         // Advance
                                    12, 13 -> SensorAmber          // Battery
                                    14, 15, 16, 17 -> RacingLime   // HV Caps
                                    18, 19 -> MotecOrange          // Temp
                                    20, 21 -> RaceRedline          // Slot & Limiter
                                    30, 31 -> RacingLime           // CRC16
                                    else -> TextSecondary
                                }
                                Text(
                                    text = "%02X".format(byteVal),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Field Decoder Legend
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendChip("0-2: Magic (15 CD 02)", ElectricCyan)
                        LegendChip("3: Flags (Armed/Ready)", RacingLime)
                        LegendChip("6-7: RPM (${telemetry.rpm})", MotecOrange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendChip("8-9: TPS (${(telemetry.tps / 10f).toInt()}%)", TechPurple)
                        LegendChip("10-11: Adv (%.1f°)".format(telemetry.advanceCdeg / 100f), ElectricCyan)
                        LegendChip("30-31: CRC16 OK", RacingLime)
                    }
                }
            }
        }

        // TERMINAL CONSOLE LOG & COMMAND SENDER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DIAGNOSTIC TERMINAL LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SensorAmber,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Console Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(CarbonDark, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    logs.takeLast(20).forEach { line ->
                        Text(
                            text = "> $line",
                            fontSize = 10.sp,
                            color = if (line.startsWith("TX")) MotecOrange else if (line.startsWith("RX")) ElectricCyan else TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Command Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        placeholder = { Text("Ketik command (e.g. PING, LOAD,0)", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MotecOrange,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = SurfacePanel,
                            unfocusedContainerColor = SurfacePanel
                        )
                    )

                    Button(
                        onClick = {
                            viewModel.sendRawCommand(commandInput)
                            commandInput = ""
                        },
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MotecOrange)
                    ) {
                        Text("KIRIM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Command Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("PING", "LOAD,0", "LOAD,1", "SAVE,0").forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfacePanel)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                .clickable { viewModel.sendRawCommand(cmd) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(cmd, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GattSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LegendChip(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
    }
}
