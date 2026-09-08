package id.ns200.cdir7.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ns200.cdir7.CdiViewModel
import id.ns200.cdir7.ui.theme.*

@Composable
fun StrobeScreen(viewModel: CdiViewModel) {
    val strobeActive by viewModel.strobeActive.collectAsState()
    val pulserOffset by viewModel.pulserOffsetDeg.collectAsState()
    val flashSaved by viewModel.flashSaved.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val scrollState = rememberScrollState()

    // Strobe flash visual effect animation
    val infiniteTransition = rememberInfiniteTransition(label = "strobe_flash")
    val strobeGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobe_glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Column {
            Text(
                text = "ZERO-DISASSEMBLY CALIBRATION",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = SensorAmber
            )
            Text(
                text = "Kalibrasi Timing Pulser Tanpa Bongkar Bak Magnet",
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        // STROBO LED PB9 SWITCH CARD (10.0° BTDC Locked Pulse)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (strobeActive) SensorAmber else BorderSubtle,
                    RoundedCornerShape(14.dp)
                )
                .testTag("strobe_switch_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (strobeActive) SensorAmber.copy(alpha = strobeGlow)
                                else SurfacePanel
                            )
                            .border(1.dp, if (strobeActive) SensorAmber else BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Strobe",
                            tint = if (strobeActive) CarbonDark else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "STROBO LED PB9 (10.0° BTDC)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = if (strobeActive) "AKTIF • Pulsa Terkunci Presisi 10.0°" else "NONAKTIF (Nyalakan untuk intip garis 'T')",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (strobeActive) SensorAmber else TextMuted
                        )
                    }
                }

                Switch(
                    checked = strobeActive,
                    onCheckedChange = { viewModel.toggleStrobe(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CarbonDark,
                        checkedTrackColor = SensorAmber,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfacePanel
                    )
                )
            }
        }

        // INSPECTION HOLE TIMING ALIGNMENT VISUALIZER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LUBANG INTIP MAGNET (TIMING HOLE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "OFFSET: %+.1f° BTDC".format(pulserOffset),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pulserOffset == 0.0f) RacingLime else MotecOrange,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated Circular Inspection Hole
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(SurfacePanel)
                        .border(3.dp, if (strobeActive) SensorAmber.copy(alpha = strobeGlow) else BorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2)
                        val r = size.width / 2 - 8.dp.toPx()

                        // Rotor texture / background
                        drawCircle(color = CardBackground, radius = r, center = c)

                        // Center Crankcase Notch (Index Pointer at Top)
                        drawLine(
                            color = RaceRedline,
                            start = Offset(c.x, c.y - r),
                            end = Offset(c.x, c.y - r + 18.dp.toPx()),
                            strokeWidth = 3.dp.toPx()
                        )

                        // Flywheel 'T' Mark (moves based on pulserOffset)
                        // If offset is 0, 'T' mark is centered exactly on the pointer!
                        val offsetPx = (pulserOffset / 5.0f) * (r * 0.45f)
                        val tMarkX = c.x + offsetPx

                        drawLine(
                            color = if (strobeActive) SensorAmber else TextPrimary,
                            start = Offset(tMarkX, c.y - r + 4.dp.toPx()),
                            end = Offset(tMarkX, c.y - r + 24.dp.toPx()),
                            strokeWidth = 3.dp.toPx()
                        )

                        // Horizontal bar of 'T'
                        drawLine(
                            color = if (strobeActive) SensorAmber else TextPrimary,
                            start = Offset(tMarkX - 6.dp.toPx(), c.y - r + 24.dp.toPx()),
                            end = Offset(tMarkX + 6.dp.toPx(), c.y - r + 24.dp.toPx()),
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    // Strobe Light Pulse Overlay
                    if (strobeActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SensorAmber.copy(alpha = strobeGlow * 0.15f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Arahkan garis 'T' rotor agar pas sejajar dengan takik merah crankcase saat pulsa strobo menyala.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // MICRO-KOMPENSASI OFFSET PULSER (-5.0° s/d +5.0°)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
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
                        text = "MIKRO-KOMPENSASI OFFSET PULSER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotecOrange,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%+.1f°".format(pulserOffset),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (pulserOffset == 0.0f) RacingLime else MotecOrange,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Slider(
                    value = pulserOffset,
                    onValueChange = { viewModel.setPulserOffset(((it * 10).toInt() / 10f)) },
                    valueRange = -5.0f..5.0f,
                    steps = 100, // 0.1 deg step
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MotecOrange,
                        activeTrackColor = MotecOrange,
                        inactiveTrackColor = SurfacePanel
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-5.0° (Retard)", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("0.0° (Center)", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("+5.0° (Advance)", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fine Adjustment Step Buttons (-1.0, -0.1, +0.1, +1.0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-1.0f to "-1.0°", -0.1f to "-0.1°", 0.1f to "+0.1°", 1.0f to "+1.0°").forEach { step ->
                        Button(
                            onClick = { viewModel.adjustPulserOffset(step.first) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfacePanel),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                text = step.second,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // TOMBOL SIMPAN KE FLASH 0x0807E000 (SEKTOR 63 STM32WB55)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (flashSaved) RacingLime else MotecOrange,
                    RoundedCornerShape(14.dp)
                )
                .testTag("save_flash_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (flashSaved) Icons.Default.CheckCircle else Icons.Default.Save,
                        contentDescription = "Flash Save",
                        tint = if (flashSaved) RacingLime else MotecOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SIMPAN KE FLASH 0x0807E000",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (flashSaved) RacingLime else TextPrimary
                        )
                        Text(
                            text = "Sektor 63 Memori Non-Volatile STM32WB55",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Merekam hasil kalibrasi strobo secara permanen ke memori flash agar status CDI TIDAK PERNAH KEMBALI KE DISARMED saat kontak motor dimatikan.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.saveCalibrationToFlash() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_to_flash_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (flashSaved) RacingLime else MotecOrange
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (flashSaved) "TERSIMPAN PERMANEN DI FLASH 0x0807E000" else "REKAM KALIBRASI KE FLASH SEKTOR 63",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarbonDark,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
