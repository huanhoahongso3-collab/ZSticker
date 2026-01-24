package dhp.thl.tpl.ndv

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class EasterEggActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
        private lateinit var imgLogo: ImageView
            private val random = Random()
            private val mosaicStickers = mutableListOf<View>()

            // Resource IDs for your specific images
            private val stickerRes = intArrayOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)
            private val emojiPool = mutableListOf<String>()

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)

                // 1. Setup Transparent Root
                rootLayout = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                setContentView(rootLayout)

                initEmojiPool()
                setupLogo()

                // 2. Spawn Mosaic after layout measurement
                rootLayout.post {
                    spawnDenseMosaic()
                    imgLogo.bringToFront() // Ensure logo stays on top
                }
            }

            private fun setupLogo() {
                imgLogo = ImageView(this)
                val size = (126 * resources.displayMetrics.density).toInt()
                val params = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
                imgLogo.layoutParams = params

                // Foreground Image
                imgLogo.setImageResource(R.drawable.ic_launcher_foreground)

                // Circular White Frame created programmatically
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(4, Color.LTGRAY)
                }
                imgLogo.background = shape

                imgLogo.elevation = 100f
                imgLogo.isClickable = true

                imgLogo.setOnClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    reshuffleMosaic()
                    showRandomEmojiToast()
                }

                rootLayout.addView(imgLogo)
            }

            private fun spawnDenseMosaic() {
                val width = rootLayout.width
                val height = rootLayout.height
                val density = resources.displayMetrics.density

                repeat(150) {
                    val sticker = ImageView(this)
                    val sizeDp = random.nextInt(60) + 70 // 70dp to 130dp
                    val sizePx = (sizeDp * density).toInt()

                    sticker.setImageResource(stickerRes[random.nextInt(stickerRes.size)])
                    sticker.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)

                    // Random scatter
                    sticker.translationX = random.nextInt(width).toFloat() - (sizePx / 2f)
                    sticker.translationY = random.nextInt(height).toFloat() - (sizePx / 2f)
                    sticker.rotation = random.nextFloat() * 360f
                    sticker.alpha = 0.7f

                    setupDraggable(sticker)
                    rootLayout.addView(sticker)
                    mosaicStickers.add(sticker)
                }
            }

            private fun reshuffleMosaic() {
                val width = rootLayout.width
                val height = rootLayout.height

                mosaicStickers.forEach { v ->
                    v.animate()
                    .translationX(random.nextInt(width).toFloat() - (v.width / 2f))
                    .translationY(random.nextInt(height).toFloat() - (v.height / 2f))
                    .rotation(random.nextFloat() * 360f)
                    .setDuration(700)
                    .setInterpolator(OvershootInterpolator())
                    .start()
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun setupDraggable(view: View) {
                view.setOnTouchListener(object : View.OnTouchListener {
                    private var dX = 0f
                    private var dY = 0f

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // "Pop up" effect
                                v.animate().scaleX(1.4f).scaleY(1.4f).alpha(1f).setDuration(150).start()
                                v.bringToFront()
                                imgLogo.bringToFront() // Force logo to stay top-most

                                dX = v.x - event.rawX
                                dY = v.y - event.rawY
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                v.x = event.rawX + dX
                                v.y = event.rawY + dY
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                // Settle back down
                                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.7f).setDuration(150).start()
                                return true
                            }
                        }
                        return false
                    }
                })
            }

            private fun showRandomEmojiToast() {
                val count = random.nextInt(10) + 5
                val sb = StringBuilder()
                repeat(count) {
                    sb.append(emojiPool[random.nextInt(emojiPool.size)]).append(" ")
                }
                Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_SHORT).show()
            }

            private fun initEmojiPool() {
                val ranges = arrayOf(
                    0x1F600..0x1F64F, // Faces
                    0x1F400..0x1F4FF, // Animals
                    0x1F300..0x1F3FF, // Food
                    0x1F680..0x1F6FF  // Travel
                )
                for (range in ranges) {
                    for (i in range) {
                        emojiPool.add(String(Character.toChars(i)))
                    }
                }
            }
}
