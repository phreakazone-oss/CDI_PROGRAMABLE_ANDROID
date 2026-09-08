package id.ns200.cdir7

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.media.SoundPool
import kotlin.math.max

class EngineSound(private val context: Context) {
    enum class Preset(val label: String, val anchors: IntArray) {
        SINGLE("Single 200 DTS-i", intArrayOf(R.raw.single_1200, R.raw.single_4000, R.raw.single_8000)),
        TWIN270("Twin 270°", intArrayOf(R.raw.twin270_1200, R.raw.twin270_4000, R.raw.twin270_8000)),
        INLINE3("Inline-3", intArrayOf(R.raw.inline3_1200, R.raw.inline3_4000, R.raw.inline3_8000)),
        INLINE4("Inline-4 Screamer", intArrayOf(R.raw.inline4_1200, R.raw.inline4_4000, R.raw.inline4_8000)),
        CROSS4("Crossplane 4", intArrayOf(R.raw.cross4_1200, R.raw.cross4_4000, R.raw.cross4_8000)),
        V4("V4 MotoGP", intArrayOf(R.raw.v4_1200, R.raw.v4_4000, R.raw.v4_8000)),
        CUSTOM("Manual Custom File", intArrayOf())
    }

    private var pool: SoundPool? = null
    private var loadedPreset: Preset? = null
    private var activeSoundIds: IntArray? = null
    private var loadedCount = 0
    var preset = Preset.SINGLE
        private set
    private var streams = intArrayOf(0, 0, 0)
    private var customUri: Uri? = null
    private var customPlayer: MediaPlayer? = null
    private var customBaseRpm = 2000
    var masterVolume = 0.85f
        set(value) { field = value.coerceIn(0f, 1f) }
    var enabled = false
        set(value) {
            field = value
            if (!value) {
                stop()
            } else {
                ensurePresetLoaded(preset)
            }
        }

    private fun getOrCreatePool(): SoundPool {
        return pool ?: SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        ).build().also { p ->
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    val ids = activeSoundIds
                    if (ids != null && ids.contains(sampleId)) {
                        loadedCount++
                        if (enabled && loadedCount == 3 && streams.all { it == 0 }) {
                            streams = IntArray(3) { i -> p.play(ids[i], 0f, 0f, 1, -1, 1f) }
                        }
                    }
                }
            }
            pool = p
        }
    }

    private fun ensurePresetLoaded(p: Preset) {
        if (p == Preset.CUSTOM || p.anchors.size != 3) {
            unloadActivePreset()
            return
        }
        if (loadedPreset == p && activeSoundIds != null) return

        unloadActivePreset()
        val pPool = getOrCreatePool()
        loadedCount = 0
        loadedPreset = p
        activeSoundIds = IntArray(3) { i -> pPool.load(context, p.anchors[i], 1) }
    }

    private fun unloadActivePreset() {
        activeSoundIds?.forEach { id ->
            try { pool?.unload(id) } catch (_: Exception) {}
        }
        activeSoundIds = null
        loadedPreset = null
        loadedCount = 0
    }

    fun setCustom(uri: Uri?, baseRpm: Int = customBaseRpm) {
        stop()
        customUri = uri
        customBaseRpm = baseRpm.coerceIn(600, 8000)
        preset = Preset.CUSTOM
        unloadActivePreset()
    }

    fun setCustomBaseRpm(value: Int) { customBaseRpm = value.coerceIn(600, 8000) }

    fun select(value: Preset) {
        if (value == preset) return
        stop()
        preset = value
        if (enabled) {
            ensurePresetLoaded(preset)
        }
    }

    private var lastPlayAttemptMs = 0L

    private fun startIfNeeded() {
        if (preset == Preset.CUSTOM) {
            if (customPlayer != null) return
            val uri = customUri ?: return
            try {
                customPlayer = MediaPlayer.create(context, uri)?.apply {
                    isLooping = true
                    setVolume(0f, 0f)
                    start()
                }
            } catch (_: Exception) {}
            return
        }
        ensurePresetLoaded(preset)
        if (streams.any { it != 0 }) return
        val p = pool ?: return
        val ids = activeSoundIds ?: return

        val now = System.currentTimeMillis()
        if (now - lastPlayAttemptMs < 1000L) return
        lastPlayAttemptMs = now

        if (loadedCount >= 3) {
            try {
                streams = IntArray(3) { i -> p.play(ids[i], 0f, 0f, 1, -1, 1f) }
            } catch (_: Exception) {}
        }
    }

    fun update(t: Telemetry) {
        if (!enabled) return
        try {
            startIfNeeded()
            val rpm = max(600, t.rpm).toFloat()
            val load = .18f + .82f * (t.tps / 1000f)
            val limiterGain = when (t.limiter) {
                2 -> if (t.sequence and 1 == 0) 0f else .12f
                1 -> if (t.sequence % 4 == 0) .18f else 1f
                else -> 1f
            }
            if (preset == Preset.CUSTOM) {
                customPlayer?.let { player ->
                    val volume = (load * limiterGain * masterVolume).coerceIn(0f, 1f)
                    try {
                        player.setVolume(volume, volume)
                        player.playbackParams = PlaybackParams()
                            .setSpeed((rpm / customBaseRpm).coerceIn(.5f, 2f))
                            .setPitch(1f)
                    } catch (_: Exception) { /* codec/params exception */ }
                }
                return
            }
            val p = pool ?: return
            val low = (1f - ((rpm - 1200f) / 2800f)).coerceIn(0f, 1f)
            val high = ((rpm - 4000f) / 4000f).coerceIn(0f, 1f)
            val mid = if (rpm < 4000f) 1f - low else 1f - high
            val weights = floatArrayOf(low, mid.coerceIn(0f, 1f), high)
            val anchors = floatArrayOf(1200f, 4000f, 8000f)
            streams.forEachIndexed { i, stream ->
                if (stream != 0) {
                    try {
                        val volume = (weights[i] * load * limiterGain * masterVolume).coerceIn(0f, 1f)
                        p.setVolume(stream, volume, volume)
                        p.setRate(stream, (rpm / anchors[i]).coerceIn(.5f, 2f))
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            // Protect coroutine loop from audio hardware failure
        }
    }

    fun stop() {
        val p = pool
        if (p != null) {
            streams.forEach { if (it != 0) p.stop(it) }
        }
        streams = intArrayOf(0, 0, 0)
        customPlayer?.release()
        customPlayer = null
    }

    fun release() {
        stop()
        unloadActivePreset()
        pool?.release()
        pool = null
    }
}
