package com.example.bookgenie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

class BarVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var waveform: ByteArray? = null
    private val barPaint = Paint()
    private var numBars = 64 // Gösterilecek çizgi sayısı, artırılabilir
    private var slotWidth = 0f // Her çizgi için ayrılan toplam genişlik (çizgi + boşluk)
    private var actualLineThicknessPx = 0f // Çizginin gerçek kalınlığı (piksel cinsinden)
    private val lineThicknessDp = 2f // Çizgi kalınlığını dp olarak ayarla (örneğin 2dp)

    private val minBarHeight = 2f // Çok düşük sesler için minimum yükseklik
    private val TAG = "BarVisualizerView"

    private var startColor: Int = ContextCompat.getColor(context, R.color.mystic_blue_start)
    private var midColor: Int = ContextCompat.getColor(context, R.color.mystic_blue_end)
    private var endColor: Int = ContextCompat.getColor(context, R.color.button_magic_gradient_end)

    init {
        barPaint.style = Paint.Style.FILL
        actualLineThicknessPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            lineThicknessDp,
            resources.displayMetrics
        )
    }

    fun updateVisualizer(waveformBytes: ByteArray?) {
        waveform = waveformBytes?.clone()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (numBars > 0) {
            slotWidth = w.toFloat() / numBars
        }
        Log.d(TAG, "onSizeChanged: w=$w, h=$h, slotWidth=$slotWidth, actualLineThicknessPx=$actualLineThicknessPx")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0 || numBars <= 0 || actualLineThicknessPx <= 0) {
            return
        }

        // Waveform null ise veya boşsa hiçbir şey çizme (temiz bir görünüm)
        if (waveform == null || waveform!!.isEmpty()) {
            // canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // İsteğe bağlı: Canvas'ı tamamen temizle
            return
        }

        val currentWaveform = waveform ?: return // Null check (yukarıda zaten yapıldı ama tekrar)
        val centerY = height / 2f
        var currentSlotX = 0f // Her slotun başlangıç X pozisyonu

        for (i in 0 until numBars) {
            var sum = 0
            val dataPointsPerBar = currentWaveform.size / numBars.coerceAtLeast(1)
            val startIndex = i * dataPointsPerBar
            val endIndex = (i + 1) * dataPointsPerBar

            if (dataPointsPerBar > 0) {
                for (j in startIndex until endIndex) {
                    if (j < currentWaveform.size) {
                        sum += Math.abs(currentWaveform[j].toInt())
                    }
                }
            } else {
                if (i < currentWaveform.size) {
                    sum = Math.abs(currentWaveform[i].toInt())
                }
            }

            val amplitude = if (dataPointsPerBar > 0) sum.toFloat() / dataPointsPerBar else sum.toFloat()
            val maxAmplitude = 128f

            var barHeightPercentage = amplitude / maxAmplitude
            barHeightPercentage = Math.min(1.0f, Math.max(0.0f, barHeightPercentage))

            var totalBarHeight: Float
            // Eğer ses genliği çok düşükse (neredeyse sıfırsa), bar yüksekliğini sıfır yap
            if (barHeightPercentage < 0.01f) { // Bu eşik değeri ayarlanabilir (örn: 0.005f)
                totalBarHeight = 0f
            } else {
                // Ses varsa, yüksekliği hesapla ve minimum yüksekliği uygula
                totalBarHeight = (height * barHeightPercentage * 0.9f) // %90'ını alarak kenarlarda boşluk bırak
                totalBarHeight = Math.max(minBarHeight * 2, totalBarHeight)
            }

            // Çizgiyi slot içinde ortala
            val lineLeft = currentSlotX + (slotWidth - actualLineThicknessPx) / 2f
            val lineRight = lineLeft + actualLineThicknessPx

            // Sadece yüksekliği olan barları çiz
            if (totalBarHeight > 0f && lineLeft < lineRight) {
                val barTop = centerY - (totalBarHeight / 2f)
                val barBottom = centerY + (totalBarHeight / 2f)

                val fraction = i.toFloat() / (numBars - 1).coerceAtLeast(1).toFloat()
                barPaint.color = interpolateColor(startColor, midColor, endColor, fraction)

                val rect = RectF(lineLeft, barTop, lineRight, barBottom)
                canvas.drawRect(rect, barPaint)
            }

            currentSlotX += slotWidth // Bir sonraki slotun başlangıcına git
        }
    }

    private fun interpolateColor(color1: Int, color2: Int, color3: Int, fraction: Float): Int {
        val f = if (fraction < 0.5f) {
            fraction * 2f
        } else {
            (fraction - 0.5f) * 2f
        }

        val c1 = if (fraction < 0.5f) color1 else color2
        val c2 = if (fraction < 0.5f) color2 else color3

        val a1 = Color.alpha(c1)
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)

        val a2 = Color.alpha(c2)
        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)

        val a = (a1 + (f * (a2 - a1))).toInt()
        val r = (r1 + (f * (r2 - r1))).toInt()
        val g = (g1 + (f * (g2 - g1))).toInt()
        val b = (b1 + (f * (b2 - b1))).toInt()

        return Color.argb(a, r, g, b)
    }

    fun setGradientColors(start: Int, mid: Int, end: Int) {
        startColor = start
        midColor = mid
        endColor = end
        invalidate()
    }

    fun setLineThicknessDp(thicknessDp: Float) {
        actualLineThicknessPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            thicknessDp,
            resources.displayMetrics
        )
        invalidate()
    }
}
