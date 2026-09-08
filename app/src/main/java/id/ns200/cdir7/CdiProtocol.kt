package id.ns200.cdir7

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    val ready get() = flags and 0x20 != 0
    val centerEnabled get() = outputFlags and 1 != 0
    val sideEnabled get() = outputFlags and 2 != 0
    val strobeEnabled get() = outputFlags and 4 != 0
    val fanEnabled get() = outputFlags and 8 != 0
}

object CdiProtocol {
    const val SERVICE = "7a8f1000-6c9d-4e40-a45f-0b4b4e533230"
    const val TELEMETRY = "7a8f1001-6c9d-4e40-a45f-0b4b4e533230"
    const val COMMAND = "7a8f1002-6c9d-4e40-a45f-0b4b4e533230"
    const val RESPONSE = "7a8f1003-6c9d-4e40-a45f-0b4b4e533230"

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
}
