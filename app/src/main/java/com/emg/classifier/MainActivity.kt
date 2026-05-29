package com.emg.classifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val TAG = "MainActivity"
private const val PERMISSION_REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect:    Button
    private lateinit var tvStatus:      TextView
    private lateinit var tvEMGValues:   TextView
    private lateinit var tvSampleCount: TextView
    private lateinit var tvPrediction:  TextView
    private lateinit var tvFrequency:   TextView
    private lateinit var layoutRoot:    LinearLayout
    private lateinit var bleManager:    BLEManager
    private lateinit var tfliteModel:   TFLiteModel
    private lateinit var vibrator:      Vibrator

    private val sampleBuffer     = mutableListOf<FloatArray>()
    private val validationBuffer = mutableListOf<FloatArray>()
    private var totalSamples     = 0
    private var strideCounter    = 0
    private var consecutiveRisk  = 0
    private var alertActive      = false
    private var freqWindowStart  = System.currentTimeMillis()
    private var freqSampleCount  = 0

    private val permissions: Array<String> get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.VIBRATE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.VIBRATE
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        layoutRoot    = findViewById(R.id.layoutRoot)
        btnConnect    = findViewById(R.id.btnConnect)
        tvStatus      = findViewById(R.id.tvStatus)
        tvEMGValues   = findViewById(R.id.tvEMGValues)
        tvSampleCount = findViewById(R.id.tvSampleCount)
        tvPrediction  = findViewById(R.id.tvPrediction)
        tvFrequency   = findViewById(R.id.tvFrequency)

        vibrator     = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        bleManager   = BLEManager(this)
        tfliteModel  = TFLiteModel(this)

        bleManager.onStatusChanged  = { msg -> tvStatus.text = msg }
        bleManager.onSampleReceived = { sample -> onNewSample(sample) }

        btnConnect.setOnClickListener {
            if (hasPermissions()) { resetState(); bleManager.startScan() }
            else requestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
        tfliteModel.close()
    }

    private fun onNewSample(sample: FloatArray) {
        totalSamples++; freqSampleCount++; strideCounter++
        sampleBuffer.add(sample)
        validationBuffer.add(sample)
        if (sampleBuffer.size > 200) sampleBuffer.removeAt(0)

        if (totalSamples % 5 == 0) {
            tvEMGValues.text   = "CH1: %.4f  CH2: %.4f  CH3: %.4f".format(sample[0], sample[1], sample[2])
            tvSampleCount.text = "Muestras: $totalSamples"
            updateFrequency()
        }
        if (validationBuffer.size >= 100) { logValidation(); validationBuffer.clear() }
        if (strideCounter >= Constants.STRIDE_SAMPLES && sampleBuffer.size >= Constants.WINDOW_SAMPLES) {
            strideCounter = 0
            runInference(sampleBuffer.takeLast(Constants.WINDOW_SAMPLES))
        }
    }

    private fun runInference(window: List<FloatArray>) {
        val raw = FeatureExtractor.extract(window)
        val features = FloatArray(Constants.N_FEATURES) { i ->
            (raw[i] - Constants.NORM_MU[i]) / Constants.NORM_SIGMA[i]
        }
        val (idx, probs) = tfliteModel.predict(features)
        val cls  = Constants.LABEL_CLASSES[idx]
        val name = Constants.LABEL_NAMES[cls] ?: "Clase $cls"
        val conf = probs[idx]
        Log.d(TAG, "Pred: $cls ($name) conf=${(conf*100).toInt()}%")
        updatePredictionUI(cls, name, conf)
        checkRiskAlert(idx)
    }

    private fun updatePredictionUI(clsId: Int, name: String, confidence: Float) {
        tvPrediction.text = "$name\n(${(confidence * 100).toInt()}%)"
        if (clsId == Constants.LABEL_CLASSES[Constants.RISK_CLASS_INDEX] && alertActive) {
            tvPrediction.setTextColor(Color.parseColor("#FF1744"))
            layoutRoot.setBackgroundColor(Color.parseColor("#1A0000"))
        } else {
            tvPrediction.setTextColor(Color.parseColor("#00E676"))
            layoutRoot.setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
    }

    private fun checkRiskAlert(idx: Int) {
        if (idx == Constants.RISK_CLASS_INDEX) consecutiveRisk++
        else { consecutiveRisk = 0; alertActive = false }

        if (consecutiveRisk == Constants.RISK_CONSECUTIVE && !alertActive) {
            alertActive = true
            triggerRiskAlert()
        }
    }

    private fun triggerRiskAlert() {
        tvStatus.text = "⚠ MOVIMIENTO DE RIESGO DETECTADO"
        tvStatus.setTextColor(Color.parseColor("#FF1744"))
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(300)
        Handler(Looper.getMainLooper()).postDelayed({
            tvStatus.setTextColor(Color.parseColor("#AAAAAA"))
            tvStatus.text = "Recibiendo EMG…"
        }, 2000)
    }

    private fun updateFrequency() {
        val now = System.currentTimeMillis()
        val elapsed = now - freqWindowStart
        if (elapsed >= 1000) {
            tvFrequency.text = "%.0f Hz".format(freqSampleCount * 1000f / elapsed)
            freqSampleCount = 0; freqWindowStart = now
        }
    }

    private fun logValidation() {
        for (ch in 0 until Constants.N_CHANNELS) {
            val vals = validationBuffer.map { it[ch] }
            Log.i(TAG, "VAL CH${ch+1}: min=%.5f max=%.5f mean=%.5f"
                .format(vals.minOrNull(), vals.maxOrNull(), vals.average().toFloat()))
        }
    }

    private fun resetState() {
        sampleBuffer.clear(); validationBuffer.clear()
        totalSamples = 0; strideCounter = 0; consecutiveRisk = 0; alertActive = false
        freqSampleCount = 0; freqWindowStart = System.currentTimeMillis()
        tvPrediction.text = "—"; tvEMGValues.text = "CH1: —  CH2: —  CH3: —"
        tvSampleCount.text = "Muestras: 0"; tvFrequency.text = "— Hz"
        tvStatus.setTextColor(Color.parseColor("#AAAAAA"))
        tvPrediction.setTextColor(Color.parseColor("#00E676"))
        layoutRoot.setBackgroundColor(Color.parseColor("#0A0A0A"))
    }

    private fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() =
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == PERMISSION_REQUEST_CODE) {
            if (results.all { it == PackageManager.PERMISSION_GRANTED }) { resetState(); bleManager.startScan() }
            else Toast.makeText(this, "Permisos denegados", Toast.LENGTH_LONG).show()
        }
    }
}