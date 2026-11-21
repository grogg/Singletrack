package com.example.data.bluetooth

data class HrParsedPacket(
    val heartRateBpm: Int,
    val contactSupported: Boolean,
    val contactDetected: Boolean
)

/**
 * Pure Kotlin parser for Bluetooth SIG Heart Rate Measurement characteristic (0x2A37).
 */
object HrMeasurementParser {

    fun parse(bytes: ByteArray): HrParsedPacket? {
        if (bytes.size < 2) return null

        val flags = bytes[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0
        val contactBits = (flags shr 1) and 0x03
        val contactSupported = contactBits == 2 || contactBits == 3
        val contactDetected = contactBits == 3

        val bpm = if (isUint16) {
            if (bytes.size < 3) return null
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            bytes[1].toInt() and 0xFF
        }

        if (bpm !in 25..250) return null

        return HrParsedPacket(
            heartRateBpm = bpm,
            contactSupported = contactSupported,
            contactDetected = contactDetected
        )
    }
}
