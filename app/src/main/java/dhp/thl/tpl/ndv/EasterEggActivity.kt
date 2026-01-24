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
                reshuffleMosaic() // Note: Scale is NOT reset here now
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

            setupTransformations(sticker)
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
                // v.scale is NOT changed here, so it persists
                .setDuration(700)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTransformations(view: View) {
        var dX = 0f
        var dY = 0f
        var lastAngle = 0f
        var lastDist = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.bringToFront()
                    imgLogo.bringToFront()
                    // Capture initial offset for dragging
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.alpha = 1.0f
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        // Initialize rotation and scale trackers
                        lastAngle = calculateAngle(event)
                        lastDist = calculateDist(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        // Standard Drag
                        v.x = event.rawX + dX
                        v.y = event.rawY + dY
                    } else if (event.pointerCount == 2) {
                        // Handle Zoom (Scale)
                        val currentDist = calculateDist(event)
                        if (lastDist > 0) {
                            val ratio = currentDist / lastDist
                            v.scaleX *= ratio
                            v.scaleY *= ratio
                            // Constraints
                            v.scaleX = v.scaleX.coerceIn(0.3f, 8.0f)
                            v.scaleY = v.scaleY.coerceIn(0.3f, 8.0f)
                        }
                        lastDist = currentDist

                        // Handle Rotation
                        val currentAngle = calculateAngle(event)
                        val deltaAngle = currentAngle - lastAngle
                        // Multiply by 2.0f for that "larger" rotation feel
                        v.rotation += (deltaAngle * 2.0f)
                        lastAngle = currentAngle
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.7f
                }
                
                MotionEvent.ACTION_POINTER_UP -> {
                    // Reset trackers to prevent jumping when one finger leaves
                    if (event.pointerCount > 1) {
                        val remainingIdx = if (event.actionIndex == 0) 1 else 0
                        dX = v.x - event.getRawX(remainingIdx)
                        dY = v.y - event.getRawY(remainingIdx)
                    }
                }
            }
            true
        }
    }

    private fun calculateAngle(event: MotionEvent): Float {
        val x = (event.getX(0) - event.getX(1)).toDouble()
        val y = (event.getY(0) - event.getY(1)).toDouble()
        return Math.toDegrees(atan2(y, x)).toFloat()
    }

    private fun calculateDist(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return hypot(x, y)
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
