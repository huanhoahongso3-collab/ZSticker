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
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootLayout)

        initEmojiPool()
        setupLogo()

        rootLayout.post {
            spawnDenseMosaic()
            imgLogo.bringToFront()
        }
    }

    private fun setupLogo() {
        imgLogo = ImageView(this)
        val size = (126 * resources.displayMetrics.density).toInt()
        imgLogo.layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        
        // Forced Circular Outline
        imgLogo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        imgLogo.clipToOutline = true
        imgLogo.scaleType = ImageView.ScaleType.CENTER_CROP
        imgLogo.setImageResource(R.drawable.ic_launcher_foreground)
        
        imgLogo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
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

            setupGestureEngine(sticker)
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
                // We DON'T touch scaleX/Y here to keep user zoom state per session
                .setDuration(700)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureEngine(view: View) {
        var lastTouchX = 0f
        var lastTouchY = 0f
        var lastFingerDist = 0f
        var lastFingerAngle = 0f

        view.setOnTouchListener { v, event ->
            val pointerCount = event.pointerCount

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.bringToFront()
                    imgLogo.bringToFront()
                    v.alpha = 1.0f
                    
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (pointerCount == 2) {
                        lastFingerDist = calculateDistance(event)
                        lastFingerAngle = calculateAngle(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    // 1. Translation (Drag) 
                    // Use the first finger for position tracking to prevent jumps
                    val newX = event.rawX
                    val newY = event.rawY
                    
                    v.translationX += (newX - lastTouchX)
                    v.translationY += (newY - lastTouchY)
                    
                    lastTouchX = newX
                    lastTouchY = newY

                    // 2. Transformations (Scale & Rotate)
                    if (pointerCount == 2) {
                        // Scaling
                        val currentDist = calculateDistance(event)
                        if (lastFingerDist > 10f) {
                            val scaleFactor = currentDist / lastFingerDist
                            v.scaleX *= scaleFactor
                            v.scaleY *= scaleFactor
                            // Safety clamps
                            v.scaleX = v.scaleX.coerceIn(0.5f, 6.0f)
                            v.scaleY = v.scaleY.coerceIn(0.5f, 6.0f)
                        }
                        lastFingerDist = currentDist

                        // Rotation
                        val currentAngle = calculateAngle(event)
                        val deltaAngle = currentAngle - lastFingerAngle
                        // Multiply by 2.0f for "Larger" movement as requested
                        v.rotation += (deltaAngle * 2.0f)
                        lastFingerAngle = currentAngle
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // Re-anchor drag to the remaining finger to stop the "teleport" bug
                    val remainingIdx = if (event.actionIndex == 0) 1 else 0
                    lastTouchX = event.getRawX(remainingIdx)
                    lastTouchY = event.getRawY(remainingIdx)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.7f
                }
            }
            true
        }
    }

    private fun calculateDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return hypot(x, y)
    }

    private fun calculateAngle(event: MotionEvent): Float {
        val x = (event.getX(0) - event.getX(1)).toDouble()
        val y = (event.getY(0) - event.getY(1)).toDouble()
        return Math.toDegrees(atan2(y, x)).toFloat()
    }

    private fun showRandomEmojiToast() {
        val sb = StringBuilder()
        repeat(random.nextInt(10) + 5) {
            sb.append(emojiPool[random.nextInt(emojiPool.size)]).append(" ")
        }
        Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_SHORT).show()
    }

    private fun initEmojiPool() {
        val ranges = arrayOf(0x1F600..0x1F64F, 0x1F400..0x1F4FF, 0x1F300..0x1F3FF, 0x1F680..0x1F6FF)
        for (range in ranges) {
            for (i in range) emojiPool.add(String(Character.toChars(i)))
        }
    }
}
