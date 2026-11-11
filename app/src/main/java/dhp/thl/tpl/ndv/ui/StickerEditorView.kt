package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas for the sticker editor. Edits are kept as a list of operations (strokes and text) replayed on top
 * of the original sticker bitmap, which makes undo / redo exact.
 *
 * Tools: [Tool.ERASE] clears pixels, [Tool.RESTORE] paints the original photo back through the brush
 * (only possible when an original was saved), [Tool.DRAW] paints colour, [Tool.TEXT] places one movable,
 * scalable, rotatable text at a time. Two fingers zoom and pan the canvas (or transform the text while
 * the Text tool has one).
 */
class StickerEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { ERASE, RESTORE, DRAW, TEXT }

    private sealed interface Op
    private class StrokeOp(val tool: Tool, val path: Path, val size: Float, val color: Int) : Op
    private class TextOp(
        var text: String,
        var cx: Float,
        var cy: Float,
        var size: Float,
        var color: Int,
        var rotation: Float
    ) : Op

    var tool: Tool = Tool.ERASE
        set(value) {
            if (field == value) return
            if (field == Tool.TEXT) commitText()
            field = value
            cancelStroke()
        }

    /** Brush width in sticker pixels (Eraser / Restore / Draw). */
    var brushSize: Float = 36f

    /** Size of the next / active text in sticker pixels. */
    var textSize: Float = 64f
        set(value) {
            field = value
            activeText?.let {
                it.size = value
                refreshPreview()
            }
        }

    /** Colour for Draw strokes and Text. Changing it recolours the active text. */
    var drawColor: Int = Color.WHITE
        set(value) {
            field = value
            activeText?.let {
                it.color = value
                refreshPreview()
            }
        }

    var onHistoryChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var ready = false

    private lateinit var base: Bitmap
    private var original: Bitmap? = null
    private var originalShader: BitmapShader? = null
    private lateinit var committed: Bitmap
    private lateinit var preview: Bitmap
    private var bw = 1
    private var bh = 1

    private val ops = ArrayList<Op>()
    private val redoStack = ArrayList<Op>()
    private var activeText: TextOp? = null
    private var currentStroke: StrokeOp? = null
    private var lastBx = 0f
    private var lastBy = 0f

    // View transform: screen = bitmap * viewScale + (tx, ty)
    private var viewScale = 1f
    private var fitScale = 1f
    private var tx = 0f
    private var ty = 0f

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val checkerPaint = Paint()
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(90, 128, 128, 128)
    }

    // Touch state
    private var lastX = 0f
    private var lastY = 0f
    private var twoFinger = false
    private var movingText = false
    private var lastDist = 1f
    private var lastAngle = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    init {
        val cell = (12 * density).toInt().coerceAtLeast(6)
        val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(tile)
        val light = Paint().apply { color = 0xFFEEEEEE.toInt() }
        val dark = Paint().apply { color = 0xFFCCCCCC.toInt() }
        c.drawRect(0f, 0f, cell * 2f, cell * 2f, light)
        c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), dark)
        c.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, dark)
        checkerPaint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ------------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------------

    /** [originalPhoto] is only used when it has exactly the size of [sticker]. */
    fun setBitmaps(sticker: Bitmap, originalPhoto: Bitmap?) {
        base = if (sticker.config == Bitmap.Config.ARGB_8888) sticker else sticker.copy(Bitmap.Config.ARGB_8888, false)
        bw = base.width
        bh = base.height
        original = originalPhoto?.takeIf { it.width == bw && it.height == bh }
        originalShader = original?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        committed = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        preview = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        ops.clear()
        redoStack.clear()
        activeText = null
        currentStroke = null
        ready = true
        rebuildCommitted()
        fit()
        notifyHistory()
    }

    fun canRestore(): Boolean = original != null
    fun canUndo(): Boolean = ops.isNotEmpty() || activeText != null
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun hasChanges(): Boolean = ops.isNotEmpty() || activeText != null

    fun undo() {
        if (activeText != null) {
            activeText = null
            refreshPreview()
        } else if (ops.isNotEmpty()) {
            redoStack.add(ops.removeAt(ops.lastIndex))
            rebuildCommitted()
        }
        notifyHistory()
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            ops.add(redoStack.removeAt(redoStack.lastIndex))
            rebuildCommitted()
            notifyHistory()
        }
    }

    /** Puts a new text in the middle of the sticker (an earlier uncommitted text is kept as it is). */
    fun addText(text: String) {
        commitText()
        redoStack.clear()
        activeText = TextOp(text, bw / 2f, bh / 2f, textSize, drawColor, 0f)
        refreshPreview()
        notifyHistory()
    }

    fun commitText() {
        val t = activeText ?: return
        activeText = null
        ops.add(t)
        redoStack.clear()
        applyOp(Canvas(committed), t)
        refreshPreview()
        notifyHistory()
    }

    /** The edited sticker (any text still being placed is applied first). */
    fun renderResult(): Bitmap {
        commitText()
        return committed.copy(Bitmap.Config.ARGB_8888, false)
    }

    // ------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------

    private fun notifyHistory() {
        onHistoryChanged?.invoke()
    }

    private fun applyOp(canvas: Canvas, op: Op) {
        when (op) {
            is StrokeOp -> canvas.drawPath(op.path, strokePaint(op))
            is TextOp -> drawText(canvas, op)
        }
    }

    private fun strokePaint(op: StrokeOp): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = op.size
        when (op.tool) {
            Tool.ERASE -> xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            Tool.RESTORE -> shader = originalShader
            else -> color = op.color
        }
    }

    private fun drawText(canvas: Canvas, op: TextOp) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = op.color
            textSize = op.size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val outline = Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeWidth = op.size * 0.14f
            strokeJoin = Paint.Join.ROUND
            color = if (ColorUtils.calculateLuminance(op.color) > 0.5) Color.BLACK else Color.WHITE
        }
        val baseline = -(fill.ascent() + fill.descent()) / 2f
        canvas.save()
        canvas.translate(op.cx, op.cy)
        canvas.rotate(op.rotation)
        canvas.drawText(op.text, 0f, baseline, outline)
        canvas.drawText(op.text, 0f, baseline, fill)
        canvas.restore()
    }

    private fun rebuildCommitted() {
        val c = Canvas(committed)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.drawBitmap(base, 0f, 0f, null)
        ops.forEach { applyOp(c, it) }
        refreshPreview()
    }

    private fun refreshPreview() {
        if (!ready) return
        val c = Canvas(preview)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.drawBitmap(committed, 0f, 0f, null)
        currentStroke?.let { applyOp(c, it) }
        activeText?.let { applyOp(c, it) }
        invalidate()
    }

    private fun fit() {
        if (!ready || width == 0 || height == 0) return
        val pad = 16f * density
        fitScale = min((width - 2 * pad) / bw, (height - 2 * pad) / bh)
        viewScale = fitScale
        tx = (width - bw * viewScale) / 2f
        ty = (height - bh * viewScale) / 2f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    override fun onDraw(canvas: Canvas) {
        if (!ready) return
        val right = tx + bw * viewScale
        val bottom = ty + bh * viewScale
        canvas.save()
        canvas.clipRect(tx, ty, right, bottom)
        canvas.drawPaint(checkerPaint)
        canvas.translate(tx, ty)
        canvas.scale(viewScale, viewScale)
        canvas.drawBitmap(preview, 0f, 0f, bitmapPaint)
        canvas.restore()
        canvas.drawRect(tx, ty, right, bottom, outlinePaint)
    }

    // ------------------------------------------------------------------------------------------
    // Touch
    // ------------------------------------------------------------------------------------------

    private fun toBx(sx: Float) = (sx - tx) / viewScale
    private fun toBy(sy: Float) = (sy - ty) / viewScale

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!ready) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x
                lastY = e.y
                twoFinger = false
                movingText = false
                if (tool == Tool.TEXT) {
                    movingText = activeText != null
                } else {
                    beginStroke(toBx(e.x), toBy(e.y))
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    cancelStroke()
                    movingText = false
                    twoFinger = true
                    lastDist = max(1f, distance(e))
                    lastAngle = angle(e)
                    lastMidX = (e.getX(0) + e.getX(1)) / 2f
                    lastMidY = (e.getY(0) + e.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (twoFinger) {
                    if (e.pointerCount >= 2) twoFingerMove(e)
                } else if (currentStroke != null) {
                    extendStroke(toBx(e.x), toBy(e.y))
                } else if (movingText) {
                    activeText?.let {
                        it.cx += (e.x - lastX) / viewScale
                        it.cy += (e.y - lastY) / viewScale
                        refreshPreview()
                    }
                    lastX = e.x
                    lastY = e.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // The rest of a multi-finger gesture is ignored so a lifted finger can't start a stray stroke.
                if (e.pointerCount <= 2) twoFinger = true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishStroke()
                twoFinger = false
                movingText = false
            }
        }
        return true
    }

    private fun distance(e: MotionEvent) = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    private fun angle(e: MotionEvent) = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))

    private fun twoFingerMove(e: MotionEvent) {
        val dist = max(1f, distance(e))
        val ang = angle(e)
        val midX = (e.getX(0) + e.getX(1)) / 2f
        val midY = (e.getY(0) + e.getY(1)) / 2f
        val ratio = dist / lastDist

        val text = activeText
        if (tool == Tool.TEXT && text != null) {
            text.size = (text.size * ratio).coerceIn(8f, 600f)
            textSize = text.size
            text.rotation += Math.toDegrees((ang - lastAngle).toDouble()).toFloat()
            text.cx += (midX - lastMidX) / viewScale
            text.cy += (midY - lastMidY) / viewScale
            refreshPreview()
        } else {
            val newScale = (viewScale * ratio).coerceIn(fitScale * 0.9f, fitScale * 12f)
            val bx = (lastMidX - tx) / viewScale
            val by = (lastMidY - ty) / viewScale
            viewScale = newScale
            tx = midX - bx * viewScale
            ty = midY - by * viewScale
            invalidate()
        }
        lastDist = dist
        lastAngle = ang
        lastMidX = midX
        lastMidY = midY
    }

    private fun beginStroke(bx: Float, by: Float) {
        if (tool == Tool.RESTORE && original == null) return // nothing to restore from
        val path = Path().apply {
            moveTo(bx, by)
            lineTo(bx + 0.1f, by) // makes a single tap leave a dot
        }
        currentStroke = StrokeOp(tool, path, brushSize, drawColor)
        lastBx = bx
        lastBy = by
        refreshPreview()
    }

    private fun extendStroke(bx: Float, by: Float) {
        val stroke = currentStroke ?: return
        stroke.path.quadTo(lastBx, lastBy, (lastBx + bx) / 2f, (lastBy + by) / 2f)
        lastBx = bx
        lastBy = by
        refreshPreview()
    }

    private fun finishStroke() {
        val stroke = currentStroke ?: return
        currentStroke = null
        ops.add(stroke)
        redoStack.clear()
        applyOp(Canvas(committed), stroke)
        refreshPreview()
        notifyHistory()
    }

    private fun cancelStroke() {
        if (currentStroke != null) {
            currentStroke = null
            refreshPreview()
        }
    }
}
