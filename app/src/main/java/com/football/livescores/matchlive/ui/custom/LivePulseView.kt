package com.football.livescores.matchlive.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.football.livescores.matchlive.R

class LivePulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var pulseProgress = 0f

    // Premium color palette values resolved dynamically from XML resources
    private val colorCenter by lazy { ContextCompat.getColor(context, R.color.live_pulse_center) }
    private val colorPrimary by lazy { ContextCompat.getColor(context, R.color.live_pulse_primary) }
    private val colorTransparent by lazy { ContextCompat.getColor(context, R.color.live_pulse_transparent) }
    private val colorGlowStart by lazy { ContextCompat.getColor(context, R.color.live_pulse_glow_start) }
    private val colorGlowMid by lazy { ContextCompat.getColor(context, R.color.live_pulse_glow_mid) }
    private val colorDark by lazy { ContextCompat.getColor(context, R.color.live_pulse_dark) }

    init {
        // Support shadow layers for soft glows
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startPulseAnimator()
    }

    private fun startPulseAnimator() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                pulseProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null || !animator!!.isRunning) {
            startPulseAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = Math.min(width, height) / 5f
        val maxRadius = Math.min(width, height) / 2f

        // Draw 3 layers of concentric breathing pulse rings, each 120 degrees out-of-phase (0.33f)
        val phases = floatArrayOf(
            pulseProgress,
            (pulseProgress + 0.33f) % 1.0f,
            (pulseProgress + 0.67f) % 1.0f
        )

        for (phase in phases) {
            val radius = baseRadius + (maxRadius - baseRadius) * phase
            
            // Premium exponential fade out so it dissipates naturally towards the edge
            val alphaRatio = Math.pow((1.0 - phase), 2.0).toFloat()
            val ringAlpha = (180 * alphaRatio).toInt()

            pulsePaint.color = colorPrimary
            pulsePaint.alpha = ringAlpha
            // Thinner outer lines for an elegant, tech-radar style
            pulsePaint.strokeWidth = 2.5f + (3f * (1f - phase))

            canvas.drawCircle(cx, cy, radius, pulsePaint)
        }

        // Draw an organic radial light glow behind the center dot to create a light emission effect
        val glowRadius = baseRadius * 1.8f
        val glowShader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(colorGlowStart, colorGlowMid, colorTransparent),
            null, Shader.TileMode.CLAMP
        )
        glowPaint.shader = glowShader
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // Draw the premium solid inner dot with radial gradient core
        val dotShader = RadialGradient(
            cx, cy, baseRadius,
            intArrayOf(colorCenter, colorPrimary, colorDark),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        dotPaint.shader = dotShader

        // Add software glow shadow to the inner core
        dotPaint.setShadowLayer(8f, 0f, 0f, colorPrimary)
        canvas.drawCircle(cx, cy, baseRadius, dotPaint)
    }
}
