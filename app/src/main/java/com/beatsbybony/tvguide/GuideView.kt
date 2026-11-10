package com.beatsbybony.tvguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Draws the whole 1990s-style channel guide: a live preview window and clock
 * at the top, and a scrolling channel / time grid underneath.
 * The preview window is left transparent so the video surface behind shows through.
 */
class GuideView(context: Context) : View(context) {

    companion object {
        const val HALF_HOUR = 30 * 60 * 1000L
        const val WINDOW = 3 * HALF_HOUR
        const val VISIBLE_ROWS = 6

        fun floorHalfHour(t: Long): Long {
            val off = TimeZone.getDefault().getOffset(t)
            val local = t + off
            return local - (local % HALF_HOUR) - off
        }
    }

    var channels: List<Channel> = emptyList()
    var selRow = 0
    var selTime = System.currentTimeMillis()
    var windowStart = floorHalfHour(System.currentTimeMillis())
    var guideVisible = true
    val videoRect = Rect()
    var onLayoutChanged: (() -> Unit)? = null

    private var firstRow = 0
    private var topH = 0f
    private var margin = 0f

    private var bannerLine1 = ""
    private var bannerLine2 = ""
    private var bannerUntil = 0L

    private val cTop = 0xFF0A1580.toInt()
    private val cHeader = 0xFF3A1F9E.toInt()
    private val cLabel = 0xFF081060.toInt()
    private val cCellA = 0xFF1E3AC8.toInt()
    private val cCellB = 0xFF1830A8.toInt()
    private val cBorder = 0xFF9FB0FF.toInt()
    private val cYellow = 0xFFFFD21F.toInt()
    private val cLight = 0xFFB8C4FF.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE MMM d", Locale.US)

    // ---------- navigation ----------

    fun selectedAiring(): Airing? = channels.getOrNull(selRow)?.airingAt(selTime)

    fun resetToNow(row: Int) {
        val now = System.currentTimeMillis()
        selTime = now
        windowStart = floorHalfHour(now)
        selRow = if (channels.isEmpty()) 0 else row.coerceIn(0, channels.size - 1)
        ensureRowVisible()
        invalidate()
    }

    fun moveRow(delta: Int) {
        if (channels.isEmpty()) return
        selRow = (selRow + delta + channels.size) % channels.size
        ensureRowVisible()
        invalidate()
    }

    fun moveTime(dir: Int) {
        if (channels.isEmpty()) return
        val now = System.currentTimeMillis()
        val a = selectedAiring()
        if (dir > 0) {
            selTime = a?.endMs ?: (selTime + HALF_HOUR)
        } else {
            // Never step back onto something that has already finished airing.
            val t = if (a != null) a.startMs - 1 else selTime - HALF_HOUR
            if (t < now) return
            selTime = t
        }
        while (selTime >= windowStart + WINDOW) windowStart += HALF_HOUR
        while (selTime < windowStart) windowStart -= HALF_HOUR
        invalidate()
    }

    private fun ensureRowVisible() {
        if (selRow < firstRow) firstRow = selRow
        if (selRow >= firstRow + VISIBLE_ROWS) firstRow = selRow - VISIBLE_ROWS + 1
        if (firstRow < 0) firstRow = 0
    }

    fun showBanner(line1: String, line2: String, durationMs: Long) {
        bannerLine1 = line1
        bannerLine2 = line2
        bannerUntil = System.currentTimeMillis() + durationMs
        invalidate()
    }

    /** Called once a second: keeps the clock moving and the time window current. */
    fun tick() {
        val now = System.currentTimeMillis()
        val nf = floorHalfHour(now)
        if (windowStart < nf) {
            windowStart = nf
            if (selTime < windowStart) selTime = now
        }
        invalidate()
    }

