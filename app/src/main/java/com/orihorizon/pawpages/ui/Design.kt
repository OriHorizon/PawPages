package com.orihorizon.pawpages.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal object Palette {
    const val background = 0xFFFFF9FC.toInt()
    const val paper = 0xFFFFFFFF.toInt()
    const val ink = 0xFF392A3D.toInt()
    const val secondary = 0xFF665568.toInt()
    const val pink = 0xFFFAE7EF.toInt()
    const val blue = 0xFFE5F0FB.toInt()
    const val lilac = 0xFFF0E7F7.toInt()
    const val vanilla = 0xFFF8EEDF.toInt()
    const val line = 0xFFEBDEE8.toInt()
    const val rose = 0xFF923C64.toInt()
    const val deepRose = 0xFF793452.toInt()
    const val error = 0xFFAE284A.toInt()
}

internal fun Context.dp(value: Int) = (resources.displayMetrics.density * value).roundToInt()

internal fun View.pad(start: Int, top: Int, end: Int, bottom: Int) =
    setPaddingRelative(context.dp(start), context.dp(top), context.dp(end), context.dp(bottom))

internal fun Context.shape(color: Int, radius: Int, border: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        border?.let { setStroke(dp(1), it) }
    }

internal fun Context.touch(color: Int, radius: Int, border: Int? = null) = RippleDrawable(
    ColorStateList.valueOf(0x22733A5A),
    shape(color, radius, border), shape(Palette.paper, radius)
)

internal fun Context.stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.row() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

internal fun Context.label(value: String, size: Float = 15f, color: Int = Palette.ink,
                           bold: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size
    setTextColor(color)
    typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif",
        Typeface.NORMAL)
    includeFontPadding = false
}

internal fun Context.heading(value: String, size: Float = 35f) = label(value, size).apply {
    typeface = Typeface.create("serif", Typeface.NORMAL)
}

internal fun LinearLayout.gap(height: Int) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(height)))
}

internal fun Context.action(title: String, icon: Symbol, fill: Int = Palette.rose,
                            foreground: Int = Palette.paper, click: () -> Unit) = row().apply {
    gravity = Gravity.CENTER
    background = touch(fill, 18, Palette.line.takeIf { fill == Palette.paper })
    minimumHeight = dp(52)
    pad(16, 10, 16, 10)
    addView(Glyph(context, icon, foreground), LinearLayout.LayoutParams(dp(20), dp(20)))
    addView(label(title, 14f, foreground, bold = true).apply { pad(8, 0, 0, 0) })
    describeButton(title, click)
}

internal fun View.describeButton(title: String, click: () -> Unit) {
    contentDescription = title
    isFocusable = true
    setOnClickListener { click() }
    accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View, info: android.view.accessibility.AccessibilityNodeInfo
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.Button"
        }
    }
}

internal enum class Symbol { PAW, BOOK, PLUS, BACK, ARROW, PDF, BOOKMARK, SAVED, PEN, NOTE, MARKS, DOWN }

internal class Glyph(context: Context, private var symbol: Symbol, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setSymbol(value: Symbol) {
        if (symbol != value) {
            symbol = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - scale * 24) / 2, (height - scale * 24) / 2)
        canvas.scale(scale, scale)
        paint.color = color
        paint.strokeWidth = 1.65f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        paint.pathEffect = null
        fun line(vararg points: Float) {
            path.reset()
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            canvas.drawPath(path, paint)
        }
        when (symbol) {
            Symbol.PAW -> {
                paint.style = Paint.Style.FILL
                canvas.drawOval(3f, 6f, 7.5f, 12f, paint)
                canvas.drawOval(7.5f, 2.5f, 11.5f, 9f, paint)
                canvas.drawOval(12.5f, 2.5f, 16.5f, 9f, paint)
                canvas.drawOval(16.5f, 6f, 21f, 12f, paint)
                path.reset()
                path.moveTo(12f, 11f)
                path.cubicTo(8f, 11f, 4f, 16f, 5f, 19f)
                path.cubicTo(6f, 23f, 10f, 20f, 12f, 20f)
                path.cubicTo(14f, 20f, 18f, 23f, 19f, 19f)
                path.cubicTo(20f, 16f, 16f, 11f, 12f, 11f)
                canvas.drawPath(path, paint)
            }
            Symbol.BOOK -> {
                line(12f, 6f, 8f, 4.5f, 3f, 4.5f, 3f, 17.5f, 8f, 17.5f, 12f, 19.5f,
                    16f, 17.5f, 21f, 17.5f, 21f, 4.5f, 16f, 4.5f, 12f, 6f, 12f, 19.5f)
            }
            Symbol.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            Symbol.BACK -> { line(14.5f, 5f, 7f, 12f, 14.5f, 19f); line(7f, 12f, 21f, 12f) }
            Symbol.ARROW -> { line(10f, 6f, 16f, 12f, 10f, 18f) }
            Symbol.PDF -> {
                line(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f, 6f, 3f)
                line(14f, 3f, 14f, 8f, 19f, 8f)
                line(9f, 13f, 16f, 13f); line(9f, 16f, 14f, 16f)
            }
            Symbol.BOOKMARK, Symbol.SAVED -> {
                if (symbol == Symbol.SAVED) paint.style = Paint.Style.FILL_AND_STROKE
                line(6f, 3.5f, 18f, 3.5f, 18f, 20.5f, 12f, 17f, 6f, 20.5f, 6f, 3.5f)
            }
            Symbol.PEN -> {
                line(4f, 20f, 7f, 13f, 17f, 3f, 21f, 7f, 11f, 17f, 4f, 20f)
                line(7f, 13f, 11f, 17f); line(12f, 20f, 21f, 20f)
            }
            Symbol.NOTE -> {
                line(4f, 4f, 20f, 4f, 20f, 16f, 10f, 16f, 4f, 20f, 4f, 4f)
                line(8f, 8f, 16f, 8f); line(8f, 12f, 13f, 12f)
            }
            Symbol.MARKS -> {
                for (y in listOf(6f, 12f, 18f)) {
                    line(4f, y, 5f, y)
                    line(9f, y, 20f, y)
                }
            }
            Symbol.DOWN -> line(6f, 9f, 12f, 15f, 18f, 9f)
        }
        canvas.restore()
    }
}
