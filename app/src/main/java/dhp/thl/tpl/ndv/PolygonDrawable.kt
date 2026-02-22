package dhp.thl.tpl.ndv

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

class PolygonDrawable(
    private val polygon: RoundedPolygon,
    private val color: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@PolygonDrawable.color
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val scale = minOf(bounds.width(), bounds.height()).toFloat()
        matrix.reset()
        matrix.postScale(scale / 2f, scale / 2f)
        matrix.postTranslate(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        
        path.reset()
        polygon.toPath(path)
        path.transform(matrix)
        
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    
    fun setColor(newColor: Int) {
        paint.color = newColor
        invalidateSelf()
    }
}
