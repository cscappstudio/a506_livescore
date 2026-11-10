package com.football.livescores.matchlive.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.core.content.ContextCompat
import com.football.livescores.matchlive.R

class FootballPitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Harmonious modern lawn greens
    private val stripePaintA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pitch_stripe_a)
        style = Paint.Style.FILL
    }

    private val stripePaintB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pitch_stripe_b)
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pitch_line)
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val glowingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pitch_border_glow)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.selectedBlue)
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.background)
        style = Paint.Style.FILL
    }

    private val textBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface)
        style = Paint.Style.FILL
    }

    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textPrimary)
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primaryRed)
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val teamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textPrimary)
        textSize = 21f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val situationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorSuccess)
        textSize = 17f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val ballGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pitch_ball_shadow)
        style = Paint.Style.FILL
    }

    // Dynamic state parameters
    private var ballX = 0.5f
    private var ballY = 0.5f

    private var activeAttackSide = 0 // 0 = none, 1 = Home (attacking right), 2 = Away (attacking left)
    private var attackProgress = 0f

    private var homeTeamName = "Home Team"
    private var awayTeamName = "Away Team"
    private var matchStatus = "1st Half | 00:00"
    private var matchStatusExtra = "0'"
    private var situationText = "Safe Possession"

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startAttackAnimation()
    }

    fun setTeamNames(home: String, away: String) {
        homeTeamName = home
        awayTeamName = away
        invalidate()
    }

    fun setMatchStatus(status: String, extra: String) {
        matchStatus = status
        matchStatusExtra = extra
        invalidate()
    }

    fun setSituationText(situation: String) {
        situationText = situation
        invalidate()
    }

    fun updateBallPosition(x: Float, y: Float) {
        ballX = x.coerceIn(0.05f, 0.95f)
        ballY = y.coerceIn(0.1f, 0.9f)
        invalidate()
    }

    fun triggerHomeAttack() {
        activeAttackSide = 1
        invalidate()
    }

    fun triggerAwayAttack() {
        activeAttackSide = 2
        invalidate()
    }

    fun clearAttack() {
        activeAttackSide = 0
        invalidate()
    }

    private fun startAttackAnimation() {
        val anim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                attackProgress = interpolatedTime
                invalidate()
            }
        }
        anim.duration = 1800
        anim.repeatCount = Animation.INFINITE
        anim.repeatMode = Animation.RESTART
        startAnimation(anim)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Striped Lawn Grass
        val stripesCount = 15
        val stripeWidth = w / stripesCount
        for (i in 0 until stripesCount) {
            val startX = i * stripeWidth
            val paint = if (i % 2 == 0) stripePaintA else stripePaintB
            canvas.drawRect(startX, 0f, startX + stripeWidth, h, paint)
        }

        val pad = 16f
        val midX = w / 2f

        // 2. Draw holographic pressure waves for active attacks
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }

        if (activeAttackSide != 0) {
            val phases = floatArrayOf(
                attackProgress,
                (attackProgress + 0.33f) % 1.0f,
                (attackProgress + 0.67f) % 1.0f
            )
            for (phase in phases) {
                // Decay opacity as wave travels
                val waveAlpha = (110 * (1.0f - phase)).toInt()
                wavePaint.alpha = waveAlpha
                wavePaint.strokeWidth = 2f + 4f * (1.0f - phase)

                val wavePath = Path()
                if (activeAttackSide == 1) { // Home attacking right
                    wavePaint.color = ContextCompat.getColor(context, R.color.colorSuccess)
                    val x = midX + phase * (midX - pad)
                    wavePath.moveTo(x - 40f * (1f - phase), pad + 8f)
                    wavePath.quadTo(x + 50f * phase, h / 2f, x - 40f * (1f - phase), h - pad - 8f)
                } else { // Away attacking left
                    wavePaint.color = ContextCompat.getColor(context, R.color.textSecondary)
                    val x = midX - phase * (midX - pad)
                    wavePath.moveTo(x + 40f * (1f - phase), pad + 8f)
                    wavePath.quadTo(x - 50f * phase, h / 2f, x + 40f * (1f - phase), h - pad - 8f)
                }
                canvas.drawPath(wavePath, wavePaint)
            }
        }

        // 3. Draw Pitch Markings
        val pitchRect = RectF(pad, pad, w - pad, h - pad)
        canvas.drawRect(pitchRect, linePaint)
        
        // Pitch Neon border outer glow
        glowingBorderPaint.setShadowLayer(10f, 0f, 0f, ContextCompat.getColor(context, R.color.pitch_border_glow))
        canvas.drawRect(pitchRect, glowingBorderPaint)

        // Center line
        canvas.drawLine(midX, pad, midX, h - pad, linePaint)

        // Center circle
        canvas.drawCircle(midX, h / 2f, h / 5.5f, linePaint)
        canvas.drawCircle(midX, h / 2f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        // Penalty boxes
        val penWidth = w / 6.5f
        val penHeight = h / 2.2f
        val penTop = h / 2f - penHeight / 2f

        // Left box
        canvas.drawRect(pad, penTop, pad + penWidth, penTop + penHeight, linePaint)
        canvas.drawCircle(pad + w / 10f, h / 2f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        
        // Right box
        canvas.drawRect(w - pad - penWidth, penTop, w - pad, penTop + penHeight, linePaint)
        canvas.drawCircle(w - pad - w / 10f, h / 2f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        // 4. Draw Ball glowing indicator and Programmatic 3D Soccer Ball
        val drawBallX = pad + (ballX * (w - 2 * pad))
        val drawBallY = pad + (ballY * (h - 2 * pad))
        val ballRadius = 14f

        // Animated ball pulsing aura
        val ballPulsePhase = (System.currentTimeMillis() % 1200) / 1200f
        ballGlowPaint.alpha = (90 * (1f - ballPulsePhase)).toInt()
        canvas.drawCircle(drawBallX, drawBallY, ballRadius + (16f * ballPulsePhase), ballGlowPaint)

        // Draw Soccer Ball vector graphics
        drawSoccerBall(canvas, drawBallX, drawBallY, ballRadius)

        // 5. Draw Info overlays & target dashed linking pointers
        val activeTeam = if (activeAttackSide == 1) homeTeamName else if (activeAttackSide == 2) awayTeamName else "Contested Play"
        val activeSituation = when {
            activeAttackSide == 0 -> "Midfield Play"
            activeAttackSide == 1 && ballX > 0.82f -> "Dangerous Attack 🔥"
            activeAttackSide == 1 && ballX > 0.65f -> "Attack in Box"
            activeAttackSide == 2 && ballX < 0.18f -> "Dangerous Attack 🔥"
            activeAttackSide == 2 && ballX < 0.35f -> "Attack in Box"
            else -> situationText
        }

        // Draw detailed translucent panel for active possession details
        val textWidth = 240f
        val textHeight = 75f
        val textX = if (activeAttackSide == 1) w * 0.52f else if (activeAttackSide == 2) w * 0.15f else w * 0.32f
        val textY = h / 2f - textHeight / 2f

        val labelRect = RectF(textX, textY, textX + textWidth, textY + textHeight)
        canvas.drawRoundRect(labelRect, 8f, 8f, textBgPaint)
        canvas.drawRoundRect(labelRect, 8f, 8f, textBorderPaint)

        // Indicator color stripe
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (activeAttackSide == 1) ContextCompat.getColor(context, R.color.colorSuccess) else if (activeAttackSide == 2) ContextCompat.getColor(context, R.color.textSecondary) else ContextCompat.getColor(context, R.color.colorWarning)
            style = Paint.Style.FILL
        }
        canvas.drawRect(textX, textY, textX + 6f, textY + textHeight, indicatorPaint)

        // Text titles
        canvas.drawText(activeTeam, textX + 16f, textY + 28f, teamPaint)
        situationPaint.color = if (activeSituation.contains("Dangerous")) ContextCompat.getColor(context, R.color.colorError) else ContextCompat.getColor(context, R.color.colorSuccess)
        canvas.drawText(activeSituation, textX + 16f, textY + 54f, situationPaint)

        // Sleek connecting pointer lines
        if (activeAttackSide != 0) {
            val startPX = if (activeAttackSide == 1) textX + textWidth else textX
            val startPY = textY + textHeight / 2f
            canvas.drawLine(startPX, startPY, drawBallX, drawBallY, pointerPaint)
        }

        // 6. Top status overlay pills
        val headerWidth = w * 0.44f
        val headerHeight = 36f
        val headerX = w / 2f - headerWidth / 2f
        val headerY = pad + 10f
        val headerRect = RectF(headerX, headerY, headerX + headerWidth, headerY + headerHeight)
        canvas.drawRoundRect(headerRect, 18f, 18f, headerBgPaint)
        canvas.drawText(matchStatus, w / 2f, headerY + 24f, headerTextPaint)

        if (matchStatusExtra.isNotEmpty()) {
            val badgeWidth = w * 0.12f
            val badgeX = w / 2f + headerWidth / 2f + 8f
            val badgeRect = RectF(badgeX, headerY, badgeX + badgeWidth, headerY + headerHeight)
            canvas.drawRoundRect(badgeRect, 18f, 18f, headerBgPaint)
            canvas.drawText("⏱ $matchStatusExtra", badgeX + badgeWidth / 2f, headerY + 24f, badgeTextPaint)
        }
    }

    /**
     * Programmatically constructs a high-fidelity vector soccer ball using custom geometric mathematical paths.
     */
    private fun drawSoccerBall(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.border)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val darkPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.textSecondary)
            style = Paint.Style.FILL
        }

        // Draw clean base sphere
        canvas.drawCircle(cx, cy, radius, basePaint)

        // Draw central solid dark pentagon
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

        // Draw lines radiating to outer boundary from pentagon corners
        for (i in 0 until 5) {
            val angle = Math.toRadians((i * 72.0 - 90.0))
            val px = cx + pentagonRadius * Math.cos(angle).toFloat()
            val py = cy + pentagonRadius * Math.sin(angle).toFloat()

            // Coordinate on boundary
            val bx = cx + radius * Math.cos(angle).toFloat()
            val by = cy + radius * Math.sin(angle).toFloat()

            canvas.drawLine(px, py, bx, by, borderPaint)
        }

        // Draw outer shell boundary
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }
}
