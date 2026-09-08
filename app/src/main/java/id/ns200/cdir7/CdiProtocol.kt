package id.ns200.cdir7

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class SetupStage(val code: Int, val label: String, val desc: String) {
    BARU(0, "BARU", "Cek catu daya & BLE; starter dengan JP_HV lepas. HV <30V. Charger & koil OFF"),
    PULSER(1, "PULSER", "Uji input pulser J1.10; PPR=1; gate 80µs; quality >=10"),
    TDC(2, "TDC", "Strobo PB9; sejajarkan tanda 'T'; SAVE TDC ke flash"),
    TPS_CAL(3, "TPS", "Simpan gas tertutup (0%) dan terbuka penuh (100%)"),
    FIRST_START(4, "FIRST START", "Mode aman 220V, CENTER saja, advance <=10°, limiter 3.000 RPM"),
    READY(5, "READY", "Hidup stabil >=3 detik, simpan CENTER; boot berikutnya langsung pakai map")
}

data class Telemetry(
    val sequence: Int, val rpm: Int, val tps: Int, val advanceCdeg: Int,
    val batteryCv: Int, val hvCenter: Int, val hvSide: Int, val tempCdeg: Int,
    val slot: Int, val limiter: Int, val flags: Int, val faults: Int,
    val setupStage: Int, val outputFlags: Int, val triggerCdeg: Int,
    val pickupQuality: Int, val firstStartSeconds: Int
) {
    val armed get() = flags and 0x01 != 0
    val proJumper get() = flags and 0x02 != 0
    val hvEnabled get() = flags and 0x04 != 0
    val calibrated get() = flags and 0x08 != 0
    val sideCalibrated get() = flags and 0x10 != 0
    val ready get() = flags and 0x20 != 0
    val centerEnabled get() = outputFlags and 1 != 0
    val sideEnabled get() = outputFlags and 2 != 0
    val strobeEnabled get() = outputFlags and 4 != 0
    val fanEnabled get() = outputFlags and 8 != 0
    val stage: SetupStage
        get() = SetupStage.entries.find { it.code == setupStage } ?: SetupStage.BARU
    val isHvOver300 get() = hvCenter >= 300 || hvSide >= 300
    val isHvOver345Warning get() = hvCenter >= 345 || hvSide >= 345
}

object CdiProtocol {
    const val SERVICE = "7a8f1000-6c9d-4e40-a45f-0b4b4e533230"
    const val TELEMETRY = "7a8f1001-6c9d-4e40-a45f-0b4b4e533230"
    const val COMMAND = "7a8f1002-6c9d-4e40-a45f-0b4b4e533230"
    const val RESPONSE = "7a8f1003-6c9d-4e40-a45f-0b4b4e533230"
    const val BLE_PIN = "123456"

    fun crc16(data: ByteArray, length: Int = data.size): Int {
        var crc = 0xffff
        repeat(length) { i ->
            crc = crc xor ((data[i].toInt() and 0xff) shl 8)
            repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff }
        }
        return crc
    }

    fun command(sequence: Int, body: String): ByteArray {
        val payload = "$sequence,$body"
        val crc = crc16(payload.toByteArray(Charsets.US_ASCII))
        return "@$payload*%04X\n".format(crc).toByteArray(Charsets.US_ASCII)
    }

    fun telemetry(packet: ByteArray): Telemetry? {
        if (packet.size != 32 || (packet[0].toInt() and 0xff) != 0x15 ||
            (packet[1].toInt() and 0xff) != 0xcd || packet[2].toInt() != 2 ||
            crc16(packet, 30) != ((packet[30].toInt() and 0xff) or
                    ((packet[31].toInt() and 0xff) shl 8))) return null
        val b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        b.short; b.get()
        val flags = b.get().toInt() and 0xff
        return Telemetry(
            b.short.toInt() and 0xffff, b.short.toInt() and 0xffff,
            b.short.toInt() and 0xffff, b.short.toInt(),
            b.short.toInt() and 0xffff, b.short.toInt() and 0xffff,
            b.short.toInt() and 0xffff, b.short.toInt(),
            b.get().toInt() and 0xff, b.get().toInt() and 0xff,
            flags, b.short.toInt() and 0xffff, b.get().toInt() and 0xff,
            b.get().toInt() and 0xff, b.short.toInt() and 0xffff,
            b.get().toInt() and 0xff, b.get().toInt() and 0xff
        )
    }

    fun packetFromTelemetry(t: Telemetry): ByteArray {
        val b = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0x15.toByte())
        b.put(0xcd.toByte())
        b.put(2.toByte())
        b.put(t.flags.toByte())
        b.putShort(t.sequence.toShort())
        b.putShort(t.rpm.toShort())
        b.putShort(t.tps.toShort())
        b.putShort(t.advanceCdeg.toShort())
        b.putShort(t.batteryCv.toShort())
        b.putShort(t.hvCenter.toShort())
        b.putShort(t.hvSide.toShort())
        b.putShort(t.tempCdeg.toShort())
        b.put(t.slot.toByte())
        b.put(t.limiter.toByte())
        b.putShort(t.faults.toShort())
        b.put(t.setupStage.toByte())
        b.put(t.outputFlags.toByte())
        b.putShort(t.triggerCdeg.toShort())
        b.put(t.pickupQuality.toByte())
        b.put(t.firstStartSeconds.toByte())
        val raw = b.array()
        val crc = crc16(raw, 30)
        raw[30] = (crc and 0xff).toByte()
        raw[31] = ((crc shr 8) and 0xff).toByte()
        return raw
    }

    fun toHexDump(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}
