package id.ns200.cdir7.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ns200.cdir7.CdiViewModel
import id.ns200.cdir7.CustomAdvancePoint
import id.ns200.cdir7.ui.theme.*

@Composable
fun MapsScreen(viewModel: CdiViewModel) {
    val context = LocalContext.current
    val selectedSlot by viewModel.selectedMapSlot.collectAsState()
    val softLimiterRpm by viewModel.softRevLimiterRpm.collectAsState()
    val softBandRpm by viewModel.softBandRpm.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val customPoints by viewModel.customAdvancePoints.collectAsState()
    val scrollState = rememberScrollState()

    var isCustomMapMode by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var safetyAlertText by remember { mutableStateOf<String?>(null) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    val currentMap = viewModel.mapPresets[selectedSlot]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isCustomMapMode) "CUSTOM ADVANCE TIMING MAP" else "FLASH MEMORY IGNITION MAPS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MotecOrange
                )
                Text(
                    text = if (isCustomMapMode) "8-Titik Interpolasi Bebas • Sektor 63 Flash MCU" else "4 Slot Memori STM32WB55 • Dual Spark 3-D Curve",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RacingLime.copy(alpha = 0.15f))
                    .border(1.dp, RacingLime, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isCustomMapMode) "CUSTOM MAP" else "SLOT ${selectedSlot + 1} ACTIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RacingLime,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // MODE TOGGLE: PRESET SLOTS VS CUSTOM ADVANCE MAP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isCustomMapMode) MotecOrange else Color.Transparent)
                    .clickable { isCustomMapMode = false }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PRESET SLOTS (1-4)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (!isCustomMapMode) CarbonDark else TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCustomMapMode) MotecOrange else Color.Transparent)
                    .clickable { isCustomMapMode = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MAP ADVANCE CUSTOM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isCustomMapMode) CarbonDark else TextSecondary
                )
            }
        }

        if (!isCustomMapMode) {
            // 4 SLOT SELECTION GRID
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.mapPresets.forEach { mapData ->
                    val isSelected = mapData.slot == selectedSlot
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.5.dp,
                                if (isSelected) MotecOrange else BorderSubtle,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectMapSlot(mapData.slot) }
                            .testTag("map_slot_${mapData.slot}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CardHover else CardBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MotecOrange else SurfacePanel)
                                        .border(1.dp, if (isSelected) MotecOrange else BorderSubtle, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "M${mapData.slot + 1}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) CarbonDark else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = mapData.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = mapData.description,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Max: ${mapData.revLimit} RPM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RaceRedline else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Peak: %.1f°".format(mapData.peakAdvance),
                                    fontSize = 10.sp,
                                    color = ElectricCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // INTERACTIVE CURVE GRAPH VISUALIZER
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
                            text = "ADVANCE TIMING CURVE (°BTDC vs RPM)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Real-time: ${telemetry.rpm} RPM / %.1f°".format(telemetry.advanceCdeg / 100f),
                            fontSize = 10.sp,
                            color = MotecOrange,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Canvas Graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(SurfacePanel, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            for (i in 0..4) {
                                val y = h * (i / 4f)
                                drawLine(GridLine, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                            }
                            for (i in 0..6) {
                                val x = w * (i / 6f)
                                drawLine(GridLine, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                            }

                            val points = currentMap.curvePoints
                            val path = Path()
                            points.forEachIndexed { index, pair ->
                                val rpm = pair.first
                                val adv = pair.second
                                val x = (rpm / 12000f).coerceIn(0f, 1f) * w
                                val y = h - (adv / 40f).coerceIn(0f, 1f) * h
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = ElectricCyan,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            points.forEach { pair ->
                                val x = (pair.first / 12000f).coerceIn(0f, 1f) * w
                                val y = h - (pair.second / 40f).coerceIn(0f, 1f) * h
                                drawCircle(color = MotecOrange, radius = 4.dp.toPx(), center = Offset(x, y))
                            }

                            val currentX = (telemetry.rpm / 12000f).coerceIn(0f, 1f) * w
                            drawLine(
                                color = RaceRedline,
                                start = Offset(currentX, 0f),
                                end = Offset(currentX, h),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }

            // SOFT REV-LIMITER SLIDER CARD (9.500 RPM to 12.000 RPM)
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
                            text = "SOFT REV-LIMITER SETTING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RaceRedline,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$softLimiterRpm RPM",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = RaceRedline,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = softLimiterRpm.toFloat(),
                        onValueChange = { viewModel.setSoftRevLimiter(it.toInt()) },
                        valueRange = 9500f..12000f,
                        steps = 24,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = RaceRedline,
                            activeTrackColor = RaceRedline,
                            inactiveTrackColor = SurfacePanel
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("9.500 RPM (Aman)", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        Text("12.000 RPM (Race Extreme)", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Soft Cut Bandwidth:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(200, 400, 600, 800).forEach { band ->
                                val isBandSel = softBandRpm == band
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isBandSel) MotecOrange else SurfacePanel)
                                        .border(1.dp, if (isBandSel) MotecOrange else BorderSubtle, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.setSoftBand(band) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$band",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBandSel) CarbonDark else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SYNC ACTION BUTTON
            Button(
                onClick = { viewModel.syncCurveToBle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sync_curve_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync",
                    tint = CarbonDark,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SINKRONISASI KURVA KE CDI VIA BLE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CarbonDark,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            // ==========================================
            // CUSTOM ADVANCE MAP EDITOR SECTION
            // ==========================================

            // Quick Preset Loader Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Muat Base Preset:",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ECO", "STREET", "RAIN", "PRO").forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.loadCustomPreset(preset)
                                    Toast.makeText(context, "Base preset $preset dimuat!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                            ) {
                                Text(preset, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // CUSTOM MAP GRAPH VISUALIZER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
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
                            text = "CUSTOM ADVANCE GRAPH (MAX 36.0° BTDC)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Live RPM: ${telemetry.rpm}",
                            fontSize = 10.sp,
                            color = RacingLime,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(SurfacePanel, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            for (i in 0..4) {
                                val y = h * (i / 4f)
                                drawLine(GridLine, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                            }
                            for (i in 0..6) {
                                val x = w * (i / 6f)
                                drawLine(GridLine, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                            }

                            // Draw Custom Curve
                            val path = Path()
                            customPoints.forEachIndexed { index, pt ->
                                val x = (pt.rpm / 12000f).coerceIn(0f, 1f) * w
                                val y = h - (pt.advanceDeg / 40f).coerceIn(0f, 1f) * h
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = RacingLime,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            customPoints.forEach { pt ->
                                val x = (pt.rpm / 12000f).coerceIn(0f, 1f) * w
                                val y = h - (pt.advanceDeg / 40f).coerceIn(0f, 1f) * h
                                val isAggressive = pt.advanceDeg > 34.0f
                                drawCircle(
                                    color = if (isAggressive) RaceRedline else MotecOrange,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }

                            val currentX = (telemetry.rpm / 12000f).coerceIn(0f, 1f) * w
                            drawLine(
                                color = RaceRedline,
                                start = Offset(currentX, 0f),
                                end = Offset(currentX, h),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }

            // CRITICAL MOTORSPORT TUNING WARNING BANNER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, RaceRedline, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = RaceRedline.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = RaceRedline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PERINGATAN KESELAMATAN TUNING IGNITION:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = RaceRedline
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "• Advance berlebihan (>34° pada RPM tinggi atau >18° idle) memicu detonasi/knocking ekstrem yang dapat melubangi piston!\n" +
                                    "• Simpan Flash ke MCU HANYA DAPAT DILAKUKAN saat mesin MATI (RPM = 0) dan tegangan HV kapasitor < 30V.\n" +
                                    "• Firmware membatasi advance fisik maksimum 36.0° BTDC.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }

            // 8-POINT EDITABLE SLIDERS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "8-TITIK TIMING ADVANCE (°BTDC)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )

                    customPoints.forEachIndexed { index, pt ->
                        val isAggressive = pt.advanceDeg > 34.0f
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfacePanel, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isAggressive) RaceRedline.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${pt.rpm} RPM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                    if (isAggressive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(RaceRedline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "AGRESIF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RaceRedline,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.updateCustomAdvancePoint(index, pt.advanceDeg - 0.5f) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Text("-", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElectricCyan)
                                    }

                                    Text(
                                        text = "%.1f°".format(pt.advanceDeg),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isAggressive) RaceRedline else MotecOrange
                                    )

                                    IconButton(
                                        onClick = { viewModel.updateCustomAdvancePoint(index, pt.advanceDeg + 0.5f) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElectricCyan)
                                    }
                                }
                            }

                            Slider(
                                value = pt.advanceDeg,
                                onValueChange = { viewModel.updateCustomAdvancePoint(index, it) },
                                valueRange = 0.0f..36.0f,
                                steps = 71, // 0.5 deg step
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = if (isAggressive) RaceRedline else MotecOrange,
                                    activeTrackColor = if (isAggressive) RaceRedline else MotecOrange,
                                    inactiveTrackColor = CarbonDark
                                )
                            )
                        }
                    }
                }
            }

            // SAVE TO MCU FLASH BUTTON WITH SAFETY INTERLOCK
            Button(
                onClick = {
                    // Pre-check safety
                    if (telemetry.rpm > 0) {
                        safetyAlertText = "PERINGATAN KESELAMATAN: Mesin terdeteksi hidup (${telemetry.rpm} RPM)! Simpan Flash MCU hanya diizinkan saat mesin mati (RPM = 0)."
                        showSafetyDialog = true
                    } else if (telemetry.hvCenter >= 30 || telemetry.hvSide >= 30) {
                        safetyAlertText = "PERINGATAN KESELAMATAN: Tegangan kapasitor masih tinggi (CENTER: ${telemetry.hvCenter}V, SIDE: ${telemetry.hvSide}V)! Tunggu HV turun hingga < 30V."
                        showSafetyDialog = true
                    } else {
                        showSaveConfirmDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_custom_map_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RacingLime),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Flash",
                    tint = CarbonDark,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIMPAN MAP CUSTOM KE FLASH MCU",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = CarbonDark,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // SAFETY ERROR DIALOG
    if (showSafetyDialog && safetyAlertText != null) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = RaceRedline) },
            title = {
                Text(
                    text = "OPERASI DITOLAK DEMI KESELAMATAN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = RaceRedline
                )
            },
            text = {
                Text(
                    text = safetyAlertText ?: "",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSafetyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfacePanel)
                ) {
                    Text("MENGERTI", color = TextPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = CardBackground,
            tonalElevation = 6.dp
        )
    }

    // CONFIRMATION DIALOG BEFORE FLASHING MCU
    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MotecOrange) },
            title = {
                Text(
                    text = "KONFIRMASI PENULISAN FLASH MCU",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MotecOrange
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Anda akan menulis 8-titik timing advance kustom ke Flash Sektor 63 STM32WB55 dengan verifikasi CRC32.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = "Kondisi Aman Terpenuhi:\n✓ RPM = 0 (Mesin Mati)\n✓ HV < 30V (Kapasitor Aman)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = RacingLime
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        val error = viewModel.saveCustomMapToMcu()
                        if (error != null) {
                            safetyAlertText = error
                            showSafetyDialog = true
                        } else {
                            Toast.makeText(context, "Map Custom Berhasil Ditulis ke Flash MCU!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MotecOrange)
                ) {
                    Text("TULIS KE MCU", color = CarbonDark, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("BATAL", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = CardBackground,
            tonalElevation = 6.dp
        )
    }
}

