package com.football.livescores.matchlive.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.football.livescores.matchlive.R

data class TimelineEvent(
    val minute: Int,
    val type: String, // "GOAL", "CARD_YELLOW", "CARD_RED", "SUBST"
    val isHome: Boolean
)

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var events = listOf<TimelineEvent>()
    private val density = context.resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val playheadGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_playhead_glow)
        style = Paint.Style.FILL
    }

    private val playheadCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.selectedBlue)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textSecondary)
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val minuteLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textSecondary)
        textSize = 9.5f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
        strokeWidth = 1.2f * density
        style = Paint.Style.STROKE
    }

    private var currentMatchMinute = 78

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setEvents(emptyList())
    }

    fun setEvents(eventList: List<TimelineEvent>) {
        events = eventList
        invalidate()
    }

    fun setMatchMinute(minute: Int) {
        currentMatchMinute = minute
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Moderate padding (32dp) to make the horizontal timeline stretch beautifully wide across the card!
        val padding = 32f * density
        val centerY = h / 2f
        val timelineWidth = w - (2 * padding)

        // 1. Draw central structural grid segments (Ticks at 15, 30, 45, 60, 75)
        canvas.drawLine(padding, centerY, w - padding, centerY, linePaint)
        
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.border)
            strokeWidth = 1f * density
            style = Paint.Style.STROKE
        }
        for (i in 1..5) {
            val tickX = padding + (i * 15f / 90f) * timelineWidth
            canvas.drawLine(tickX, centerY - 4f * density, tickX, centerY + 4f * density, tickPaint)
        }

        // 2. Draw active progress line with gradient flow
        val progressRatio = (currentMatchMinute.toFloat() / 90f).coerceIn(0f, 1f)
        val progressX = padding + (progressRatio * timelineWidth)

        progressPaint.shader = LinearGradient(
            padding, centerY, progressX, centerY,
            intArrayOf(
                ContextCompat.getColor(context, R.color.selectedBlue),
                ContextCompat.getColor(context, R.color.timeline_progress_end)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawLine(padding, centerY, progressX, centerY, progressPaint)

        // Glowing active playhead marker
        if (currentMatchMinute in 1..89) {
            val auraRadius = 6.5f * density
            val pulseAura = (System.currentTimeMillis() % 1000) / 1000f
            playheadGlowPaint.alpha = (90 * (1f - pulseAura)).toInt()
            canvas.drawCircle(progressX, centerY, auraRadius + (3.5f * density * pulseAura), playheadGlowPaint)
            canvas.drawCircle(progressX, centerY, 2.5f * density, playheadCorePaint)
        }

        // 3. Main structural labels (00', HT 45', 90')
        canvas.drawText("00'", padding, centerY + 18f * density, textPaint)
        canvas.drawText("90'", w - padding, centerY + 18f * density, textPaint)
        canvas.drawText("HT 45'", padding + (0.5f * timelineWidth), centerY + 18f * density, textPaint)

        // 4. Draw detailed visual events
        for (event in events) {
            val eventRatio = (event.minute.toFloat() / 90f).coerceIn(0f, 1f)
            val x = padding + (eventRatio * timelineWidth)
            val yOffset = if (event.isHome) -18f * density else 18f * density

            // Connector link line
            canvas.drawLine(x, centerY, x, centerY + yOffset, connectorPaint)

            val eventY = centerY + yOffset
            when (event.type) {
                "GOAL" -> {
                    drawSoccerBall(canvas, x, eventY, 5.5f * density)
                }
                "CARD_YELLOW" -> {
                    drawCardNode(canvas, x, eventY, isRed = false)
                }
                "CARD_RED" -> {
                    drawCardNode(canvas, x, eventY, isRed = true)
                }
                "SUBST" -> {
                    drawSubstitutionNode(canvas, x, eventY, 5.5f * density)
                }
            }

            // Draw clean timestamp label with clear offsets to absolutely avoid overlaps with icons
            val textY = if (event.isHome) eventY - 11f * density else eventY + 19f * density
            canvas.drawText("${event.minute}'", x, textY, minuteLabelPaint)
        }
    }

    /**
     * Programmatically constructs a high-fidelity vector soccer ball.
     */
    private fun drawSoccerBall(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.timeline_soccer_ball_border)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }
        val darkPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.timeline_soccer_ball_dark)
            style = Paint.Style.FILL
        }

        canvas.drawCircle(cx, cy, radius, basePaint)

        // Pentagon core
        val pentagonPath = Path()
        val pentagonRadius = radius * 0.35f
        for (i in 0 until 5) {
            val angle = Math.toRadians((i * 72.0 - 90.0))
            val px = cx + pentagonRadius * Math.cos(angle).toFloat()
            val py = cy + pentagonRadius * Math.sin(angle).toFloat()
            if (i == 0) {
                pentagonPath.moveTo(px, py)
            } else {
                pentagonPath.lineTo(px, py)
            }
        }
        pentagonPath.close()
        canvas.drawPath(pentagonPath, darkPanelPaint)
        canvas.drawPath(pentagonPath, borderPaint)

        // Radiating line segments
        for (i in 0 until 5) {
            val angle = Math.toRadians((i * 72.0 - 90.0))
            val px = cx + pentagonRadius * Math.cos(angle).toFloat()
            val py = cy + pentagonRadius * Math.sin(angle).toFloat()
            val bx = cx + radius * Math.cos(angle).toFloat()
            val by = cy + radius * Math.sin(angle).toFloat()
            canvas.drawLine(px, py, bx, by, borderPaint)
        }

        canvas.drawCircle(cx, cy, radius, borderPaint)
    }

    /**
     * Programmatically draws a tilted warning card.
     */
    private fun drawCardNode(canvas: Canvas, cx: Float, cy: Float, isRed: Boolean) {
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isRed) ContextCompat.getColor(context, R.color.colorError) else ContextCompat.getColor(context, R.color.colorWarning)
            style = Paint.Style.FILL
            setShadowLayer(3f * density, 0f, 1f * density, ContextCompat.getColor(context, R.color.black_alpha_25))
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.border)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(15f) // Subtle tilting rotation for organic posture

        val cardW = 7f * density
        val cardH = 11f * density
        val cardRect = RectF(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f)
        canvas.drawRoundRect(cardRect, 1.5f * density, 1.5f * density, cardPaint)
        canvas.drawRoundRect(cardRect, 1.5f * density, 1.5f * density, borderPaint)

        canvas.restore()
    }

    /**
     * Programmatically draws overlapping green (entering) and red (exiting) circles for substitutions.
     */
    private fun drawSubstitutionNode(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.colorSuccess)
            style = Paint.Style.FILL
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.colorError)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.border)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }

        // Render overlapping indicator node structures
        val offset = radius * 0.35f
        val subRadius = radius * 0.72f

        canvas.drawCircle(cx - offset, cy - offset, subRadius, greenPaint)
        canvas.drawCircle(cx - offset, cy - offset, subRadius, borderPaint)

        canvas.drawCircle(cx + offset, cy + offset, subRadius, redPaint)
        canvas.drawCircle(cx + offset, cy + offset, subRadius, borderPaint)
    }
}
