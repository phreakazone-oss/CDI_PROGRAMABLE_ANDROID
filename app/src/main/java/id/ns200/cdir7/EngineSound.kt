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
        SINGLE("NS200 Single", intArrayOf(R.raw.single_1200, R.raw.single_4000, R.raw.single_8000)),
        TWIN270("Twin 270°", intArrayOf(R.raw.twin270_1200, R.raw.twin270_4000, R.raw.twin270_8000)),
        INLINE3("Inline-3", intArrayOf(R.raw.inline3_1200, R.raw.inline3_4000, R.raw.inline3_8000)),
        INLINE4("Inline-4 Flat", intArrayOf(R.raw.inline4_1200, R.raw.inline4_4000, R.raw.inline4_8000)),
        CROSS4("Inline-4 Crossplane", intArrayOf(R.raw.cross4_1200, R.raw.cross4_4000, R.raw.cross4_8000)),
        V4("V4", intArrayOf(R.raw.v4_1200, R.raw.v4_4000, R.raw.v4_8000)),
        CUSTOM("Manual dari file", intArrayOf())
    }

    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    ).build()
    private val soundIds = mutableMapOf<Preset, IntArray>()
    private var preset = Preset.SINGLE
    private var streams = intArrayOf(0, 0, 0)
    private var customUri: Uri? = null
    private var customPlayer: MediaPlayer? = null
    private var customBaseRpm = 2000
    var enabled = false
        set(value) { field = value; if (!value) stop() }

    init {
        Preset.entries.filter { it.anchors.size == 3 }.forEach { p ->
            soundIds[p] = IntArray(3) { i -> pool.load(context, p.anchors[i], 1) }
        }
    }

    fun setCustom(uri: Uri?, baseRpm: Int = customBaseRpm) {
        stop()
        customUri = uri
        customBaseRpm = baseRpm.coerceIn(600, 8000)
        preset = Preset.CUSTOM
    }

    fun setCustomBaseRpm(value: Int) { customBaseRpm = value.coerceIn(600, 8000) }

    fun select(value: Preset) {
        if (value == preset) return
        stop(); preset = value
    }

    private fun startIfNeeded() {
        if (preset == Preset.CUSTOM) {
            if (customPlayer != null) return
            val uri = customUri ?: return
            customPlayer = MediaPlayer.create(context, uri)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
            return
        }
        if (streams.any { it != 0 }) return
        val ids = soundIds[preset] ?: return
        streams = IntArray(3) { i -> pool.play(ids[i], 0f, 0f, 1, -1, 1f) }
    }

    fun update(t: Telemetry) {
        if (!enabled) return
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
                val volume = (load * limiterGain).coerceIn(0f, 1f)
                player.setVolume(volume, volume)
                try {
                    player.playbackParams = PlaybackParams()
                        .setSpeed((rpm / customBaseRpm).coerceIn(.5f, 2f))
                        .setPitch(1f)
                } catch (_: IllegalArgumentException) { /* codec tertentu tidak mendukung rate */ }
            }
            return
        }
        val low = (1f - ((rpm - 1200f) / 2800f)).coerceIn(0f, 1f)
        val high = ((rpm - 4000f) / 4000f).coerceIn(0f, 1f)
        val mid = if (rpm < 4000f) 1f - low else 1f - high
        val weights = floatArrayOf(low, mid.coerceIn(0f, 1f), high)
        val anchors = floatArrayOf(1200f, 4000f, 8000f)
        streams.forEachIndexed { i, stream ->
            if (stream != 0) {
                val volume = (weights[i] * load * limiterGain).coerceIn(0f, 1f)
                pool.setVolume(stream, volume, volume)
                pool.setRate(stream, (rpm / anchors[i]).coerceIn(.5f, 2f))
            }
        }
    }

    fun stop() {
        streams.forEach { if (it != 0) pool.stop(it) }
        streams = intArrayOf(0, 0, 0)
        customPlayer?.release()
        customPlayer = null
    }

    fun release() { stop(); pool.release() }
}
