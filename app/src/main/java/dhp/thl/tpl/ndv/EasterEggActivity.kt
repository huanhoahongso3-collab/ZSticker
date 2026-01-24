package dhp.thl.tpl.ndv

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.math.atan2
import kotlin.math.hypot

class EasterEggActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var imgLogo: ImageView
    private val random = Random()
    private val mosaicStickers = mutableListOf<View>()
    private val stickerRes = intArrayOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)
    private val emojiPool = mutableListOf<String>()
    private var isToastActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(rootLayout)
        initEmojiPool()
        setupLogo()
        rootLayout.post { spawnDenseMosaic(); imgLogo.bringToFront() }
    }

    private fun setupLogo() {
        imgLogo = ImageView(this)
        val size = (126 * resources.displayMetrics.density).toInt()
        imgLogo.layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        imgLogo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setOval(0, 0, view.width, view.height) }
        }
        imgLogo.clipToOutline = true
        imgLogo.scaleType = ImageView.ScaleType.CENTER_CROP
        imgLogo.setImageResource(R.drawable.ic_launcher_foreground)
        imgLogo.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        imgLogo.elevation = 100f
        imgLogo.setOnClickListener { v ->
            if (!isToastActive) {
                isToastActive = true
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                reshuffleMosaic()
                showRandomEmojiToast()
                Handler(Looper.getMainLooper()).postDelayed({ isToastActive = false }, 2000)
            }
        }
        rootLayout.addView(imgLogo)
    }

    private fun spawnDenseMosaic() {
        val width = rootLayout.width
        val height = rootLayout.height
        val density = resources.displayMetrics.density
        repeat(150) {
            val sticker = ImageView(this)
            val sizePx = ((random.nextInt(60) + 70) * density).toInt()
            sticker.setImageResource(stickerRes[random.nextInt(stickerRes.size)])
            sticker.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            sticker.translationX = random.nextInt(width).toFloat() - (sizePx / 2f)
            sticker.translationY = random.nextInt(height).toFloat() - (sizePx / 2f)
            sticker.rotation = random.nextFloat() * 360f
            sticker.alpha = 0.7f
            setupTransformEngine(sticker)
            rootLayout.addView(sticker)
            mosaicStickers.add(sticker)
        }
    }

    private fun reshuffleMosaic() {
        mosaicStickers.forEach { v ->
            v.animate()
                .translationX(random.nextInt(rootLayout.width).toFloat() - (v.width / 2f))
                .translationY(random.nextInt(rootLayout.height).toFloat() - (v.height / 2f))
                .rotation(random.nextFloat() * 360f)
                // Note: .scaleX/Y are NOT modified here to persist zoom
                .setDuration(700)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTransformEngine(view: View) {
        var lastX = 0f
        var lastY = 0f
        var lastRotation = 0f
        var lastDistance = 0f

        view.setOnTouchListener { v, event ->
            // Use parent-relative coordinates to prevent rotation-induced jitter
            val parentX = event.rawX
            val parentY = event.rawY

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.bringToFront()
                    imgLogo.bringToFront()
                    lastX = parentX
                    lastY = parentY
                    v.alpha = 1.0f
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastDistance = getDistance(event)
                        lastRotation = getAngle(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    // 1. DRAG (Works for 1 or 2 fingers)
                    val deltaX = parentX - lastX
                    val deltaY = parentY - lastY
                    v.translationX += deltaX
                    v.translationY += deltaY
                    lastX = parentX
                    lastY = parentY

                    // 2. ZOOM & ROTATE (2 fingers only)
                    if (event.pointerCount == 2) {
                        // Zoom
                        val currentDist = getDistance(event)
                        if (lastDistance > 10f) {
                            val scaleFactor = currentDist / lastDistance
                            v.scaleX *= scaleFactor
                            v.scaleY *= scaleFactor
                            v.scaleX = v.scaleX.coerceIn(0.4f, 7.0f)
                            v.scaleY = v.scaleY.coerceIn(0.4f, 7.0f)
                        }
                        lastDistance = currentDist

                        // Rotate (Amplify for "Larger" feel)
                        val currentAngle = getAngle(event)
                        val deltaAngle = currentAngle - lastRotation
                        v.rotation += (deltaAngle * 1.8f) 
                        lastRotation = currentAngle
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.7f
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // When lifting a finger, reset drag anchors to the remaining finger
                    val remainingIdx = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getRawX(remainingIdx)
                    lastY = event.getRawY(remainingIdx)
                }
            }
            true
        }
    }

    private fun getDistance(e: MotionEvent): Float {
        val x = e.getX(0) - e.getX(1)
        val y = e.getY(0) - e.getY(1)
        return hypot(x, y)
    }

    private fun getAngle(e: MotionEvent): Float {
        val deltaX = (e.getX(0) - e.getX(1)).toDouble()
        val deltaY = (e.getY(0) - e.getY(1)).toDouble()
        return Math.toDegrees(atan2(deltaY, deltaX)).toFloat()
    }

    private fun showRandomEmojiToast() {
        val sb = StringBuilder()
        repeat(random.nextInt(10) + 5) { sb.append(emojiPool[random.nextInt(emojiPool.size)]).append(" ") }
        Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_SHORT).show()
    }

    private fun initEmojiPool() {
        val ranges = arrayOf(0x1F600..0x1F64F, 0x1F400..0x1F4FF, 0x1F300..0x1F3FF, 0x1F680..0x1F6FF)
        for (range in ranges) { for (i in range) emojiPool.add(String(Character.toChars(i))) }
    }
}
