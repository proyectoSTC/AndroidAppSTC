package com.emg.classifier

import android.util.Log

/**
 * EMGParser
 *
 * Accumulates raw BLE bytes (notifications may arrive as any number of bytes)
 * and emits complete EMG samples (3 × float32) one at a time.
 *
 * Packet format: [ch1_L, ch1_H, ch2_L, ch2_H, ch3_L, ch3_H] — little-endian int16
 * Scaling      : int16 / 10000f → volts
 */
class EMGParser {

    private val byteBuffer = mutableListOf<Byte>()

    /** Call this for every BLE notification payload. Returns list of decoded samples. */
    fun feed(data: ByteArray): List<FloatArray> {
        // Accumulate incoming bytes
        for (b in data) byteBuffer.add(b)

        val samples = mutableListOf<FloatArray>()

        // Drain complete 6-byte packets
        while (byteBuffer.size >= 6) {
            val b0 = byteBuffer[0].toInt() and 0xFF
            val b1 = byteBuffer[1].toInt()          // sign-extended
            val b2 = byteBuffer[2].toInt() and 0xFF
            val b3 = byteBuffer[3].toInt()
            val b4 = byteBuffer[4].toInt() and 0xFF
            val b5 = byteBuffer[5].toInt()

            val ch1Raw = (b1 shl 8) or b0   // signed int16
            val ch2Raw = (b3 shl 8) or b2
            val ch3Raw = (b5 shl 8) or b4

            val ch1 = ch1Raw.toShort() / Constants.SCALE_FACTOR
            val ch2 = ch2Raw.toShort() / Constants.SCALE_FACTOR
            val ch3 = ch3Raw.toShort() / Constants.SCALE_FACTOR

            samples.add(floatArrayOf(ch1, ch2, ch3))

            // Remove consumed bytes
            repeat(6) { byteBuffer.removeAt(0) }
        }

        return samples
    }

    fun reset() {
        byteBuffer.clear()
        Log.d("EMGParser", "Buffer reset")
    }
}
