package com.example.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/**
 * Generates a procedural multi-color calibration test grid Bitmap when a layer or scene
 * does not have a video or photo attached yet. Never captures or displays app UI screenshots.
 */
object ProceduralTestGridFactory {

    fun createTestGridBitmap(
        width: Int = 512,
        height: Int = 512,
        baseBgArgb: Int? = null
    ): Bitmap {
        val w = width.coerceAtLeast(64)
        val h = height.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep gradient backdrop or chosen layer color
        if (baseBgArgb != null && baseBgArgb != 0 && baseBgArgb != 0xFF000000.toInt()) {
            paint.color = baseBgArgb
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        } else {
            paint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF1A102B.toInt(), 0xFF0D253F.toInt(), 0xFF1B1B24.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        // Top SMPTE-inspired vivid color bars (65% height)
        val barColors = intArrayOf(
            0xFFEAEAF0.toInt(),
            0xFFFFD54F.toInt(),
            0xFF26C6DA.toInt(),
            0xFF66BB6A.toInt(),
            0xFFD92B7F.toInt(),
            0xFFEF5350.toInt(),
            0xFF42A5F5.toInt()
        )
        val barWidth = w.toFloat() / barColors.size
        val topBarBottom = h * 0.68f
        barColors.forEachIndexed { idx, color ->
            paint.color = color
            paint.alpha = 215
            canvas.drawRect(idx * barWidth, 0f, (idx + 1) * barWidth + 1f, topBarBottom, paint)
        }

        // Bottom contrast & magenta-teal gradient strip (32% height)
        paint.alpha = 255
        paint.shader = LinearGradient(
            0f, topBarBottom, w.toFloat(), h.toFloat(),
            intArrayOf(0xFFB81D6F.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(), 0xFF101016.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, topBarBottom, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Crisp subtle grid lines for shader distortion/blur visibility
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            strokeWidth = (w / 256f).coerceAtLeast(1f)
            style = Paint.Style.STROKE
        }
        val cols = 8
        val rows = 8
        for (i in 1 until cols) {
            val x = (w.toFloat() / cols) * i
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
        }
        for (j in 1 until rows) {
            val y = (h.toFloat() / rows) * j
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
        }

        // Center alignment crosshair & calibration ring
        val cx = w / 2f
        val cy = h / 2f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            strokeWidth = (w / 140f).coerceAtLeast(2f)
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(cx, cy, minOf(w, h) * 0.22f, ringPaint)
        canvas.drawCircle(cx, cy, minOf(w, h) * 0.08f, ringPaint)
        canvas.drawLine(cx - w * 0.28f, cy, cx + w * 0.28f, cy, ringPaint)
        canvas.drawLine(cx, cy - h * 0.28f, cx, cy + h * 0.28f, ringPaint)

        return bmp
    }
}
