package dhp.thl.tpl.ndv

import android.annotation.SuppressLint
import android.graphics.Color
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
                imgLogo.scaleType = ImageView.ScaleType.CENTER_CROP
                imgLogo.setImageResource(R.drawable.ic_launcher_foreground)

                imgLogo.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#EEEEEE"))
                }
                imgLogo.clipToOutline = true
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

                    setupDraggableAndRotatable(sticker)
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
                    .setDuration(700)
                    .setInterpolator(OvershootInterpolator())
                    .start()
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun setupDraggableAndRotatable(view: View) {
                view.setOnTouchListener(object : View.OnTouchListener {
                    private var dX = 0f
                    private var dY = 0f
                    private var lastRotation = 0f
                    private var angleOffset = 0f

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.animate().scaleX(1.4f).scaleY(1.4f).alpha(1f).setDuration(150).start()
                                v.bringToFront()
                                imgLogo.bringToFront()

                                dX = v.x - event.rawX
                                dY = v.y - event.rawY
                                return true
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                // Calculate initial angle between two fingers
                                val x = event.getX(0) - event.getX(1)
                                val y = event.getY(0) - event.getY(1)
                                angleOffset = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
                                lastRotation = v.rotation
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                // Handle Drag
                                v.x = event.rawX + dX
                                v.y = event.rawY + dY

                                // Handle Rotation if two fingers are present
                                if (event.pointerCount == 2) {
                                    val x = event.getX(0) - event.getX(1)
                                    val y = event.getY(0) - event.getY(1)
                                    val currentAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
                                    v.rotation = lastRotation + (currentAngle - angleOffset)
                                }
                                return true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.7f).setDuration(150).start()
                                return true
                            }
                        }
                        return false
                    }
                })
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
