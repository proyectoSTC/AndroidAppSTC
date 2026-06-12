package com.emg.classifier

import java.util.concurrent.CopyOnWriteArrayList

object EMGDataRepository {

    private const val BUFFER_SIZE = 500   // 5 s a 100 Hz

    private val buffer = ArrayDeque<FloatArray>()

    var lastPrediction: String = "—"
        private set

    private val sampleListeners     = CopyOnWriteArrayList<(FloatArray) -> Unit>()
    private val predictionListeners = CopyOnWriteArrayList<(String)    -> Unit>()

    // ── Productores ────────────────────────────────────────────────────────────

    fun pushSample(sample: FloatArray) {
        if (buffer.size >= BUFFER_SIZE) buffer.removeFirst()
        buffer.addLast(sample.copyOf())
        sampleListeners.forEach { it(sample) }
    }

    fun pushPrediction(text: String) {
        lastPrediction = text
        predictionListeners.forEach { it(text) }
    }

    // ── Consumidores ───────────────────────────────────────────────────────────

    fun subscribeSamples(listener: (FloatArray) -> Unit) {
        sampleListeners.add(listener)
    }

    fun unsubscribeSamples(listener: (FloatArray) -> Unit) {
        sampleListeners.remove(listener)
    }

    fun subscribePrediction(listener: (String) -> Unit) {
        predictionListeners.add(listener)
    }

    fun unsubscribePrediction(listener: (String) -> Unit) {
        predictionListeners.remove(listener)
    }

    fun getBufferSnapshot(): List<FloatArray> = buffer.toList()
}