package com.football.livescores.matchlive.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.football.livescores.matchlive.R

class BracketConnectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midX = w / 2f

        // Draw bracket line shapes:
        // 1. Horizontal line at the very top (left to midX)
        canvas.drawLine(0f, 1.5f, midX, 1.5f, linePaint)
        // 2. Horizontal line at the very bottom (left to midX)
        canvas.drawLine(0f, h - 1.5f, midX, h - 1.5f, linePaint)
        // 3. Vertical line connecting top and bottom at midX
        canvas.drawLine(midX, 1.5f, midX, h - 1.5f, linePaint)
        // 4. Horizontal line extending from midX to the right in the middle
        canvas.drawLine(midX, h / 2f, w, h / 2f, linePaint)
    }
}
