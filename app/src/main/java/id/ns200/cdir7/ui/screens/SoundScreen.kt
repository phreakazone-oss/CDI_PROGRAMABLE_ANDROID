package id.ns200.cdir7.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ns200.cdir7.CdiViewModel
import id.ns200.cdir7.EngineSound
import id.ns200.cdir7.ui.theme.*
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SoundScreen(viewModel: CdiViewModel) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val soundVolume by viewModel.soundVolume.collectAsState()
    val soundPreset by viewModel.soundPreset.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val scrollState = rememberScrollState()

    // File picker launcher for custom audio
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomAudioFile(uri)
        }
    }

    // Audio visualizer wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // MASTER AUDIO SWITCH CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (soundEnabled) RacingLime else BorderSubtle,
                    RoundedCornerShape(14.dp)
                )
                .testTag("master_audio_card"),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (soundEnabled) RacingLime.copy(alpha = 0.2f) else SurfacePanel)
                            .border(1.dp, if (soundEnabled) RacingLime else BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Sound Status",
                            tint = if (soundEnabled) RacingLime else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "VIRTUAL SOUND ENGINE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = if (soundEnabled) "AKTIF • Sintesis Suara Sesuai RPM" else "MUTED (Tekan Saklar Untuk Aktifkan)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (soundEnabled) RacingLime else TextMuted
                        )
                    }
                }

                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CarbonDark,
                        checkedTrackColor = RacingLime,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfacePanel
                    )
                )
            }
        }

        // VOLUME SLIDER CARD
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
                        text = "VOLUME SPEAKER HP / BLUETOOTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotecOrange,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(soundVolume * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = soundVolume,
                    onValueChange = { viewModel.setSoundVolume(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MotecOrange,
                        activeTrackColor = MotecOrange,
                        inactiveTrackColor = SurfacePanel
                    )
                )
            }
        }

        // 6 EXHAUST ACOUSTIC PRESETS
        Text(
            text = "PILIHAN PRESET AKUSTIK KNALPOT",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = ElectricCyan
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineSound.Preset.entries.forEach { preset ->
                val isSelected = preset == soundPreset
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isSelected) ElectricCyan else BorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (preset == EngineSound.Preset.CUSTOM) {
                                filePicker.launch("audio/*")
                            } else {
                                viewModel.setSoundPreset(preset)
                            }
                        }
                        .testTag("sound_preset_${preset.name}"),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) ElectricCyan else BorderSubtle)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = preset.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = when (preset) {
                                        EngineSound.Preset.SINGLE -> "1 Silinder: Karakter asli NS200 silinder tunggal DTS-i"
                                        EngineSound.Preset.TWIN270 -> "2 Silinder: Dentuman berirama khas cross-twin 270 derajat"
                                        EngineSound.Preset.INLINE3 -> "3 Silinder: Suara khas raungan melengking tiga silinder"
                                        EngineSound.Preset.INLINE4 -> "4 Silinder: Screamer RPM tinggi ala superbike 4 silinder"
                                        EngineSound.Preset.CROSS4 -> "4 Silinder: Geraman berat teratur konfigurasi crossplane"
                                        EngineSound.Preset.V4 -> "V4 Silinder: Karakter bertenaga agresif prototipe V4 MotoGP"
                                        EngineSound.Preset.CUSTOM -> "Kustom 5/6+ Silinder: Impor audio MP3/WAV/OGG manual"
                                    },
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricCyan.copy(alpha = 0.2f))
                                    .border(1.dp, ElectricCyan, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // CUSTOM AUDIO TRACKS MANAGER (5/6+ Silinder: MP3/WAV/OGG)
        val customTracks by viewModel.customSoundTracks.collectAsState()
        val selectedCustomTrack by viewModel.selectedCustomTrack.collectAsState()

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
                    Column {
                        Text(
                            text = "CUSTOM 5/6+ SILINDER & AUDIO MANUAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotecOrange,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Dukungan format MP3 / WAV / OGG mengikuti RPM",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { filePicker.launch("audio/*") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MotecOrange),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = CarbonDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TAMBAH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonDark, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (customTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfacePanel, RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada file audio manual 5/6 silinder. Tap TAMBAH untuk memilih berkas MP3/WAV/OGG.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customTracks.forEach { track ->
                            val isSel = selectedCustomTrack?.id == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) CardHover else SurfacePanel)
                                    .border(1.dp, if (isSel) ElectricCyan else BorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectCustomTrack(track) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = "Audio",
                                        tint = if (isSel) ElectricCyan else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = track.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) TextPrimary else TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Base: ${track.baseRpm} RPM • ${track.format}",
                                            fontSize = 9.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeCustomTrack(track) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RaceRedline, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // BLUETOOTH AMPLIFIER TRANSMISSION ROUTE (MH-M18 & PAM8610 2x10W BTL)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TRANSMISI AUDIO BLUETOOTH KE AMPLIFIER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SensorAmber,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Simulasi Rute Modul MH-M18 & PAM8610 (2x10W BTL)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Diagram Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModuleBox(
                        title = "PHONE / APP",
                        desc = "A2DP Stream",
                        status = if (soundEnabled) "TRANSMITTING" else "IDLE",
                        color = if (soundEnabled) RacingLime else TextMuted
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "to",
                        tint = if (soundEnabled) RacingLime else BorderSubtle,
                        modifier = Modifier.size(16.dp)
                    )

                    ModuleBox(
                        title = "MH-M18",
                        desc = "Lossless BLE RX",
                        status = "PAIRED",
                        color = ElectricCyan
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "to",
                        tint = if (soundEnabled) RacingLime else BorderSubtle,
                        modifier = Modifier.size(16.dp)
                    )

                    ModuleBox(
                        title = "PAM8610",
                        desc = "2x10W BTL",
                        status = "STANDBY",
                        color = MotecOrange
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Audio Waveform Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(SurfacePanel, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f
                        val isPlaying = soundEnabled && telemetry.rpm > 500

                        val bars = 36
                        for (i in 0 until bars) {
                            val x = w * (i.toFloat() / bars)
                            val amplitude = if (isPlaying) {
                                val sinVal = abs(sin((i * 0.4f + phase).toDouble())).toFloat()
                                (sinVal * (h * 0.4f) * soundVolume).coerceIn(4f, h * 0.45f)
                            } else 3f

                            drawLine(
                                color = if (isPlaying) ElectricCyan else TextMuted.copy(alpha = 0.3f),
                                start = Offset(x, midY - amplitude),
                                end = Offset(x, midY + amplitude),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Konfigurasi Pin: VCC (5V DC Step-Down), GND (Sasis), L-OUT/R-OUT ke PAM8610 INL/INR.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModuleBox(
    title: String,
    desc: String,
    status: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfacePanel)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
            Text(desc, fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text(status, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}
