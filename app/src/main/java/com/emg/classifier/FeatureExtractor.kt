package com.emg.classifier

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * FeatureExtractor
 *
 * Extracts features EXACTLY matching the Python training pipeline:
 *   Per channel: RMS, MAV, ZeroCrossing, WaveformLength
 *   3 channels × 4 features = 12 features total
 */
object FeatureExtractor {

    /**
     * @param window List of samples, each FloatArray(3) → [ch1, ch2, ch3]
     *               window.size must be == WINDOW_SAMPLES (20)
     * @return FloatArray(12) feature vector
     */
    fun extract(window: List<FloatArray>): FloatArray {
        require(window.size == Constants.WINDOW_SAMPLES) {
            "Window must have exactly ${Constants.WINDOW_SAMPLES} samples, got ${window.size}"
        }

        val features = FloatArray(Constants.N_FEATURES)
        var featIdx = 0

        for (ch in 0 until Constants.N_CHANNELS) {
            val signal = FloatArray(window.size) { window[it][ch] }

            features[featIdx++] = rms(signal)
            features[featIdx++] = mav(signal)
            features[featIdx++] = zeroCrossing(signal)
            features[featIdx++] = waveformLength(signal)
        }

        return features
    }

    // ── Feature functions — mirror of Python train_emg.py ─────────────────────

    private fun rms(w: FloatArray): Float {
        var sum = 0f
        for (v in w) sum += v * v
        return sqrt(sum / w.size)
    }

    private fun mav(w: FloatArray): Float {
        var sum = 0f
        for (v in w) sum += abs(v)
        return sum / w.size
    }

    private fun zeroCrossing(w: FloatArray): Float {
        var count = 0
        val thr = Constants.ZC_THRESHOLD
        for (i in 0 until w.size - 1) {
            val signChange = (w[i] >= 0f) != (w[i + 1] >= 0f)
            val ampOk = abs(w[i]) + abs(w[i + 1]) > thr
            if (signChange && ampOk) count++
        }
        return count.toFloat()
    }

    private fun waveformLength(w: FloatArray): Float {
        var sum = 0f
        for (i in 0 until w.size - 1) sum += abs(w[i + 1] - w[i])
        return sum
    }
}
