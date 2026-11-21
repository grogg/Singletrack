package com.example.data.bluetooth

import kotlin.math.roundToInt

data class CscParserState(
    val lastWheelRevs: Long? = null,
    val lastWheelEventTimeTicks: Int? = null,
    val lastCrankRevs: Int? = null,
    val lastCrankEventTimeTicks: Int? = null
)

data class CscParsedPacket(
    val wheelDataPresent: Boolean,
    val crankDataPresent: Boolean,
    val speedMps: Double?,
    val incrementalDistanceMeters: Double,
    val cadenceRpm: Int?,
    val nextState: CscParserState
)

/**
 * Pure Kotlin parser for Bluetooth SIG Cycling Speed & Cadence Measurement (0x2A5B).
 */
object CscMeasurementParser {

    fun parse(
        bytes: ByteArray,
        previousState: CscParserState,
        wheelCircumferenceMm: Int
    ): CscParsedPacket? {
        if (bytes.isEmpty()) return null

        val flags = bytes[0].toInt() and 0xFF
        val wheelPresent = (flags and 0x01) != 0
        val crankPresent = (flags and 0x02) != 0

        var offset = 1
        var nextWheelRevs = previousState.lastWheelRevs
        var nextWheelTime = previousState.lastWheelEventTimeTicks
        var nextCrankRevs = previousState.lastCrankRevs
        var nextCrankTime = previousState.lastCrankEventTimeTicks

        var speedMps: Double? = null
        var deltaDistanceMeters = 0.0
        var cadenceRpm: Int? = null

        if (wheelPresent) {
            if (offset + 6 > bytes.size) return null
            val cumulativeWheelRevs = readUInt32LE(bytes, offset)
            val wheelEventTimeTicks = readUInt16LE(bytes, offset + 4)
            offset += 6

            val prevRevs = previousState.lastWheelRevs
            val prevTime = previousState.lastWheelEventTimeTicks
            if (prevRevs != null && prevTime != null) {
                val deltaRevs = (cumulativeWheelRevs - prevRevs) and 0xFFFFFFFFL
                val deltaTicks = (wheelEventTimeTicks - prevTime) and 0xFFFF
                if (deltaTicks > 0 && deltaRevs in 1L..5000L) {
                    val deltaSeconds = deltaTicks / 1024.0
                    val meters = deltaRevs * (wheelCircumferenceMm / 1000.0)
                    val computedSpeed = meters / deltaSeconds
                    if (computedSpeed in 0.1..40.0) {
                        speedMps = computedSpeed
                        deltaDistanceMeters = meters
                    }
                } else if (deltaTicks == 0 && deltaRevs == 0L) {
                    speedMps = 0.0
                }
            }
            nextWheelRevs = cumulativeWheelRevs
            nextWheelTime = wheelEventTimeTicks
        }

        if (crankPresent) {
            if (offset + 4 > bytes.size) return null
            val cumulativeCrankRevs = readUInt16LE(bytes, offset)
            val crankEventTimeTicks = readUInt16LE(bytes, offset + 2)

            val prevCranks = previousState.lastCrankRevs
            val prevTime = previousState.lastCrankEventTimeTicks
            if (prevCranks != null && prevTime != null) {
                val deltaCranks = (cumulativeCrankRevs - prevCranks) and 0xFFFF
                val deltaTicks = (crankEventTimeTicks - prevTime) and 0xFFFF
                if (deltaTicks > 0 && deltaCranks in 1..200) {
                    val deltaSeconds = deltaTicks / 1024.0
                    val rpm = ((deltaCranks / deltaSeconds) * 60.0).roundToInt()
                    if (rpm in 1..240) {
                        cadenceRpm = rpm
                    }
                } else if (deltaTicks == 0 && deltaCranks == 0) {
                    cadenceRpm = 0
                }
            }
            nextCrankRevs = cumulativeCrankRevs
            nextCrankTime = crankEventTimeTicks
        }

        return CscParsedPacket(
            wheelDataPresent = wheelPresent,
            crankDataPresent = crankPresent,
            speedMps = speedMps,
            incrementalDistanceMeters = deltaDistanceMeters,
            cadenceRpm = cadenceRpm,
            nextState = CscParserState(
                lastWheelRevs = nextWheelRevs,
                lastWheelEventTimeTicks = nextWheelTime,
                lastCrankRevs = nextCrankRevs,
                lastCrankEventTimeTicks = nextCrankTime
            )
        )
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        val b0 = (bytes[offset].toLong() and 0xFFL)
        val b1 = (bytes[offset + 1].toLong() and 0xFFL) shl 8
        val b2 = (bytes[offset + 2].toLong() and 0xFFL) shl 16
        val b3 = (bytes[offset + 3].toLong() and 0xFFL) shl 24
        return b0 or b1 or b2 or b3
    }
}
