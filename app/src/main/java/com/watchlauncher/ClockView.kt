package com.watchlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

/**
 * A lightweight clock widget that renders a digital clock with date
 * directly on a Canvas — no Bitmap required, works perfectly on round Wear OS screens.
 */
class ClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paints ──────────────────────────────────────────────────────────────
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    // ── Tick receiver ────────────────────────────────────────────────────────
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(tickReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(tickReceiver) } catch (_: Exception) {}
    }

    // ── Drawing ──────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Scale text to ~40% of view height for time, 14% for date
        timePaint.textSize = h * 0.40f
        shadowPaint.textSize = h * 0.40f
        datePaint.textSize = h * 0.18f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cal = Calendar.getInstance()
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val cx = width / 2f
        val cy = height / 2f

        val timeStr = String.format("%02d:%02d", hour, minute)
        val days   = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val months = arrayOf("JAN","FEB","MAR","APR","MAY","JUN",
                             "JUL","AUG","SEP","OCT","NOV","DEC")
        val dateStr = "${days[cal.get(Calendar.DAY_OF_WEEK)-1]}  " +
                "${cal.get(Calendar.DAY_OF_MONTH)} " +
                months[cal.get(Calendar.MONTH)]

        // Shadow pass
        canvas.drawText(timeStr, cx + 2f, cy + 2f, shadowPaint)
        // Time
        canvas.drawText(timeStr, cx, cy, timePaint)
        // Date below
        canvas.drawText(dateStr, cx, cy + datePaint.textSize * 1.3f, datePaint)
    }
}