    // ---------- layout ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        topH = h * 0.40f
        margin = h * 0.03f
        val vh = topH - 2 * margin
        val vw = vh * 16f / 9f
        videoRect.set(margin.toInt(), margin.toInt(), (margin + vw).toInt(), (margin + vh).toInt())
        onLayoutChanged?.invoke()
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.currentTimeMillis()
        if (!guideVisible) {
            drawBanner(c, w, h, now)
            return
        }
        drawTop(c, w, now)
        drawGrid(c, w, h)
        drawBanner(c, w, h, now)
    }

    private fun drawLabel(
        c: Canvas,
        s: String,
        x: Float,
        cy: Float,
        size: Float,
        color: Int,
        maxW: Float,
        align: Paint.Align = Paint.Align.LEFT,
        shadow: Boolean = true
    ) {
        if (maxW <= 0f) return
        text.textSize = size
        text.color = color
        text.textAlign = align
        if (shadow) text.setShadowLayer(size * 0.05f, size * 0.04f, size * 0.04f, Color.BLACK)
        else text.clearShadowLayer()
        val shown = TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END).toString()
        val fm = text.fontMetrics
        c.drawText(shown, x, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun drawTop(c: Canvas, w: Float, now: Long) {
        val vr = videoRect
        fill.color = cTop
        // Fill everything except the video window so the video shows through the hole.
        c.drawRect(0f, 0f, vr.left.toFloat(), topH, fill)
        c.drawRect(vr.left.toFloat(), 0f, vr.right.toFloat(), vr.top.toFloat(), fill)
        c.drawRect(vr.left.toFloat(), vr.bottom.toFloat(), vr.right.toFloat(), topH, fill)
        c.drawRect(vr.right.toFloat(), 0f, w, topH, fill)

        stroke.color = cBorder
        stroke.strokeWidth = 6f
        c.drawRect(vr.left - 3f, vr.top - 3f, vr.right + 3f, vr.bottom + 3f, stroke)

        val px0 = vr.right + margin
        val px1 = w - margin
        val pw = px1 - px0
        val ph = topH - 2 * margin
        val d = Date(now)

        // Clock (left) and temperature (right)
        drawLabel(c, timeFmt.format(d), px0, margin + ph * 0.14f, ph * 0.26f, cYellow, pw * 0.55f)
        val temp = Weather.text
        if (temp.isNotEmpty()) {
            drawLabel(
                c, Config.LOCATION_LABEL + "  " + temp, px1, margin + ph * 0.14f,
                ph * 0.14f, Color.WHITE, pw * 0.45f, Paint.Align.RIGHT
            )
        }
        drawLabel(c, dateFmt.format(d).uppercase(Locale.US), px0, margin + ph * 0.36f, ph * 0.11f, cLight, pw)

        stroke.color = cBorder
        stroke.strokeWidth = 3f
        c.drawLine(px0, margin + ph * 0.46f, px1, margin + ph * 0.46f, stroke)

        // Info about the highlighted program
        val ch = channels.getOrNull(selRow)
        val a = selectedAiring()
        if (ch != null) {
            drawLabel(c, "CH " + ch.number + "  " + ch.name, px0, margin + ph * 0.56f, ph * 0.11f, cYellow, pw)
            if (a != null) {
                drawLabel(c, a.program.title.uppercase(Locale.US), px0, margin + ph * 0.73f, ph * 0.14f, Color.WHITE, pw)
                val range = timeFmt.format(Date(a.startMs)) + " - " + timeFmt.format(Date(a.endMs))
                drawLabel(c, range, px0, margin + ph * 0.90f, ph * 0.11f, cLight, pw)
            } else {
                drawLabel(
                    c, if (ch.loaded) "NO SIGNAL" else "LOADING...", px0, margin + ph * 0.73f,
                    ph * 0.14f, Color.WHITE, pw
                )
            }
        }
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        val headerH = h * 0.065f
        val rowsTop = topH + headerH
        val rowH = (h - rowsTop) / VISIBLE_ROWS
        val labelW = w * 0.15f
        val gridW = w - labelW
        val slotW = gridW / 3f
        val pxPerMs = gridW / WINDOW

        // Time header
        fill.color = cHeader
        c.drawRect(0f, topH, w, rowsTop, fill)
        drawLabel(c, "TV GUIDE", 16f, topH + headerH / 2f, headerH * 0.5f, cYellow, labelW - 24f)
        stroke.color = cBorder
        stroke.strokeWidth = 2f
        for (k in 0 until 3) {
            val x = labelW + k * slotW
            c.drawLine(x, topH + 4f, x, rowsTop - 4f, stroke)
            drawLabel(
                c, timeFmt.format(Date(windowStart + k * HALF_HOUR)), x + 14f,
                topH + headerH / 2f, headerH * 0.55f, Color.WHITE, slotW - 20f
            )
        }

        val selAiring = selectedAiring()
        val end = windowStart + WINDOW

        for (r in 0 until VISIBLE_ROWS) {
            val idx = firstRow + r
            if (idx >= channels.size) break
            val ch = channels[idx]
            val y0 = rowsTop + r * rowH
            val y1 = y0 + rowH
            val cy = (y0 + y1) / 2f
            val rowSelected = idx == selRow

            // Channel label cell
            fill.color = cLabel
            c.drawRect(2f, y0 + 2f, labelW - 2f, y1 - 2f, fill)
            if (rowSelected) {
                stroke.color = cYellow
                stroke.strokeWidth = 3f
                c.drawRect(3f, y0 + 3f, labelW - 3f, y1 - 3f, stroke)
            }
            drawLabel(c, ch.number.toString(), 14f, cy, rowH * 0.46f, cYellow, rowH * 0.9f)
            drawLabel(c, ch.name, 14f + rowH * 0.9f, cy, rowH * 0.32f, Color.WHITE, labelW - rowH * 0.9f - 22f)

            // Program cells
            val baseFill = if (r % 2 == 0) cCellA else cCellB
            if (ch.programs.isEmpty()) {
                drawCell(
                    c, labelW, y0, w, y1, if (ch.loaded) "NO SIGNAL" else "LOADING...",
                    baseFill, false, rowH
                )
                continue
            }
            var t = windowStart
            while (t < end) {
                val a = ch.airingAt(t) ?: break
                val cs = maxOf(a.startMs, windowStart)
                val ce = minOf(a.endMs, end)
                val x0 = labelW + (cs - windowStart) * pxPerMs
                val x1 = labelW + (ce - windowStart) * pxPerMs
                val isSel = rowSelected && selAiring != null && selAiring.startMs == a.startMs
                var title = a.program.title.uppercase(Locale.US)
                if (a.startMs < windowStart) title = "« " + title
                if (a.endMs > end) title = "$title »"
                drawCell(c, x0, y0, x1, y1, title, baseFill, isSel, rowH)
                t = a.endMs
            }
        }
    }

    private fun drawCell(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float,
        title: String, base: Int, selected: Boolean, rowH: Float
    ) {
        fill.color = if (selected) cYellow else base
        c.drawRect(x0 + 2f, y0 + 2f, x1 - 2f, y1 - 2f, fill)
        stroke.color = if (selected) Color.WHITE else cBorder
        stroke.strokeWidth = if (selected) 4f else 2f
        c.drawRect(x0 + 2f, y0 + 2f, x1 - 2f, y1 - 2f, stroke)
        val maxW = (x1 - x0) - 24f
        if (maxW > 30f) {
            drawLabel(
                c, title, x0 + 12f, (y0 + y1) / 2f, rowH * 0.34f,
                if (selected) Color.BLACK else Color.WHITE, maxW, Paint.Align.LEFT, !selected
            )
        }
    }

    private fun drawBanner(c: Canvas, w: Float, h: Float, now: Long) {
        if (now >= bannerUntil) return
        val x0 = w * 0.05f
        val x1 = w * 0.95f
        val y0 = h * 0.78f
        val y1 = h * 0.94f
        fill.color = cHeader
        c.drawRect(x0, y0, x1, y1, fill)
        stroke.color = cBorder
        stroke.strokeWidth = 4f
        c.drawRect(x0, y0, x1, y1, stroke)
        val bh = y1 - y0
        drawLabel(c, bannerLine1, x0 + 24f, y0 + bh * 0.32f, bh * 0.28f, cYellow, x1 - x0 - 48f)
        drawLabel(c, bannerLine2, x0 + 24f, y0 + bh * 0.70f, bh * 0.26f, Color.WHITE, x1 - x0 - 48f)
    }
}
