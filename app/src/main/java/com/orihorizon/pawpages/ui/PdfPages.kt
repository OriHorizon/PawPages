package com.orihorizon.pawpages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.orihorizon.pawpages.data.Mark
import com.orihorizon.pawpages.pdf.PdfSession
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class EditMode { READ, HIGHLIGHT, NOTE }

internal class PdfPages(
    private val pdf: PdfSession,
    private val scope: CoroutineScope,
    private val mode: () -> EditMode,
    private val onHighlight: (Mark) -> Unit,
    private val onNote: (Mark) -> Unit
) : RecyclerView.Adapter<PdfPages.Holder>() {
    private var marks: Map<Int, List<Mark>> = emptyMap()

    init { setHasStableIds(true) }

    fun setMarks(value: Map<Int, List<Mark>>) {
        val previous = marks
        if (value === previous) return
        marks = value
        (previous.keys + value.keys).filter { previous[it] != value[it] && it in 0 until itemCount }
            .forEach { notifyItemChanged(it, "marks") }
    }

    override fun getItemCount() = pdf.pages
    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        PdfPage(parent.context, pdf, scope, mode, onHighlight, onNote).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = parent.context.dp(12) }
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.page.bind(position, marks[position].orEmpty())

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads)
        else holder.page.showMarks(marks[position].orEmpty())
    }

    override fun onViewRecycled(holder: Holder) = holder.page.unbind()

    class Holder(internal val page: PdfPage) : RecyclerView.ViewHolder(page)
}

internal class PdfPage(
    context: Context,
    private val pdf: PdfSession,
    private val scope: CoroutineScope,
    private val mode: () -> EditMode,
    private val onHighlight: (Mark) -> Unit,
    private val onNote: (Mark) -> Unit
) : View(context) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var job: Job? = null
    private var page = -1
    private var renderWidth = 0
    private var failed = false
    private var aspect: Double? = null
    private var bitmap: Bitmap? = null
    private var marks = emptyList<Mark>()
    private var start: PointF? = null
    private var end: PointF? = null
    private var gestureMode = EditMode.READ

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        setBackgroundColor(Palette.paper)
    }

    fun bind(index: Int, value: List<Mark>) {
        if (page != index) {
            unbind()
            page = index
            aspect = pdf.cachedSize(index)?.let(::ratio)
            requestLayout()
        }
        showMarks(value)
        if (isAttachedToWindow) post { load() }
    }

    fun showMarks(value: List<Mark>) {
        marks = value
        contentDescription = "PDF page ${page + 1}" +
            if (marks.isEmpty()) "" else ", ${marks.size} annotations"
        invalidate()
    }

    fun unbind() {
        job?.cancel()
        job = null
        bitmap = null
        page = -1
        renderWidth = 0
        aspect = null
        failed = false
        start = null
        end = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { load() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
        job = null
        bitmap = null
        start = null
        end = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * (aspect ?: 1.414)).roundToInt().coerceIn(1, 16_000)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        if (w != oldw) post { load() }
    }

    private fun ratio(size: IntArray) = size[1].toDouble() / size[0]

    private fun load() {
        val index = page
        val width = width
        if (!isAttachedToWindow || index < 0 || width <= 0) return
        if (renderWidth == width && (job?.isActive == true || bitmap != null)) return
        job?.cancel()
        bitmap = pdf.cachedImage(index, width)
        failed = false
        renderWidth = width
        invalidate()
        if (bitmap != null && aspect != null) return
        job = scope.launch {
            try {
                val actualAspect = ratio(pdf.size(index))
                if (aspect != actualAspect) {
                    aspect = actualAspect
                    requestLayout()
                }
                bitmap = pdf.render(index, width)
                invalidate()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed = true
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val picture = bitmap
        if (picture == null) {
            paint.color = Palette.secondary
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = context.dp(14).toFloat()
            canvas.drawText(if (failed) "Tap to retry this page" else "Loading page ${page + 1}…",
                width / 2f, minOf(height / 2f, context.dp(100).toFloat()), paint)
            return
        }
        canvas.drawBitmap(picture, null, bounds, imagePaint)
        marks.filter { it.note == null }.forEach { mark ->
            drawHighlight(canvas, mark.toPixels(bounds.width(), bounds.height()))
        }
        marks.filter { it.note != null }.forEach { mark ->
            val x = (mark.left * width).coerceIn(0f, width.toFloat())
            val y = (mark.top * height).coerceIn(0f, height.toFloat())
            drawPin(canvas, x, y)
        }
        val from = start
        val to = end
        if (from != null && to != null && gestureMode == EditMode.HIGHLIGHT) {
            drawHighlight(canvas, highlightBounds(from, to))
        }
    }

    private fun drawHighlight(canvas: Canvas, rectangle: RectF) {
        paint.color = 0x55FFDB43.toInt()
        canvas.drawRoundRect(rectangle, context.dp(2).toFloat(), context.dp(2).toFloat(), paint)
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float) {
        val radius = context.dp(12).toFloat()
        val centerX = x.coerceIn(min(radius, width / 2f), max(width - radius, width / 2f))
        val centerY = y.coerceIn(min(radius, height / 2f), max(height - radius, height / 2f))
        paint.color = Palette.deepRose
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.strokeWidth = context.dp(1).coerceAtLeast(1).toFloat()
        paint.color = Palette.paper
        val line = radius * 0.42f
        canvas.drawLine(centerX - line, centerY - line / 2, centerX + line, centerY - line / 2, paint)
        canvas.drawLine(centerX - line, centerY + line / 2, centerX + line, centerY + line / 2, paint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (failed) load()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (page < 0 || width == 0 || height == 0) return false
        val point = PointF(event.x.coerceIn(0f, width.toFloat()), event.y.coerceIn(0f, height.toFloat()))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureMode = if (bitmap == null) EditMode.READ else mode()
                start = point
                end = null
                if (gestureMode == EditMode.HIGHLIGHT) parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                end = point
                if (gestureMode == EditMode.HIGHLIGHT) invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val from = start
                start = null
                end = null
                parent.requestDisallowInterceptTouchEvent(false)
                if (from != null) finishGesture(from, point)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                start = null
                end = null
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
        return true
    }

    private fun finishGesture(from: PointF, to: PointF) {
        val tapped = abs(to.x - from.x) < slop && abs(to.y - from.y) < slop
        if (gestureMode == EditMode.HIGHLIGHT) {
            val rectangle = highlightBounds(from, to)
            if (rectangle.width() >= context.dp(8)) {
                onHighlight(Mark(page = page, left = rectangle.left / width, top = rectangle.top / height,
                    right = rectangle.right / width, bottom = rectangle.bottom / height))
            }
        } else if (tapped) {
            performClick()
            if (bitmap == null) return
            val hitSize = context.dp(24).toFloat()
            val existing = marks.lastOrNull { it.note != null &&
                abs(it.left * width - to.x) < hitSize && abs(it.top * height - to.y) < hitSize }
            if (existing != null) onNote(existing)
            else if (gestureMode == EditMode.NOTE) {
                onNote(Mark(page = page, left = to.x / width, top = to.y / height))
            }
        }
    }

    private fun highlightBounds(a: PointF, b: PointF): RectF {
        val middle = (a.y + b.y) / 2f
        val halfStroke = context.dp(4).toFloat()
        return RectF(minOf(a.x, b.x), minOf(a.y, b.y, middle - halfStroke).coerceAtLeast(0f),
            maxOf(a.x, b.x), maxOf(a.y, b.y, middle + halfStroke).coerceAtMost(height.toFloat()))
    }
}
