package com.emg.classifier

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLiteModel
 *
 * Loads model.tflite from assets and runs inference.
 * Input  : Float32[1, 12]
 * Output : Float32[1, N_CLASSES]  (softmax probabilities)
 */
class TFLiteModel(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        interpreter = Interpreter(model, options)
        Log.d("TFLiteModel", "Model loaded. Input: ${interpreter.getInputTensor(0).shape().toList()}")
    }

    /**
     * @param features FloatArray(12) — normalized feature vector
     * @return Pair(predictedLabelIndex, confidenceArray)
     */
    fun predict(features: FloatArray): Pair<Int, FloatArray> {
        require(features.size == Constants.N_FEATURES) {
            "Expected ${Constants.N_FEATURES} features, got ${features.size}"
        }

        val input  = Array(1) { features }
        val nClasses = Constants.LABEL_CLASSES.size
        val output = Array(1) { FloatArray(nClasses) }

        interpreter.run(input, output)

        val probs = output[0]
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0

        return Pair(maxIdx, probs)
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFd = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }
}
