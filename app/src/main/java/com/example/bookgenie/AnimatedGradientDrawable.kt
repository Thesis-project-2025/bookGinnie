package com.example.bookgenie.drawable // veya kendi paket adın

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

class AnimatedGradientDrawable(
    @ColorInt private var startColor1: Int,
    @ColorInt private var endColor1: Int,
    @ColorInt private var startColor2: Int,
    @ColorInt private var endColor2: Int,
    private var duration: Long = 8000L // Animasyon süresi (ms) - tam bir döngü
) : Drawable() {

    private val paint = Paint()
    private var currentGradient: Shader? = null
    private val currentBounds = Rect()

    // Anlık renkler
    @ColorInt private var animStartColor: Int = startColor1
    @ColorInt private var animEndColor: Int = endColor1

    // Anlık gradient koordinatları
    private var animStartX: Float = 0f
    private var animStartY: Float = 0f
    private var animEndX: Float = 0f
    private var animEndY: Float = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = this@AnimatedGradientDrawable.duration
        // REVERSE yerine RESTART daha akıcı bir döngü sağlayabilir
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            val rawFraction = animation.animatedValue as Float // 0 -> 1 arası gider gelir

            // Renkleri Yumuşak Geçişle Değiştir (0->1->0 efekti için sinüs kullanılabilir)
            val colorFraction = (sin(rawFraction * Math.PI)).toFloat() // 0->1->0 yumuşak geçiş
            animStartColor = ColorUtils.blendARGB(startColor1, startColor2, colorFraction)
            animEndColor = ColorUtils.blendARGB(endColor1, endColor2, colorFraction)

            // Gradient Koordinatlarını Hareket Ettir (Örn: Çapraz Döndürme)
            // rawFraction (0->1) ilerledikçe açı değişsin (0 -> 360 derece)
            val angle = rawFraction * 2 * Math.PI // 0 to 2PI radyan
            val centerX = currentBounds.exactCenterX()
            val centerY = currentBounds.exactCenterY()
            val length = currentBounds.width().coerceAtLeast(currentBounds.height()) / 2f // Yarıçap gibi

            // Başlangıç ve bitiş noktalarını merkeze göre döndür
            animStartX = centerX - length * cos(angle).toFloat()
            animStartY = centerY - length * sin(angle).toFloat()
            animEndX = centerX + length * cos(angle).toFloat()
            animEndY = centerY + length * sin(angle).toFloat()

            // Shader'ı yeni renkler ve koordinatlarla güncelle
            updateShader()
            invalidateSelf() // Drawable'ı yeniden çizmesi için uyar
        }
    }

    init {
        updateShader() // Başlangıç shader'ı
    }

    override fun draw(canvas: Canvas) {
        paint.shader = currentGradient
        canvas.drawRect(bounds, paint)
    }

    override fun onBoundsChange(bounds: Rect) {
        currentBounds.set(bounds)
        updateShader() // Yeni boyutlara göre shader'ı güncelle
        if (!animator.isStarted) {
            startAnimation()
        }
    }

    private fun updateShader() {
        if (currentBounds.width() > 0 && currentBounds.height() > 0) {
            currentGradient = LinearGradient(
                animStartX, animStartY, // Hesaplanan başlangıç noktaları
                animEndX, animEndY,     // Hesaplanan bitiş noktaları
                animStartColor,         // Hesaplanan başlangıç rengi
                animEndColor,           // Hesaplanan bitiş rengi
                Shader.TileMode.CLAMP
            )
        } else {
            currentGradient = null
        }
    }

    fun startAnimation() {
        if (!animator.isRunning) {
            animator.start()
        }
    }

    fun stopAnimation() {
        if (animator.isRunning) {
            animator.cancel()
        }
    }

    // --- Zorunlu Drawable Metotları ---
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return if (paint.alpha == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
    }
}