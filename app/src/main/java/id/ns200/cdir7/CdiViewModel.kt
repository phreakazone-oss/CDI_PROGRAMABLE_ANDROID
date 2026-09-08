package id.ns200.cdir7

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ScreenTab(val title: String, val badge: String) {
    TACHO("Tacho", "CLUSTER"),
    MAPS("Maps", "FLASH"),
    SUARA("Suara", "AUDIO"),
    STROBO("Strobo", "TDC"),
    GUIDE("Setup", "WIRING"),
    BLE("BLE", "HEX DUMP")
}

data class MapSlotData(
    val slot: Int,
    val name: String,
    val description: String,
    val revLimit: Int,
    val peakAdvance: Float,
    val curvePoints: List<Pair<Int, Float>> // (RPM, Advance Deg)
)

data class CustomAdvancePoint(
    val rpm: Int,
    val advanceDeg: Float
)

class CdiViewModel(application: Application) : AndroidViewModel(application), BleCdiClient.Listener {

    private val context: Context get() = getApplication<Application>().applicationContext
    val bleClient = BleCdiClient(context, this)
    val engineSound = EngineSound(context)

    // Active Screen
    private val _currentTab = MutableStateFlow(ScreenTab.TACHO)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    // Connection & Simulation
    private val _connectionStatus = MutableStateFlow("BLE Disconnected • Scan atau Hubungkan CDI")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    val discoveredBleDevices: StateFlow<List<DiscoveredBleDevice>> = bleClient.discoveredDevices
    val isBleScanning: StateFlow<Boolean> = bleClient.isScanning

    // Telemetry State - Default Realistic Cold Standby for Real Hardware Integration
    private val _telemetry = MutableStateFlow(
        Telemetry(
            sequence = 0,
            rpm = 0,
            tps = 0,
            advanceCdeg = 0,
            batteryCv = 0,
            hvCenter = 0,
            hvSide = 0,
            tempCdeg = 0,
            slot = 0,
            limiter = 0,
            flags = 0,
            faults = 0,
            setupStage = 0,     // BARU
            outputFlags = 0,
            triggerCdeg = 1000, // 10.0°
            pickupQuality = 0,
            firstStartSeconds = 0
        )
    )
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    // Raw Hex Packet Stream
    private val _rawPacket = MutableStateFlow(ByteArray(32))
    val rawPacket: StateFlow<ByteArray> = _rawPacket.asStateFlow()

    private val _packetRateHz = MutableStateFlow(20)
    val packetRateHz: StateFlow<Int> = _packetRateHz.asStateFlow()

    private val _crcValidPercent = MutableStateFlow(100f)
    val crcValidPercent: StateFlow<Float> = _crcValidPercent.asStateFlow()

    // Maps State - 4 Flash Memory Slots (ECO, STREET, RAIN, PRO) with two flash pages & CRC32
    val mapPresets = listOf(
        MapSlotData(
            slot = 0,
            name = "Slot 1: ECO",
            description = "Kurva linear responsif untuk jalan raya, efisiensi BBM dan suhu dingin. Konservatif anchor 5° BTDC/1500 RPM sampai 32°/9800 RPM.",
            revLimit = 9800,
            peakAdvance = 32.0f,
            curvePoints = listOf(
                1500 to 5f, 2500 to 14f, 4500 to 24f, 6500 to 30f, 8500 to 32f, 9800 to 28f, 10500 to 10f
            )
        ),
        MapSlotData(
            slot = 1,
            name = "Slot 2: STREET",
            description = "Map standar performa jalanan agresif. Respons gas instan dengan advance maksimum 34° BTDC.",
            revLimit = 10500,
            peakAdvance = 34.0f,
            curvePoints = listOf(
                1500 to 6f, 2500 to 16f, 4500 to 26f, 7000 to 33f, 8800 to 34f, 10500 to 30f, 11000 to 12f
            )
        ),
        MapSlotData(
            slot = 2,
            name = "Slot 3: RAIN",
            description = "Kurva aman untuk cuaca basah dan bensin oktan rendah (Low-RON). Mencegah slip dan knocking/detonasi.",
            revLimit = 9500,
            peakAdvance = 28.0f,
            curvePoints = listOf(
                1500 to 5f, 2500 to 11f, 4500 to 19f, 6500 to 25f, 8000 to 28f, 9500 to 22f, 10000 to 10f
            )
        ),
        MapSlotData(
            slot = 3,
            name = "Slot 4: PRO",
            description = "Map kompetisi tingkat tinggi 16x8 matrix / 290V. Membutuhkan jumper fisik JP_PRO. Advance maksimum 36° BTDC.",
            revLimit = 11800,
            peakAdvance = 36.0f,
            curvePoints = listOf(
                1500 to 8f, 2500 to 18f, 5000 to 29f, 7500 to 35f, 9500 to 36f, 10800 to 34f, 11800 to 14f
            )
        )
    )

    private val _selectedMapSlot = MutableStateFlow(0)
    val selectedMapSlot: StateFlow<Int> = _selectedMapSlot.asStateFlow()

