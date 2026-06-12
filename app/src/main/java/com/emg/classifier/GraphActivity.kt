package com.emg.classifier

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

private const val MAX_VISIBLE_POINTS = 300
private const val UPDATE_EVERY = 5

class GraphActivity : AppCompatActivity() {

    private lateinit var chartCh1: LineChart
    private lateinit var chartCh2: LineChart
    private lateinit var chartCh3: LineChart
    private lateinit var tvGraphPred: TextView

    private var sampleCounter = 0

    private val sampleListener: (FloatArray) -> Unit = { sample -> onNewSample(sample) }
    private val predListener: (String) -> Unit = { text -> onNewPrediction(text) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)
        supportActionBar?.hide()

        chartCh1 = findViewById(R.id.chartCh1)
        chartCh2 = findViewById(R.id.chartCh2)
        chartCh3 = findViewById(R.id.chartCh3)
        tvGraphPred = findViewById(R.id.tvGraphPrediction)

        setupChart(chartCh1, "CH 1", Color.parseColor("#1E3A8A"))
        setupChart(chartCh2, "CH 2", Color.parseColor("#7B5EA7"))
        setupChart(chartCh3, "CH 3", Color.parseColor("#0D9488"))

        val snapshot = EMGDataRepository.getBufferSnapshot()
        for (sample in snapshot) addEntries(sample)
        listOf(chartCh1, chartCh2, chartCh3).forEach { it.invalidate() }

        tvGraphPred.text = EMGDataRepository.lastPrediction

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        EMGDataRepository.subscribeSamples(sampleListener)
        EMGDataRepository.subscribePrediction(predListener)
    }

    override fun onPause() {
        super.onPause()
        EMGDataRepository.unsubscribeSamples(sampleListener)
        EMGDataRepository.unsubscribePrediction(predListener)
    }

    private fun onNewSample(sample: FloatArray) {
        sampleCounter++
        addEntries(sample)
        if (sampleCounter % UPDATE_EVERY == 0) {
            refreshChart(chartCh1)
            refreshChart(chartCh2)
            refreshChart(chartCh3)
        }
    }

    private fun onNewPrediction(text: String) {
        tvGraphPred.text = text
    }

    private fun addEntries(sample: FloatArray) {
        val x = sampleCounter.toFloat()
        addToChart(chartCh1, x, sample[0])
        addToChart(chartCh2, x, sample[1])
        addToChart(chartCh3, x, sample[2])
    }

    private fun addToChart(chart: LineChart, x: Float, y: Float) {
        val data = chart.data ?: return
        val set = data.getDataSetByIndex(0) as? LineDataSet ?: return
        set.addEntry(Entry(x, y.coerceIn(0f, 3.3f)))
        if (set.entryCount > MAX_VISIBLE_POINTS) {
            set.removeEntry(set.getEntryForIndex(0))
        }
        data.notifyDataChanged()
    }

    private fun refreshChart(chart: LineChart) {
        chart.data?.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun setupChart(chart: LineChart, label: String, lineColor: Int) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.parseColor("#F0F4FF"))
            xAxis.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 3.3f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#D1D9E8")
                textColor = Color.parseColor("#4A5568")
                textSize = 9f
                labelCount = 4
                removeAllLimitLines()
                addLimitLine(LimitLine(1.65f, "").apply {
                    lineWidth = 0.5f
                    enableDashedLine(8f, 4f, 0f)
                })
            }
            axisRight.isEnabled = false
            extraLeftOffset = 4f
            extraTopOffset = 4f
        }

        val set = LineDataSet(mutableListOf(), label).apply {
            color = lineColor
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            axisDependency = YAxis.AxisDependency.LEFT
        }

        chart.data = LineData(set)
    }
}