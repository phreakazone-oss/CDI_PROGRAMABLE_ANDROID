package id.ns200.cdir7.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import id.ns200.cdir7.CdiViewModel
import id.ns200.cdir7.ScreenTab
import id.ns200.cdir7.SetupStage
import id.ns200.cdir7.ui.theme.*

data class HarnessPinItem(
    val pin: String,
    val wireColor: String,
    val name: String,
    val target: String,
    val description: String,
    val status: String,
    val isWarning: Boolean = false,
    val isConfirmed: Boolean = false
)

data class WeActPinItem(
    val pin: String,
    val mcuPin: String,
    val function: String,
    val net: String,
    val note: String,
    val isWarning: Boolean = false
)

data class BomItem(
    val section: String,
    val ref: String,
    val qty: String,
    val component: String,
    val source: String,
    val note: String
)

@Composable
fun QuickSetupGuideScreen(viewModel: CdiViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Alur Quick Setup, 1: Harness J1, 2: WeAct Header, 3: BOM / Belanja

    val j1ConfirmedMap by viewModel.j1ConfirmedMap.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "QUICK SETUP & HARDWARE WIRING",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MotecOrange
                )
                Text(
                    text = "Panduan Resmi NS200 R7 • STM32WB55 Dual-Bank",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (telemetry.setupStage) {
                            SetupStage.READY.code -> RacingLime.copy(alpha = 0.2f)
                            SetupStage.FIRST_START.code -> MotecOrange.copy(alpha = 0.2f)
                            else -> SensorAmber.copy(alpha = 0.2f)
                        }
                    )
                    .border(
                        1.dp,
                        when (telemetry.setupStage) {
                            SetupStage.READY.code -> RacingLime
                            SetupStage.FIRST_START.code -> MotecOrange
                            else -> SensorAmber
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "TAHAP: ${telemetry.stage.label}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (telemetry.setupStage) {
                        SetupStage.READY.code -> RacingLime
                        SetupStage.FIRST_START.code -> MotecOrange
                        else -> SensorAmber
                    },
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Sub-Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "1. Alur Setup",
                "2. Harness J1",
                "3. Pin WeAct",
                "4. BOM Belanja"
            ).forEachIndexed { index, title ->
                val isSel = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) MotecOrange else SurfacePanel)
                        .border(1.dp, if (isSel) MotecOrange else BorderSubtle, RoundedCornerShape(6.dp))
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) CarbonDark else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> QuickSetupFlowView(viewModel, telemetry)
            1 -> HarnessJ1View(viewModel, j1ConfirmedMap)
            2 -> WeActHeaderView()
            3 -> BomShoppingView()
        }
    }
}

