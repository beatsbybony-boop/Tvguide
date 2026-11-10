package com.beatsbybony.tvguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * The multicolored test pattern a TV station showed before going on air
 * (an SMPTE-style set of color bars). Shown while a channel is tuning.
 */
class ColorBarsView(context: Context) : View(context) {

    private val paint = Paint()

    private val topBars = intArrayOf(
        Color.rgb(192, 192, 192), // white
        Color.rgb(192, 192, 0),   // yellow
        Color.rgb(0, 192, 192),   // cyan
        Color.rgb(0, 192, 0),     // green
        Color.rgb(192, 0, 192),   // magenta
        Color.rgb(192, 0, 0),     // red
        Color.rgb(0, 0, 192)      // blue
    )

    private val stripBars = intArrayOf(
        Color.rgb(0, 0, 192),
        Color.rgb(19, 19, 19),
        Color.rgb(192, 0, 192),
        Color.rgb(19, 19, 19),
        Color.rgb(0, 192, 192),
        Color.rgb(19, 19, 19),
        Color.rgb(192, 192, 192)
    )

    private fun block(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color
        c.drawRect(l, t, r, b, paint)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bar = w / 7f
        val topEnd = h * 0.67f
        val stripEnd = h * 0.75f

        // Main bars
        for (i in 0 until 7) {
            block(c, i * bar, 0f, (i + 1) * bar, topEnd, topBars[i])
        }
        // Thin reverse strip
        for (i in 0 until 7) {
            block(c, i * bar, topEnd, (i + 1) * bar, stripEnd, stripBars[i])
        }
        // Bottom row: -I, white, +Q, black, then the three "pluge" bars
        val black = Color.rgb(16, 16, 16)
        block(c, 0f, stripEnd, bar, h, Color.rgb(0, 33, 76))
        block(c, bar, stripEnd, 2 * bar, h, Color.rgb(235, 235, 235))
        block(c, 2 * bar, stripEnd, 3 * bar, h, Color.rgb(50, 0, 106))
        block(c, 3 * bar, stripEnd, 5 * bar, h, black)
        block(c, 5 * bar, stripEnd, 5.333f * bar, h, Color.rgb(9, 9, 9))
        block(c, 5.333f * bar, stripEnd, 5.667f * bar, h, black)
        block(c, 5.667f * bar, stripEnd, 6 * bar, h, Color.rgb(29, 29, 29))
        block(c, 6 * bar, stripEnd, w, h, black)
    }
}