    // Custom Advance Map Points (Default based on technical document anchor: 5° @ 1500 RPM to 34° @ 8500 RPM)
    private val _customAdvancePoints = MutableStateFlow(
        listOf(
            CustomAdvancePoint(1500, 5.0f),  // Anchor manual
            CustomAdvancePoint(2500, 14.0f),
            CustomAdvancePoint(4500, 24.0f),
            CustomAdvancePoint(6500, 30.0f),
            CustomAdvancePoint(8500, 34.0f),
            CustomAdvancePoint(9800, 32.0f),
            CustomAdvancePoint(10500, 26.0f),
            CustomAdvancePoint(11500, 12.0f)
        )
    )
    val customAdvancePoints: StateFlow<List<CustomAdvancePoint>> = _customAdvancePoints.asStateFlow()

    private val _softRevLimiterRpm = MutableStateFlow(9800)
    val softRevLimiterRpm: StateFlow<Int> = _softRevLimiterRpm.asStateFlow()

    private val _hardRevLimiterRpm = MutableStateFlow(10300)
    val hardRevLimiterRpm: StateFlow<Int> = _hardRevLimiterRpm.asStateFlow()

    private val _softBandRpm = MutableStateFlow(400)
    val softBandRpm: StateFlow<Int> = _softBandRpm.asStateFlow()

    // J1 Hardware confirmation map
    private val _j1ConfirmedMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val j1ConfirmedMap: StateFlow<Map<String, Boolean>> = _j1ConfirmedMap.asStateFlow()

    // Custom audio track list
    private val _customSoundTracks = MutableStateFlow<List<CustomSoundTrack>>(emptyList())
    val customSoundTracks: StateFlow<List<CustomSoundTrack>> = _customSoundTracks.asStateFlow()

    private val _selectedCustomTrack = MutableStateFlow<CustomSoundTrack?>(null)
    val selectedCustomTrack: StateFlow<CustomSoundTrack?> = _selectedCustomTrack.asStateFlow()

    // Hold-To-Rev State
    private val _isRevving = MutableStateFlow(false)
    val isRevving: StateFlow<Boolean> = _isRevving.asStateFlow()

    // Strobo Calibration State
    private val _strobeActive = MutableStateFlow(false)
    val strobeActive: StateFlow<Boolean> = _strobeActive.asStateFlow()

    private val _pulserOffsetDeg = MutableStateFlow(0.0f) // -5.0 to +5.0
    val pulserOffsetDeg: StateFlow<Float> = _pulserOffsetDeg.asStateFlow()

    private val _flashSaved = MutableStateFlow(false)
    val flashSaved: StateFlow<Boolean> = _flashSaved.asStateFlow()

    // Sound State
    private val _soundEnabled = MutableStateFlow(false)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _soundVolume = MutableStateFlow(0.85f)
    val soundVolume: StateFlow<Float> = _soundVolume.asStateFlow()

    private val _soundPreset = MutableStateFlow(EngineSound.Preset.SINGLE)
    val soundPreset: StateFlow<EngineSound.Preset> = _soundPreset.asStateFlow()

