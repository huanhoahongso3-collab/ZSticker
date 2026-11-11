package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Shows the current GIF frame with a draggable crop rectangle on top (move it, drag its edges / corners,
 * optionally with a locked aspect ratio). The rectangle lives in the real GIF's pixel space, so the frames
 * shown can be small previews.
 */
class GifCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private var frame: Bitmap? = null
    private var sourceW = 0
    private var sourceH = 0

    /** Crop rectangle in source pixels. */
    private val crop = RectF()
    private val imageRect = RectF()

    /** Locked width / height ratio, or null for a free rectangle. */
    private var aspect: Float? = null

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val slop = 36f * density
    private val handleLen = 26f * density
    private val minSide get() = max(24f, min(sourceW, sourceH) * 0.08f)

    // Active drag
    private var dragLeft = false
    private var dragTop = false
    private var dragRight = false
    private var dragBottom = false
    private var dragMove = false
    private var lastX = 0f
    private var lastY = 0f

    fun setSource(width: Int, height: Int) {
        sourceW = width
        sourceH = height
        crop.set(0f, 0f, width.toFloat(), height.toFloat())
        aspect = null
        requestLayout()
        invalidate()
    }

    fun setFrame(bitmap: Bitmap) {
        frame = bitmap
        invalidate()
    }

    /** The crop rectangle in source pixels. */
    fun getCropRect(): Rect = Rect(
        crop.left.toInt(),
        crop.top.toInt(),
        crop.right.toInt().coerceAtLeast(crop.left.toInt() + 1),
        crop.bottom.toInt().coerceAtLeast(crop.top.toInt() + 1)
    )

    /**
     * Locks the rectangle to [ratio] (width / height): the largest rectangle of that shape that fits in the
     * GIF, centred on where the crop currently is. Null frees it again and leaves the rectangle as it is.
     */
    fun setAspect(ratio: Float?) {
        aspect = ratio
        if (ratio != null && sourceW > 0) {
            val cx = crop.centerX()
            val cy = crop.centerY()
            var w = sourceW.toFloat()
            var h = w / ratio
            if (h > sourceH) {
                h = sourceH.toFloat()
                w = h * ratio
            }
            crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            clampMove(0f, 0f)
        }
        invalidate()
    }

    fun resetToFull() {
        crop.set(0f, 0f, sourceW.toFloat(), sourceH.toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = frame ?: return
        if (sourceW == 0 || sourceH == 0) return
        computeImageRect()
        canvas.drawBitmap(bmp, null, imageRect, framePaint)

        val r = cropOnScreen()
        // Dim everything outside the crop
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, r.top, scrimPaint)
        canvas.drawRect(imageRect.left, r.bottom, imageRect.right, imageRect.bottom, scrimPaint)
        canvas.drawRect(imageRect.left, r.top, r.left, r.bottom, scrimPaint)
        canvas.drawRect(r.right, r.top, imageRect.right, r.bottom, scrimPaint)

        // Thirds grid and border
        for (i in 1..2) {
            val x = r.left + r.width() * i / 3f
            val y = r.top + r.height() * i / 3f
            canvas.drawLine(x, r.top, x, r.bottom, gridPaint)
            canvas.drawLine(r.left, y, r.right, y, gridPaint)
        }
        canvas.drawRect(r, borderPaint)

        // Corner handles
        val l = handleLen
        canvas.drawLine(r.left, r.top, r.left + l, r.top, handlePaint)
        canvas.drawLine(r.left, r.top, r.left, r.top + l, handlePaint)
        canvas.drawLine(r.right, r.top, r.right - l, r.top, handlePaint)
        canvas.drawLine(r.right, r.top, r.right, r.top + l, handlePaint)
        canvas.drawLine(r.left, r.bottom, r.left + l, r.bottom, handlePaint)
        canvas.drawLine(r.left, r.bottom, r.left, r.bottom - l, handlePaint)
        canvas.drawLine(r.right, r.bottom, r.right - l, r.bottom, handlePaint)
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - l, handlePaint)
    }

    private fun computeImageRect() {
        val pad = 6f * density
        val availW = width - 2 * pad
        val availH = height - 2 * pad
        val scale = min(availW / sourceW, availH / sourceH)
        val w = sourceW * scale
        val h = sourceH * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        imageRect.set(left, top, left + w, top + h)
    }

    private fun scale(): Float = imageRect.width() / sourceW

    private fun cropOnScreen(): RectF {
        val s = scale()
        return RectF(
            imageRect.left + crop.left * s,
            imageRect.top + crop.top * s,
            imageRect.left + crop.right * s,
            imageRect.top + crop.bottom * s
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frame == null || sourceW == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                computeImageRect()
                val r = cropOnScreen()
                val x = event.x
                val y = event.y
                val withinX = x >= r.left - slop && x <= r.right + slop
                val withinY = y >= r.top - slop && y <= r.bottom + slop
                dragLeft = withinY && kotlin.math.abs(x - r.left) <= slop
                dragRight = withinY && !dragLeft && kotlin.math.abs(x - r.right) <= slop
                dragTop = withinX && kotlin.math.abs(y - r.top) <= slop
                dragBottom = withinX && !dragTop && kotlin.math.abs(y - r.bottom) <= slop
                dragMove = !(dragLeft || dragRight || dragTop || dragBottom) && r.contains(x, y)
                lastX = x
                lastY = y
                if (dragLeft || dragRight || dragTop || dragBottom || dragMove) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val s = scale()
                val dx = (event.x - lastX) / s
                val dy = (event.y - lastY) / s
                lastX = event.x
                lastY = event.y
                if (dragMove) clampMove(dx, dy) else resizeBy(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragLeft = false
                dragTop = false
                dragRight = false
                dragBottom = false
                dragMove = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clampMove(dx: Float, dy: Float) {
        val w = crop.width()
        val h = crop.height()
        val left = (crop.left + dx).coerceIn(0f, sourceW - w)
        val top = (crop.top + dy).coerceIn(0f, sourceH - h)
        crop.set(left, top, left + w, top + h)
    }

    private fun resizeBy(dx: Float, dy: Float) {
        var left = crop.left
        var top = crop.top
        var right = crop.right
        var bottom = crop.bottom
        if (dragLeft) left = (left + dx).coerceIn(0f, right - minSide)
        if (dragRight) right = (right + dx).coerceIn(left + minSide, sourceW.toFloat())
        if (dragTop) top = (top + dy).coerceIn(0f, bottom - minSide)
        if (dragBottom) bottom = (bottom + dy).coerceIn(top + minSide, sourceH.toFloat())

        val ratio = aspect
        if (ratio != null) {
            // Width drives the size; height follows. The side opposite to the dragged one stays put.
            var w = right - left
            if (!(dragLeft || dragRight)) w = (bottom - top) * ratio
            var h = w / ratio
            if (h > sourceH) { h = sourceH.toFloat(); w = h * ratio }
            if (w > sourceW) { w = sourceW.toFloat(); h = w / ratio }
            if (dragLeft) left = right - w else right = left + w
            if (dragTop) top = bottom - h else bottom = top + h
            if (left < 0f) { right -= left; left = 0f }
            if (right > sourceW) { left -= right - sourceW; right = sourceW.toFloat() }
            if (top < 0f) { bottom -= top; top = 0f }
            if (bottom > sourceH) { top -= bottom - sourceH; bottom = sourceH.toFloat() }
            left = max(0f, left)
            top = max(0f, top)
        }
        crop.set(left, top, right, bottom)
    }
}