@Composable
private fun QuickSetupFlowView(viewModel: CdiViewModel, t: id.ns200.cdir7.Telemetry) {
    val pulserOffset by viewModel.pulserOffsetDeg.collectAsState()
    val strobeActive by viewModel.strobeActive.collectAsState()
    val flashSaved by viewModel.flashSaved.collectAsState()
    var strobeModeChoice by remember { mutableIntStateOf(1) } // 0 = Strobo LED PB9, 1 = Manual Tanpa Strobo (Default)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP SAFETY & INTERLOCK STATUS BAR
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (t.isHvOver300) RaceRedline else BorderSubtle, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS HARDWARE & INTERLOCK FISIK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotecOrange,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "TAHAP: ${t.stage.label.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = RacingLime,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InterlockBadge("SW_ARM (PB3)", t.armed, RacingLime, RaceRedline)
                        InterlockBadge("JP_HV (PB2)", t.hvEnabled, RacingLime, TextMuted)
                        InterlockBadge("JP_PRO (PB4)", t.proJumper, ElectricCyan, TextMuted)
                        InterlockBadge("CENTER KOIL", t.centerEnabled, RacingLime, TextMuted)
                        InterlockBadge("SIDE KOIL", t.sideEnabled, ElectricCyan, TextMuted)
                    }
                }
            }
        }

        // STROBE CLARIFICATION BANNER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = ElectricCyan.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PEMBERITAHUAN SETUP AWAL (STROBO OPSIONAL)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Penggunaan lampu strobo pada Tahap 3 bersifat OPSIONAL. Anda dapat memilih kalibrasi menggunakan lampu strobo LED PB9 atau kalibrasi offset manual langsung tanpa strobo (offset standar Pulsar 200NS: 0.0° s/d -1.5° BTDC).",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // STAGE 1: BARU (0)
        item {
            StageCard(
                stageNumber = 1,
                title = "BARU : Verifikasi Baterai & Komunikasi BLE",
                isCurrent = t.setupStage == SetupStage.BARU.code,
                isDone = t.setupStage > SetupStage.BARU.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.BARU.code) }
            ) {
                Text(
                    text = "• Baterai 12V kontak terhubung pada J1.5. Pastikan tegangan HV < 30V.\n" +
                            "• Pastikan saklar SW_ARM OFF dan jumper JP_HV lepas demi keamanan.\n" +
                            "• Starter mesin 2-3 detik tanpa HV untuk verifikasi komunikasi BLE.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.sendRawCommand("PING") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("1. PING CDI", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestSetupState() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("2. BACA SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.advanceSetupStage(SetupStage.PULSER.code) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("LANJUT KE TAHAP 2 (PULSER)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // STAGE 2: PULSER (1)
        item {
            StageCard(
                stageNumber = 2,
                title = "PULSER : Pengujian Sensor Pick-up Magnet",
                isCurrent = t.setupStage == SetupStage.PULSER.code,
                isDone = t.setupStage > SetupStage.PULSER.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.PULSER.code) }
            ) {
                Text(
                    text = "• Hubungkan kabel pulser putih-merah (J1.10) via LM339 ke pin PA0.\n" +
                            "• Kualitas Pulser Live: ${t.pickupQuality}/100 (Target >= 10).\n" +
                            "• Putar starter 2-3 detik untuk mendeteksi sinyal reluktor.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trigger Edge:", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { viewModel.setPulserEdge("FALLING") },
                            colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("FALLING (NS200)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = { viewModel.setPulserEdge("RISING") },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("RISING", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.confirmPulserPickup() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RacingLime),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = CarbonDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("KONFIRMASI PULSER OK & LANJUT TDC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // STAGE 3: TDC (2) - EXPLICIT STROBE CHOICE (OPTIONAL)
        item {
            StageCard(
                stageNumber = 3,
                title = "TDC : Kalibrasi Titik Mati Atas (OPSIONAL STROBO)",
                isCurrent = t.setupStage == SetupStage.TDC.code,
                isDone = t.setupStage > SetupStage.TDC.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.TDC.code) }
            ) {
                // Choice selector between Strobe vs Manual
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfacePanel, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (strobeModeChoice == 0) MotecOrange else Color.Transparent)
                            .clickable { strobeModeChoice = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DENGAN STROBO LED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (strobeModeChoice == 0) CarbonDark else TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (strobeModeChoice == 1) RacingLime else Color.Transparent)
                            .clickable { strobeModeChoice = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TANPA STROBO (MANUAL)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (strobeModeChoice == 1) CarbonDark else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (strobeModeChoice == 0) {
                    // STROBE HARDWARE OPTION
                    Text(
                        text = "• Hubungkan PB9 ke driver MOSFET FQP30N06L & LED strobo 1W.\n" +
                                "• Buka baut lubang intip magnet bak mesin kiri Pulsar 200NS.\n" +
                                "• Strobo menghasilkan pulsa pengapian terkunci tepat di 10.0° BTDC.\n" +
                                "• Starter mesin dan sesuaikan offset hingga garis 'T' sejajar takik merah.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleStrobe(!strobeActive) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (strobeActive) SensorAmber else SurfacePanel
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (strobeActive) "STROBO ON" else "STROBO OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.saveTdcStrobe() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RacingLime),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SIMPAN TDC STROBO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    // MANUAL OFFSET OPTION (NO STROBE REQUIRED)
                    Text(
                        text = "• Kalibrasi tanpa strobo menggunakan offset manual terukur.\n" +
                                "• Nilai standar Bajaj Pulsar 200NS: 0.0° s/d -1.5° BTDC.\n" +
                                "• Nilai akan disimpan permanen ke Sektor 63 Flash MCU (0x0807E000).",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Offset Pulser:", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "${if (pulserOffset > 0) "+" else ""}%.1f° BTDC".format(pulserOffset),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MotecOrange,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = pulserOffset,
                        onValueChange = { viewModel.setPulserOffset(it) },
                        valueRange = -5.0f..5.0f,
                        steps = 20,
                        colors = SliderDefaults.colors(thumbColor = MotecOrange, activeTrackColor = MotecOrange)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setPulserOffset(0.0f) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text("0.0° STD", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = { viewModel.setPulserOffset(1.5f) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text("+1.5° ADV", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = { viewModel.setPulserOffset(-1.5f) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text("-1.5° RET", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveManualTdc(pulserOffset) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingLime),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = CarbonDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIMPAN TDC MANUAL KE FLASH & LANJUT TPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // STAGE 4: TPS_CAL (3)
        item {
            StageCard(
                stageNumber = 4,
                title = "TPS_CAL : Kalibrasi Gas Tertutup & Penuh",
                isCurrent = t.setupStage == SetupStage.TPS_CAL.code,
                isDone = t.setupStage > SetupStage.TPS_CAL.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.TPS_CAL.code) }
            ) {
                Text(
                    text = "• Mesin dalam kondisi MATI, kunci kontak ON.\n" +
                            "• Sensor TPS membaca ADC pada pin J1.2 & J1.4.\n" +
                            "• Posisi Gas Saat Ini: ${t.tps / 10f}%",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.calibrateTpsClosed() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfacePanel),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("1. GAS TUTUP (0%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.calibrateTpsOpen() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("2. GAS PENUH (100%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // STAGE 5: FIRST_START (4)
        item {
            StageCard(
                stageNumber = 5,
                title = "FIRST_START : Uji Mesin Hidup Pertama",
                isCurrent = t.setupStage == SetupStage.FIRST_START.code,
                isDone = t.setupStage > SetupStage.FIRST_START.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.FIRST_START.code) }
            ) {
                Text(
                    text = "• Pasang jumper JP_HV pada PB2, ubah saklar SW_ARM ke posisi ON.\n" +
                            "• Mode Pengamanan: Tegangan 220V, KOIL CENTER SAJA, Advance <= 10°, Rev-limiter 3.000 RPM.\n" +
                            "• Tegangan HV Kapasitor Live: CENTER ${t.hvCenter}V, SIDE ${t.hvSide}V.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.prepareFirstStartMode() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("AKTIFKAN FIRST START SAFETY MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // STAGE 6: READY (5)
        item {
            StageCard(
                stageNumber = 6,
                title = "READY : Operasi Penuh Normal & Simpan Permanen",
                isCurrent = t.setupStage == SetupStage.READY.code,
                isDone = t.setupStage >= SetupStage.READY.code,
                onSelectStage = { viewModel.advanceSetupStage(SetupStage.READY.code) }
            ) {
                Text(
                    text = "• Mesin hidup stabil >= 3 detik. Siap operasi jalan penuh.\n" +
                            "• Pilih mode koil lalu simpan konfigurasi permanen ke Flash Sektor 63.\n" +
                            "• Boot CDI berikutnya langsung memakai kalibrasi tanpa perlu setup ulang.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.confirmReadyCenterOnly() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfacePanel),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("READY: CENTER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.confirmReadyTripleSpark(0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingLime),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("READY: 3 BUSI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // FOOTER ACTIONS: RESET SETUP & GO TO CUSTOM MAP WITH WARNING
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MANAJEMEN SETUP & ADVANCE MAP CUSTOM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetSetupWorkflow() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = RaceRedline, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESET SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RaceRedline, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.setTab(ScreenTab.MAPS) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = CarbonDark, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MAP CUSTOM ⚠️", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StageCard(
    stageNumber: Int,
    title: String,
    isCurrent: Boolean,
    isDone: Boolean,
    onSelectStage: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                when {
                    isCurrent -> MotecOrange
                    isDone -> RacingLime
                    else -> BorderSubtle
                },
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) CardHover else CardBackground
        ),
        shape = RoundedCornerShape(12.dp)
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> RacingLime
                                    isCurrent -> MotecOrange
                                    else -> SurfacePanel
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isDone -> RacingLime
                                    isCurrent -> MotecOrange
                                    else -> BorderSubtle
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = CarbonDark,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "$stageNumber",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) CarbonDark else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MotecOrange else if (isDone) TextPrimary else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MotecOrange.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PROSES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MotecOrange, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HarnessJ1View(viewModel: CdiViewModel, confirmedMap: Map<String, Boolean>) {
    val j1Pins = listOf(
        HarnessPinItem("J1.1", "NC", "NC", "Tidak Disambung", "Jangan disambung", "KOSONG"),
        HarnessPinItem("J1.2", "Hijau-putih", "TPS_A", "H_BOTTOM.12 (PA3) / H_BOTTOM.14 (PA5)", "J_TPS pin1+pin6 -> TPS_REF atau TPS_SIG", "CONFIRM TPS", isWarning = true),
        HarnessPinItem("J1.3", "Hitam-putih", "TEMP", "H_BOTTOM.13 (PA4 ADC)", "+5V--4.7k--J1.3; 15k--TEMP_ADC; 27k ke GND; clamp BAT54S", "AKTIF"),
        HarnessPinItem("J1.4", "Abu-abu", "TPS_B", "H_BOTTOM.12 (PA3) / H_BOTTOM.14 (PA5)", "J_TPS pin3+pin4 -> TPS_REF atau TPS_SIG", "CONFIRM TPS", isWarning = true),
        HarnessPinItem("J1.5", "+12V kontak", "+12V Kontak", "VIN_PROT / FMAIN5A", "FMAIN5A--DREV--VIN_PROT--L47uH--VIN_FILT (ke FLOGIC dan FHV)", "AKTIF"),
        HarnessPinItem("J1.6", "Hitam-merah", "COIL_SIDE", "H_BOTTOM.11 (PA2 via QNS/QPS)", "Terminal B koil SIDE. SCR2 anode HV_SIDE, cathode GND", "OFFSET WAJIB", isWarning = true),
        HarnessPinItem("J1.7", "Biru-kuning", "FAN_RELAY", "H_TOP.7 (PB5 via QFAN BC547)", "Collector QFAN BC547; base 4.7k dari PB5 dan 10k ke GND", "CONFIRM POLARITAS", isWarning = true),
        HarnessPinItem("J1.8", "NC", "NC", "Tidak Disambung", "Kosong", "KOSONG"),
        HarnessPinItem("J1.9", "NC", "NC", "Tidak Disambung", "Kosong", "KOSONG"),
        HarnessPinItem("J1.10", "Putih-merah", "PULSER", "H_BOTTOM.9 (PA0 TIM2_CH1)", "39k--PICKUP_SENSE; clamp BAT54S; LM339 pin5; pullup 4.7k ke 3V3", "CONFIRM EDGE/OFFSET", isWarning = true),
        HarnessPinItem("J1.11", "Hitam-kuning", "GND", "H_BOTTOM.1 G / H_TOP.1 G", "GND_STAR ke logic & power, LM339 pin12 dan G board", "AKTIF"),
        HarnessPinItem("J1.12", "Koil Center", "COIL_CENTER", "H_BOTTOM.10 (PA1 via QNC/QPC)", "Terminal B koil CENTER. SCR1 anode HV_CENTER, cathode GND", "FIRST START & READY")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotecOrange, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ORIENTASI KONEKTOR CDI J1 (12 PIN)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotecOrange,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Muka soket HARNESS, latch di atas:\n• Baris atas (kiri ke kanan): 1 - 6\n• Baris bawah (kiri ke kanan): 7 - 12\n*Gunakan pigtail adaptor, JANGAN MEMOTONG harness motor!",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        items(j1Pins) { pin ->
            val isConfirmed = confirmedMap[pin.pin] ?: false
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isConfirmed) RacingLime else if (pin.isWarning) SensorAmber else BorderSubtle,
                        RoundedCornerShape(10.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pin.pin,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConfirmed) RacingLime else MotecOrange,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pin.wireColor} (${pin.name})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Tujuan: ${pin.target}",
                            fontSize = 10.sp,
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = pin.description,
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isConfirmed) RacingLime.copy(alpha = 0.2f)
                                    else if (pin.isWarning) SensorAmber.copy(alpha = 0.2f)
                                    else SurfacePanel
                                )
                                .border(
                                    1.dp,
                                    if (isConfirmed) RacingLime else if (pin.isWarning) SensorAmber else BorderSubtle,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConfirmed) "CONFIRMED" else pin.status,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConfirmed) RacingLime else if (pin.isWarning) SensorAmber else TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (pin.isWarning) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { viewModel.toggleConfirmPin(pin.pin) },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isConfirmed) RacingLime else SurfacePanel
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = if (isConfirmed) "UNCHECK" else "KONFIRMASI",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfirmed) CarbonDark else TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeActHeaderView() {
    val bottomPins = listOf(
        WeActPinItem("1", "GND", "Input", "GND_STAR", "Board GND - AKTIF"),
        WeActPinItem("2", "5V", "Input", "LM2596 logic OUT+ 5.00V", "Board 5V - AKTIF"),
        WeActPinItem("3", "5V", "-", "Tidak dipakai", "CADANGAN"),
        WeActPinItem("4", "VBAT", "-", "Jangan hubungkan ke aki motor", "DILARANG 12V!", isWarning = true),
        WeActPinItem("5", "PH3", "-", "Tidak dipakai", "CADANGAN"),
        WeActPinItem("6", "PB9", "Output", "PB9--100R--gate FQP30N06L LED strobo", "LED strobo TDC opsional - QUICK SETUP"),
        WeActPinItem("7", "PB8", "Output", "PB8--1k--TC4427 pin4 INB / QHV2 gate", "TIM1_CH2N - AKTIF"),
        WeActPinItem("8", "NRST", "Input", "Tombol NRST onboard & ST-Link NRST", "PROGRAM/DEBUG"),
        WeActPinItem("9", "PA0", "Input", "LM339 pin2--1k--PA0 (pulser pickup)", "TIM2_CH1 pickup capture - AKTIF"),
        WeActPinItem("10", "PA1", "Output", "PA1--4.7k--QNC/QPC--330R--BT151 SCR1", "Gate CENTER - FIRST START 220V lalu READY"),
        WeActPinItem("11", "PA2", "Output", "PA2--4.7k--QNS/QPS--330R--BT151 SCR2", "Gate SIDE - HANYA SETELAH OFFSET TERUKUR"),
        WeActPinItem("12", "PA3", "Input", "TPS_SIG--15k--TPS_ADC--1k--PA3 (clamp)", "ADC TPS - AKTIF setelah selector"),
        WeActPinItem("13", "PA4", "Input", "J1.3 network--1k--PA4 (coolant temp)", "ADC TEMP - AKTIF"),
        WeActPinItem("14", "PA5", "Input", "TPS_REF--15k--TPS_REF_MON--1k--PA5", "ADC TPS ref monitor - DIAGNOSTIK"),
        WeActPinItem("15", "PA6", "Input", "HV_CENTER--4x270k--HV_C_FB--1k--PA6", "ADC HV CENTER - AKTIF"),
        WeActPinItem("16", "PA7", "Input", "HV_SIDE--4x270k--HV_S_FB--1k--PA7", "ADC HV SIDE - AKTIF"),
        WeActPinItem("17", "PA8", "-", "Tidak dipakai", "CADANGAN"),
        WeActPinItem("18", "PA9", "Output", "PA9--1k--TC4427 pin2 INA / QHV1 gate", "TIM1_CH2 - AKTIF"),
        WeActPinItem("19", "PB2", "Input", "VIN_HV--100k--HV_PRESENT--1k--PB2", "JP_HV sense - AKTIF"),
        WeActPinItem("20", "GND", "Input", "GND_STAR", "Board GND - AKTIF")
    )

    val topPins = listOf(
        WeActPinItem("1", "GND", "Input", "GND_STAR", "Board GND - AKTIF"),
        WeActPinItem("3", "3V3", "Output", "Hanya pullup/clamp/interlock", "Bukan sumber beban besar - AKTIF"),
        WeActPinItem("7", "PB5", "Output", "PB5--4.7k--QFAN base -> J1.7", "FAN relay sink - CONFIRM SEBELUM AKTIF"),
        WeActPinItem("8", "PB4", "Input", "3V3--JP_PRO--PB4 (10k ke GND)", "PRO unlock fisik - WAJIB FISIK"),
        WeActPinItem("9", "PB3", "Input", "3V3--SW_ARM--PB3 (10k ke GND)", "ARM fisik - WAJIB FISIK"),
        WeActPinItem("11", "PA10", "Input", "PWM_CLAMP pullup 4.7k ke 3V3 (LOW=fault)", "Hardware fault - AKTIF"),
        WeActPinItem("12", "PE4", "Output", "LED onboard aktif-low", "Status - AKTIF"),
        WeActPinItem("14", "PB0", "Input", "VIN_FILT--100k--VBAT_ADC--1k--PB0", "ADC1_IN15 battery - AKTIF")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "POSISI FISIK HEADER WeAct STM32WB55CGU6",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Dilihat dari sisi komponen: USB di KIRI, Antena PCB di KANAN.\n• Area antena WAJIB BEBAS logam, kabel HV, dan tembaga.\n• DILARANG memberi 12V ke pin VB!\n• SWD memakai PA13/PA14.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Text(
                text = "H_BOTTOM (LUBANG 1 s/d 20 - KIRI KE KANAN)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SensorAmber,
                fontFamily = FontFamily.Monospace
            )
        }

        items(bottomPins) { pin ->
            PinRowCard(pin)
        }

        item {
            Text(
                text = "H_TOP (LUBANG 1 s/d 15 - KIRI KE KANAN)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SensorAmber,
                fontFamily = FontFamily.Monospace
            )
        }

        items(topPins) { pin ->
            PinRowCard(pin)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PinRowCard(pin: WeActPinItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (pin.isWarning) RaceRedline else BorderSubtle, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (pin.isWarning) RaceRedline else SurfacePanel)
                        .border(1.dp, if (pin.isWarning) RaceRedline else BorderSubtle, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pin.pin,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pin.isWarning) CarbonDark else TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${pin.mcuPin} : ${pin.function}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pin.isWarning) RaceRedline else TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = pin.net,
                        fontSize = 10.sp,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = pin.note,
                fontSize = 9.sp,
                color = if (pin.isWarning) RaceRedline else TextMuted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BomShoppingView() {
    val boms = listOf(
        BomItem("LOGIC", "U1", "1", "WeAct STM32WB55CGU6", "Sudah dimiliki", "WAJIB satu-satunya MCU + BLE"),
        BomItem("POWER", "PCB_POWER", "1", "PCB lubang minimal 5x7cm", "Beli baru", "Clearance HV >= 6mm, terpisah dari antena"),
        BomItem("POWER", "T1", "1", "Trafo utama ATX lilitan 5V CT utuh", "PSU PC bekas", "WAJIB; tidak dibuka/tidak dililit"),
        BomItem("LOGIC", "U2", "1", "LM339N / KA339 DIP-14 5V", "PSU / Beli", "Komparator pulser & overvoltage"),
        BomItem("LOGIC", "U_BUCK", "1", "Modul LM2596 adjustable (in >=35V, out 5V 1A)", "Beli baru", "Catu daya logic"),
        BomItem("POWER", "U4", "1", "TC4427A / TC4427CPA DIP-8", "Beli baru", "WAJIB; jangan ganti TC4427 non-A inverting"),
        BomItem("POWER", "QHV1-2", "2", "IRF3205 55V TO-220 asli", "PSU / Beli", "MOSFET push-pull trafo HV"),
        BomItem("POWER", "SCR1-2", "2", "BT151-600R 600V TO-220", "Beli baru", "Thyristor pemicu koil CENTER & SIDE"),
        BomItem("HV", "C_CAP", "2", "1uF 630V Polypropylene Pulse MKP/MPP", "Beli baru", "WAJIB polypropylene pulse; bukan elko/X2!"),
        BomItem("HV", "DREC1-4", "4", "UF4007 1A 1000V ultrafast", "Beli baru", "Bridge penyearah trafo HV"),
        BomItem("CONTROL", "SW_ARM", "1", "Saklar toggle ON-OFF fisik", "Beli baru", "WAJIB fisik pengaman pengapian"),
        BomItem("CONTROL", "JP_HV/PRO", "2", "Header 2-pin + jumper 2.54mm", "Beli baru", "Jumper HV & PRO unlock"),
        BomItem("HARNESS", "J1", "1", "Pigtail pasangan soket CDI 12-pin NS200", "Donor / Beli", "WAJIB; jangan potong harness motor!")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SensorAmber, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "CATATAN MATERIAL & KOMPONEN KRITIS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SensorAmber,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Donor PSU hanya dipakai bila marking dan rating benar.\n• Kapasitor CDI harus polypropylene pulse 630V MKP/MPP; jangan gunakan elko atau X2!\n• PCB Power HV dipisah fisik minimal 6mm dari board logic dan antena.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        items(boms) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[${item.section}] ${item.ref} (Qty: ${item.qty})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MotecOrange,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = item.component,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.note,
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfacePanel)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(item.source, fontSize = 9.sp, color = ElectricCyan, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InterlockBadge(label: String, active: Boolean, activeColor: Color, inactiveColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else inactiveColor)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (active) activeColor else TextMuted,
            fontFamily = FontFamily.Monospace
        )
    }
}