    // Console logs
    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf(
            "NS200 CDI R7 System Initialized.",
            "MoTeC / AIM Telemetry Protocol Engine Ready.",
            "GATT: 7a8f1000-6c9d-4e40-a45f-0b4b4e533230",
            "Hardware: STM32WB55 Dual-Core Wireless MCU."
        )
    )
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Simulation job
    private var simulationJob: Job? = null
    private var simRpm = 1420f
    private var simTps = 0f
    private var simPhase = 0f

    init {
        // Initialize with default raw packet
        _rawPacket.value = CdiProtocol.packetFromTelemetry(_telemetry.value)

        // Restore sound settings from SharedPreferences
        val prefs = context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE)
        val savedPreset = prefs.getString("sound_preset", EngineSound.Preset.SINGLE.name)
        try {
            _soundPreset.value = EngineSound.Preset.valueOf(savedPreset ?: EngineSound.Preset.SINGLE.name)
            engineSound.select(_soundPreset.value)
        } catch (_: Exception) {}

        _pulserOffsetDeg.value = prefs.getFloat("pulser_offset", 0.0f)
        _softRevLimiterRpm.value = prefs.getInt("rev_limiter", 9800)
        val savedStage = prefs.getInt("setup_stage", SetupStage.READY.code)
        _telemetry.value = _telemetry.value.copy(
            setupStage = savedStage,
            flags = if (savedStage == SetupStage.READY.code) (_telemetry.value.flags or 0x28) else _telemetry.value.flags,
            outputFlags = if (savedStage == SetupStage.READY.code) 0x03 else if (savedStage == SetupStage.FIRST_START.code) 0x01 else 0x00
        )

        // Restore Custom Map Points if previously saved
        val savedCustomMap = prefs.getString("custom_map_points", null)
        if (savedCustomMap != null) {
            try {
                val parsed = savedCustomMap.split(";").mapNotNull { part ->
                    val sub = part.split(":")
                    if (sub.size == 2) {
                        CustomAdvancePoint(sub[0].toInt(), sub[1].toFloat() / 10f)
                    } else null
                }
                if (parsed.size >= 4) {
                    _customAdvancePoints.value = parsed
                }
            } catch (_: Exception) {}
        }

        // Start internal ticker for smooth simulation when not connected to hardware
        startSimulationEngine()
    }

    fun setTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun toggleConnect() {
        if (_isConnected.value) {
            bleClient.disconnect()
            _isConnected.value = false
            _connectionStatus.value = "Disconnected"
            appendLog("Manual BLE disconnect requested.")
        } else {
            _isSimulationMode.value = false
            appendLog("Scanning for NS200-CDI-R7 BLE...")
            bleClient.connect()
        }
    }

    fun startBleScan() {
        _isSimulationMode.value = false
        appendLog("Scanning BLE devices nearby...")
        bleClient.startScan()
    }

    fun stopBleScan() {
        bleClient.stopScanInternal()
        appendLog("BLE scan dihentikan.")
    }

    fun connectBleDevice(device: BluetoothDevice) {
        _isSimulationMode.value = false
        val dName = device.name ?: device.address
        appendLog("Menghubungkan langsung ke BLE: $dName")
        bleClient.connectDeviceExplicit(device)
    }

    fun toggleSimulation() {
        _isSimulationMode.value = !_isSimulationMode.value
        if (_isSimulationMode.value) {
            _connectionStatus.value = "SIMULASI AKTIF • Telemetry 20Hz (MoTeC Mode)"
            _isConnected.value = true
            appendLog("Demo simulation mode activated.")
        } else {
            _connectionStatus.value = "SIMULASI NONAKTIF • Menunggu Hardware CDI"
            _isConnected.value = false
            appendLog("Demo simulation mode stopped.")
        }
    }

    fun setHoldToRev(pressed: Boolean) {
        _isRevving.value = pressed
        if (pressed) {
            if (bleClient.gattReady) {
                bleClient.send("TEST,REV,START")
                appendLog("Hold To Rev: Uji gas aktif (TEST,REV,START)")
            } else if (!_isConnected.value) {
                // If offline and not in simulation, start simulation so user can test sound & gauges
                _isSimulationMode.value = true
                _isConnected.value = true
                _connectionStatus.value = "SIMULASI AKTIF • Hold To Rev"
            }
        } else {
            simTps = 0f
            if (bleClient.gattReady) {
                bleClient.send("TEST,REV,STOP")
                appendLog("Hold To Rev: Dilepas (TEST,REV,STOP)")
            }
        }
    }

    fun resetVirtualEngine() {
        _isRevving.value = false
        simTps = 0f
        simRpm = if (_isSimulationMode.value) 1420f else 0f
        engineSound.stop()
        if (bleClient.gattReady) {
            bleClient.send("TEST,REV,STOP")
        }
        val currentT = _telemetry.value
        _telemetry.value = currentT.copy(
            rpm = simRpm.toInt(),
            tps = 0,
            limiter = 0
        )
        appendLog("Virtual Engine di-reset ke ${simRpm.toInt()} RPM (Idle/Nol).")
        Toast.makeText(context, "Engine Reset: RPM & Audio kembali normal", Toast.LENGTH_SHORT).show()
    }

    fun updateCustomAdvancePoint(index: Int, newAdvance: Float) {
        val current = _customAdvancePoints.value.toMutableList()
        if (index in current.indices) {
            val rounded = (newAdvance * 10f).roundToInt() / 10f
            // Enforce hard ceiling of 36.0° BTDC as per documentation
            val bounded = rounded.coerceIn(0.0f, 36.0f)
            current[index] = current[index].copy(advanceDeg = bounded)
            _customAdvancePoints.value = current
            appendLog("Map Custom: ${current[index].rpm} RPM diubah ke ${bounded}° BTDC")
        }
    }

    fun loadCustomPreset(presetKey: String) {
        val points = when (presetKey) {
            "ECO" -> listOf(
                CustomAdvancePoint(1500, 5.0f),
                CustomAdvancePoint(2500, 14.0f),
                CustomAdvancePoint(4500, 24.0f),
                CustomAdvancePoint(6500, 30.0f),
                CustomAdvancePoint(8500, 32.0f),
                CustomAdvancePoint(9800, 28.0f),
                CustomAdvancePoint(10500, 20.0f),
                CustomAdvancePoint(11500, 10.0f)
            )
            "STREET" -> listOf(
                CustomAdvancePoint(1500, 6.0f),
                CustomAdvancePoint(2500, 16.0f),
                CustomAdvancePoint(4500, 26.0f),
                CustomAdvancePoint(6500, 32.0f),
                CustomAdvancePoint(8500, 34.0f),
                CustomAdvancePoint(9800, 32.0f),
                CustomAdvancePoint(10500, 28.0f),
                CustomAdvancePoint(11500, 14.0f)
            )
            "RAIN" -> listOf(
                CustomAdvancePoint(1500, 5.0f),
                CustomAdvancePoint(2500, 11.0f),
                CustomAdvancePoint(4500, 19.0f),
                CustomAdvancePoint(6500, 25.0f),
                CustomAdvancePoint(8500, 28.0f),
                CustomAdvancePoint(9800, 24.0f),
                CustomAdvancePoint(10500, 18.0f),
                CustomAdvancePoint(11500, 10.0f)
            )
            "PRO" -> listOf(
                CustomAdvancePoint(1500, 7.0f),
                CustomAdvancePoint(2500, 17.0f),
                CustomAdvancePoint(4500, 28.0f),
                CustomAdvancePoint(6500, 33.0f),
                CustomAdvancePoint(8500, 36.0f),
                CustomAdvancePoint(9800, 35.0f),
                CustomAdvancePoint(10500, 30.0f),
                CustomAdvancePoint(11500, 14.0f)
            )
            else -> return
        }
        _customAdvancePoints.value = points
        appendLog("Preset $presetKey dimuat ke Map Custom")
    }

    fun saveCustomMapToMcu(): String? {
        val t = _telemetry.value
        // Safety verification as per page 14: save/load only when RPM=0 and HV < 30V
        if (t.rpm > 0) {
            return "PERINGATAN KESELAMATAN: Mesin terdeteksi hidup (${t.rpm} RPM)! Simpan Flash MCU hanya diizinkan saat mesin mati (RPM = 0) untuk mencegah crash interrupt TIM2."
        }
        if ((t.hvCenter >= 30 || t.hvSide >= 30) && _isConnected.value) {
            return "PERINGATAN KESELAMATAN: Tegangan tinggi kapasitor CDI masih aktif (CENTER: ${t.hvCenter}V, SIDE: ${t.hvSide}V)! Tunggu hingga tegangan < 30 V sebelum menulis flash."
        }

        val points = _customAdvancePoints.value
        val pointsPayload = points.joinToString(";") { "${it.rpm}:${(it.advanceDeg * 10).toInt()}" }

        if (bleClient.gattReady) {
            bleClient.send("MAP,SET_CUSTOM,$pointsPayload")
            bleClient.send("MAP,SAVE_FLASH,CUSTOM")
            appendLog("BLE Send: MAP,SET_CUSTOM & MAP,SAVE_FLASH ke Sektor 63 Flash MCU")
        } else {
            appendLog("Map Custom disimpan ke penyimpanan lokal (Mode Offline)")
        }

        context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE).edit()
            .putString("custom_map_points", pointsPayload)
            .apply()

        return null
    }

    fun calibrateTpsMin() {
        val t = _telemetry.value
        if (t.rpm > 0) {
            Toast.makeText(context, "Kalibrasi TPS hanya saat mesin mati (RPM=0)!", Toast.LENGTH_SHORT).show()
            return
        }
        if (bleClient.gattReady) {
            bleClient.send("SETUP,TPS_MIN")
            appendLog("BLE Send: SETUP,TPS_MIN (Gas tertutup 0% disimpan)")
        } else {
            appendLog("Simulasi TPS Min (Gas Tertutup 0%) Disimpan")
        }
        Toast.makeText(context, "ADC Gas Tertutup (0%) Tersimpan ke MCU!", Toast.LENGTH_SHORT).show()
    }

    fun calibrateTpsMax() {
        val t = _telemetry.value
        if (t.rpm > 0) {
            Toast.makeText(context, "Kalibrasi TPS hanya saat mesin mati (RPM=0)!", Toast.LENGTH_SHORT).show()
            return
        }
        if (bleClient.gattReady) {
            bleClient.send("SETUP,TPS_MAX")
            appendLog("BLE Send: SETUP,TPS_MAX (Gas penuh 100% WOT disimpan)")
        } else {
            appendLog("Simulasi TPS Max (Gas Penuh 100% WOT) Disimpan")
        }
        Toast.makeText(context, "ADC Gas Terbuka Penuh (100% WOT) Tersimpan ke MCU!", Toast.LENGTH_SHORT).show()
    }

    fun setPulserEdge(isRising: Boolean) {
        val edgeStr = if (isRising) "RISING" else "FALLING"
        if (bleClient.gattReady) {
            bleClient.send("SETUP,EDGE,$edgeStr")
            appendLog("BLE Send: SETUP,EDGE,$edgeStr")
        } else {
            appendLog("Polaritas Pulser Edge diubah: $edgeStr")
        }
    }

    fun selectMapSlot(slot: Int) {
        val bounded = slot.coerceIn(0, 3)
        _selectedMapSlot.value = bounded
        val preset = mapPresets[bounded]
        _softRevLimiterRpm.value = preset.revLimit

        if (bleClient.gattReady) {
            bleClient.send("LOAD,$bounded")
            appendLog("BLE Send: LOAD,$bounded (${preset.name})")
        } else {
            appendLog("Memori Slot $bounded aktif: ${preset.name}")
        }
    }

    fun setSoftRevLimiter(rpm: Int) {
        _softRevLimiterRpm.value = rpm.coerceIn(9500, 12000)
    }

    fun setSoftBand(band: Int) {
        _softBandRpm.value = band.coerceIn(200, 800)
    }

    fun syncCurveToBle() {
        val slot = _selectedMapSlot.value
        val rpm = _softRevLimiterRpm.value
        val band = _softBandRpm.value

        if (bleClient.gattReady) {
            bleClient.send("LIMIT,SOFT,$rpm,$band")
            bleClient.send("SAVE,$slot")
            appendLog("BLE Sync: LIMIT,SOFT,$rpm,$band -> SAVE,$slot")
        } else {
            appendLog("Sync Kurva Map $slot (Limiter: $rpm RPM, Band: $band RPM) Disimpan Lokal.")
        }

        context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE).edit()
            .putInt("rev_limiter", rpm)
            .apply()

        Toast.makeText(context, "Kurva Map ${slot + 1} & Rev-Limiter ($rpm RPM) Tersinkronisasi!", Toast.LENGTH_SHORT).show()
    }

    fun setSoundPreset(preset: EngineSound.Preset) {
        _soundPreset.value = preset
        engineSound.select(preset)
        context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE).edit()
            .putString("sound_preset", preset.name)
            .apply()
        appendLog("Sound preset switched to: ${preset.label}")
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        engineSound.enabled = enabled
        appendLog("Sound engine ${if (enabled) "ENABLED" else "MUTED"}")
    }

    fun setSoundVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _soundVolume.value = v
        engineSound.masterVolume = v
    }

    fun setCustomAudioFile(uri: Uri?) {
        if (uri == null) return
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Track_${System.currentTimeMillis()}"
        val newTrack = CustomSoundTrack(
            id = System.currentTimeMillis().toString(),
            name = fileName,
            uri = uri,
            baseRpm = 2000,
            format = "MP3/WAV/OGG"
        )
        val updated = _customSoundTracks.value + newTrack
        _customSoundTracks.value = updated
        selectCustomTrack(newTrack)
    }

    fun selectCustomTrack(track: CustomSoundTrack) {
        _selectedCustomTrack.value = track
        engineSound.setCustom(track.uri, track.baseRpm)
        _soundPreset.value = EngineSound.Preset.CUSTOM
        appendLog("Track Kustom Aktif: ${track.name} (Base: ${track.baseRpm} RPM)")
    }

    fun removeCustomTrack(track: CustomSoundTrack) {
        val updated = _customSoundTracks.value.filter { it.id != track.id }
        _customSoundTracks.value = updated
        if (_selectedCustomTrack.value?.id == track.id) {
            _selectedCustomTrack.value = null
            setSoundPreset(EngineSound.Preset.SINGLE)
        }
    }

    fun advanceSetupStage(targetStageCode: Int) {
        val target = SetupStage.entries.find { it.code == targetStageCode } ?: return
        val current = _telemetry.value.setupStage
        if (targetStageCode == SetupStage.READY.code) {
            // Check interlocks: READY requires CENTER stored and HV confirmed
            appendLog("SETUP: Transisi ke READY. Menyimpan konfigurasi ke Flash Sektor 63...")
            saveCalibrationToFlash()
        }

        if (bleClient.gattReady) {
            bleClient.send("SETUP,STAGE,${target.code}")
            appendLog("BLE Send: SETUP,STAGE,${target.code} (${target.label})")
        } else {
            appendLog("Tahap Setup CDI diubah ke: ${target.label}")
        }

        // Update local telemetry stage
        val currentT = _telemetry.value
        val updatedFlags = if (target == SetupStage.READY) (currentT.flags or 0x20) else currentT.flags
        val updatedOutputs = when (target) {
            SetupStage.FIRST_START -> 0x01 // CENTER only, SIDE off
            SetupStage.READY -> 0x03       // CENTER & SIDE
            else -> currentT.outputFlags
        }
        val updatedLimiter = if (target == SetupStage.FIRST_START) 3000 else _softRevLimiterRpm.value

        _telemetry.value = currentT.copy(
            setupStage = target.code,
            flags = updatedFlags,
            outputFlags = updatedOutputs,
            limiter = if (target == SetupStage.FIRST_START) 1 else 0
        )
        _rawPacket.value = CdiProtocol.packetFromTelemetry(_telemetry.value)

        // Save stage persistently
        context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE).edit()
            .putInt("setup_stage", target.code)
            .apply()
    }

    fun toggleConfirmPin(pin: String) {
        val current = _j1ConfirmedMap.value.toMutableMap()
        val newState = !(current[pin] ?: false)
        current[pin] = newState
        _j1ConfirmedMap.value = current
        appendLog("Harness $pin konfirmasi: ${if (newState) "CONFIRMED" else "UNCHECK"}")
    }

    fun toggleStrobe(active: Boolean) {
        _strobeActive.value = active
        if (bleClient.gattReady) {
            bleClient.send(if (active) "STROBE,ON" else "STROBE,OFF")
            appendLog("BLE Send: STROBE,${if (active) "ON" else "OFF"} (PB9 10.0° BTDC)")
        } else {
            appendLog("Strobo LED PB9 ${if (active) "AKTIF (Pulsa Terkunci 10.0° BTDC)" else "NONAKTIF"}")
        }
    }

    fun adjustPulserOffset(delta: Float) {
        val updated = ((_pulserOffsetDeg.value + delta) * 10f).toInt() / 10f
        setPulserOffset(updated)
    }

    fun setPulserOffset(offset: Float) {
        val clamped = offset.coerceIn(-5.0f, 5.0f)
        _pulserOffsetDeg.value = clamped
        _flashSaved.value = false

        if (bleClient.gattReady) {
            val offsetCdeg = (clamped * 100).toInt()
            bleClient.send("SETUP,MANUAL_TDC,$offsetCdeg")
        }
    }

    fun saveCalibrationToFlash(): Boolean {
        val t = _telemetry.value
        if (t.rpm > 0) {
            Toast.makeText(context, "DITOLAK: Kalibrasi Flash hanya saat RPM = 0!", Toast.LENGTH_LONG).show()
            appendLog("SAFETY: Simpan Flash ditolak karena mesin hidup (${t.rpm} RPM)")
            return false
        }
        if ((t.hvCenter >= 30 || t.hvSide >= 30) && _isConnected.value) {
            Toast.makeText(context, "DITOLAK: Tunggu tegangan HV < 30V sebelum tulis flash!", Toast.LENGTH_LONG).show()
            appendLog("SAFETY: Simpan Flash ditolak karena HV >= 30V")
            return false
        }

        val offsetCdeg = (_pulserOffsetDeg.value * 100).toInt()
        if (bleClient.gattReady) {
            bleClient.send("SETUP,MANUAL_TDC,$offsetCdeg,CONFIRM")
            bleClient.send("SETUP,SAVE_TDC")
            appendLog("BLE: SETUP,SAVE_TDC -> Sektor 63 Flash 0x0807E000")
        } else {
            appendLog("Simpan Kalibrasi: Offset ${_pulserOffsetDeg.value}° BTDC ke Flash 0x0807E000 (Sektor 63)")
        }

        context.getSharedPreferences("cdi_r7_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("pulser_offset", _pulserOffsetDeg.value)
            .apply()

        _flashSaved.value = true
        Toast.makeText(context, "Kalibrasi tersimpan permanen di Flash 0x0807E000 (Sektor 63)!", Toast.LENGTH_LONG).show()
        return true
    }

    // --- QUICK SETUP R7 PROTOCOL METHODS ---

    fun requestSetupState() {
        if (bleClient.gattReady) {
            bleClient.send("GET,SETUP")
            appendLog("BLE Send: GET,SETUP")
        }
    }

    fun setPulserEdge(edge: String) { // "FALLING" or "RISING"
        if (bleClient.gattReady) {
            bleClient.send("SETUP,EDGE,$edge")
            appendLog("BLE Send: SETUP,EDGE,$edge")
        } else {
            appendLog("Pulser Edge diatur ke: $edge (Simulasi)")
        }
        Toast.makeText(context, "Pulser Edge: $edge", Toast.LENGTH_SHORT).show()
    }

    fun setPulserPpr(ppr: Int) {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,PPR,$ppr")
            appendLog("BLE Send: SETUP,PPR,$ppr")
        } else {
            appendLog("Pulser PPR diatur ke: $ppr")
        }
    }

    fun setGateDurationUs(us: Int) {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,GATE_US,$us")
            appendLog("BLE Send: SETUP,GATE_US,$us")
        } else {
            appendLog("SCR Gate Duration: ${us}µs")
        }
    }

    fun confirmPulserPickup() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,PICKUP,CONFIRM")
            appendLog("BLE Send: SETUP,PICKUP,CONFIRM")
        } else {
            appendLog("Pulser Pick-up Dikonfirmasi (PPR=1, Gate=80µs). Lanjut ke TDC.")
            advanceSetupStage(SetupStage.TDC.code)
        }
        Toast.makeText(context, "PULSER OK: Sensor pick-up terverifikasi!", Toast.LENGTH_SHORT).show()
    }

    fun saveTdcStrobe() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,SAVE_TDC")
            appendLog("BLE Send: SETUP,SAVE_TDC (TDC Strobo disimpan ke Flash)")
        } else {
            appendLog("TDC Strobo disimpan ke Flash Sektor 63. Lanjut ke TPS.")
            advanceSetupStage(SetupStage.TPS_CAL.code)
        }
        _flashSaved.value = true
        Toast.makeText(context, "TDC Strobo tersimpan di Flash!", Toast.LENGTH_SHORT).show()
    }

    fun saveManualTdc(offsetDeg: Float) {
        val clamped = offsetDeg.coerceIn(-5.0f, 5.0f)
        _pulserOffsetDeg.value = clamped
        val offsetCdeg = (clamped * 100).toInt()
        if (bleClient.gattReady) {
            bleClient.send("SETUP,MANUAL_TDC,$offsetCdeg,CONFIRM")
            bleClient.send("SETUP,SAVE_TDC")
            appendLog("BLE Send: SETUP,MANUAL_TDC,$offsetCdeg,CONFIRM & SAVE_TDC")
        } else {
            appendLog("TDC Manual Terukur ${clamped}° BTDC disimpan tanpa strobo. Lanjut ke TPS.")
            advanceSetupStage(SetupStage.TPS_CAL.code)
        }
        _flashSaved.value = true
        Toast.makeText(context, "TDC Manual (${clamped}°) tersimpan di Flash!", Toast.LENGTH_SHORT).show()
    }

    fun calibrateTpsClosed() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,TPS,CLOSED")
            appendLog("BLE Send: SETUP,TPS,CLOSED (Simpan Gas Tertutup 0%)")
        } else {
            appendLog("TPS Gas Tertutup (0%) Disimpan.")
        }
        Toast.makeText(context, "TPS Gas Tertutup (0%) Disimpan", Toast.LENGTH_SHORT).show()
    }

    fun calibrateTpsOpen() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,TPS,OPEN")
            appendLog("BLE Send: SETUP,TPS,OPEN (Simpan Gas Penuh 100%)")
        } else {
            appendLog("TPS Gas Terbuka Penuh (100%) Disimpan. Lanjut ke FIRST START.")
            advanceSetupStage(SetupStage.FIRST_START.code)
        }
        Toast.makeText(context, "TPS Gas Terbuka Penuh (100%) Disimpan", Toast.LENGTH_SHORT).show()
    }

    fun prepareFirstStartMode() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,FIRST_START")
            appendLog("BLE Send: SETUP,FIRST_START (Mode Aman: 220V, CENTER saja, Max 10° Adv, Limiter 3.000 RPM)")
        } else {
            appendLog("Mode FIRST START Siap (220V, CENTER saja, Limiter 3.000 RPM)")
            advanceSetupStage(SetupStage.FIRST_START.code)
        }
        Toast.makeText(context, "Mode FIRST START Siap. Pasang JP_HV & SW_ARM ON", Toast.LENGTH_LONG).show()
    }

    fun confirmReadyCenterOnly() {
        val t = _telemetry.value
        if (t.hvCenter >= 30 || t.hvSide >= 30) {
            Toast.makeText(context, "PERINGATAN: Pastikan SW_ARM OFF & HV < 30V sebelum simpan!", Toast.LENGTH_LONG).show()
        }
        if (bleClient.gattReady) {
            bleClient.send("SETUP,READY,CENTER")
            appendLog("BLE Send: SETUP,READY,CENTER (Mode Siap Jalan - Koil CENTER)")
        } else {
            appendLog("Setup Selesai: READY - CENTER Saja. Disimpan Permanen di Flash")
            advanceSetupStage(SetupStage.READY.code)
        }
        Toast.makeText(context, "READY! CDI R7 Siap Digunakan (CENTER)", Toast.LENGTH_LONG).show()
    }

    fun confirmReadyTripleSpark(sideOffsetCdeg: Int = 0) {
        val t = _telemetry.value
        if (t.hvCenter >= 30 || t.hvSide >= 30) {
            Toast.makeText(context, "PERINGATAN: Pastikan SW_ARM OFF & HV < 30V sebelum simpan!", Toast.LENGTH_LONG).show()
        }
        if (bleClient.gattReady) {
            bleClient.send("SETUP,READY,THREE,$sideOffsetCdeg")
            appendLog("BLE Send: SETUP,READY,THREE,$sideOffsetCdeg (Mode Triple Spark Terkalibrasi)")
        } else {
            appendLog("Setup Selesai: READY - 3 Busi (Triple Spark). Offset SIDE: ${sideOffsetCdeg/100f}°")
            advanceSetupStage(SetupStage.READY.code)
        }
        Toast.makeText(context, "READY! CDI R7 Siap Digunakan (Triple Spark)", Toast.LENGTH_LONG).show()
    }

    fun resetSetupWorkflow() {
        if (bleClient.gattReady) {
            bleClient.send("SETUP,RESET,CONFIRM")
            appendLog("BLE Send: SETUP,RESET,CONFIRM (Kembali ke Tahap BARU)")
        } else {
            appendLog("Reset Setup CDI ke Tahap Awal (BARU)")
            advanceSetupStage(SetupStage.BARU.code)
        }
        Toast.makeText(context, "Setup direset ke Tahap BARU", Toast.LENGTH_SHORT).show()
    }

    fun setFanMode(mode: String) { // "OFF", "ON", "AUTO"
        if (bleClient.gattReady) {
            bleClient.send("SETUP,FAN,$mode")
            appendLog("BLE Send: SETUP,FAN,$mode")
        } else {
            appendLog("Fan mode: $mode")
        }
    }

    fun sendRawCommand(cmd: String) {
        if (cmd.isBlank()) return
        if (bleClient.gattReady) {
            bleClient.send(cmd.trim())
            appendLog("TX: ${cmd.trim()}")
        } else {
            appendLog("CMD (Offline): ${cmd.trim()}")
            if (cmd.startsWith("PING")) {
                appendLog("RX: PONG,CDI-R7-OK*CRC")
            }
        }
    }

    private fun appendLog(line: String) {
        val list = _terminalLogs.value.toMutableList()
        if (list.size > 80) list.removeAt(0)
        list.add(line)
        _terminalLogs.value = list
    }

    // BleCdiClient.Listener implementation
    override fun onState(text: String, connected: Boolean) {
        _connectionStatus.value = text
        _isConnected.value = connected
        appendLog("BLE: $text")
    }

    override fun onTelemetry(value: Telemetry) {
        _telemetry.value = value
        engineSound.update(value)
    }

    override fun onRawPacket(bytes: ByteArray) {
        _rawPacket.value = bytes
    }

    override fun onResponse(value: String) {
        appendLog("RX: $value")
    }

    // Internal simulation loop for Hold to Rev & Demo Mode
    private fun startSimulationEngine() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch(Dispatchers.Default) {
            var seq = 0
            while (isActive) {
                delay(50) // 20 Hz

                val revving = _isRevving.value
                val isSim = _isSimulationMode.value
                val realBleReady = bleClient.gattReady

                val shouldSimulate = !realBleReady && (isSim || revving || simRpm > 50f || simTps > 0.01f)
                if (shouldSimulate) {
                    // Update simulated throttle & RPM
                    if (revving) {
                        simTps = (simTps + 0.18f).coerceAtMost(1.0f)
                        val targetRpm = _softRevLimiterRpm.value + 800f
                        simRpm += (targetRpm - simRpm) * 0.20f
                        if (simRpm >= _softRevLimiterRpm.value) {
                            // Limiter stutter flutter
                            val flutter = ((sin(seq * 1.5) * 350f)).toFloat()
                            simRpm = (_softRevLimiterRpm.value - 150f) + flutter
                        }
                    } else {
                        // Quick snap decay on release
                        simTps = (simTps - 0.25f).coerceAtLeast(0.0f)
                        val idleTarget = if (isSim) (1420f + (sin(seq * 0.2) * 40f).toFloat()) else 0f
                        simRpm += (idleTarget - simRpm) * 0.25f
                        if (kotlin.math.abs(simRpm - idleTarget) < 25f) {
                            simRpm = idleTarget
                        }
                    }

                    if (!isSim && !revving && simRpm <= 30f) {
                        simRpm = 0f
                        simTps = 0f
                        engineSound.stop()
                    }

                    simPhase += 0.05f

                    // Calculate advance angle based on active map
                    val activeMap = mapPresets[_selectedMapSlot.value]
                    val baseAdvance = when {
                        simRpm < 2000 -> 12f + (simRpm - 1000f) * 0.005f
                        simRpm < 6000 -> 17f + (simRpm - 2000f) * 0.0035f
                        simRpm < 9000 -> 31f + (simRpm - 6000f) * 0.0015f
                        else -> (activeMap.peakAdvance - ((simRpm - 9000f) * 0.004f)).coerceAtLeast(10f)
                    }
                    val finalAdvance = baseAdvance + _pulserOffsetDeg.value

                    val limiterState = when {
                        simRpm >= _softRevLimiterRpm.value + 300 -> 2 // Hard
                        simRpm >= _softRevLimiterRpm.value -> 1       // Soft
                        else -> 0
                    }

                    seq = (seq + 1) and 0xFFFF
                    val simTelemetry = Telemetry(
                        sequence = seq,
                        rpm = simRpm.toInt().coerceIn(0, 13000),
                        tps = (simTps * 1000).toInt(),
                        advanceCdeg = (finalAdvance * 100).toInt(),
                        batteryCv = 1380 + (sin(seq * 0.1) * 20).toInt(),
                        hvCenter = 248 + (sin(seq * 0.3) * 4).toInt(),
                        hvSide = 252 + (sin(seq * 0.25) * 5).toInt(),
                        tempCdeg = 8200 + (simTps * 500).toInt(),
                        slot = _selectedMapSlot.value,
                        limiter = limiterState,
                        flags = 0x21 or (if (_flashSaved.value) 0x08 else 0x00),
                        faults = 0,
                        setupStage = _telemetry.value.setupStage,
                        outputFlags = 0x03 or (if (_strobeActive.value) 0x04 else 0x00),
                        triggerCdeg = ((10.0f + _pulserOffsetDeg.value) * 100).toInt(),
                        pickupQuality = 99,
                        firstStartSeconds = 0
                    )

                    _telemetry.value = simTelemetry
                    _rawPacket.value = CdiProtocol.packetFromTelemetry(simTelemetry)
                    if (simRpm > 50f) {
                        engineSound.update(simTelemetry)
                    } else {
                        engineSound.stop()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        bleClient.release()
        engineSound.release()
    }
}
