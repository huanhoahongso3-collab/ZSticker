package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dhp.thl.tpl.ndv.databinding.ActivityEasterEggBinding
import java.util.Random

class EasterEggActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEasterEggBinding
        private val random = Random()

        // Custom images from your drawables
        private val localStickers = intArrayOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)

        // Dynamically pulls all available Android system emojis from Unicode ranges
        private val allAndroidStickers: List<String> by lazy {
            val list = mutableListOf<String>()
            val ranges = listOf(
                0x1F600..0x1F64F, // Faces
                0x1F400..0x1F4FF, // Animals
                0x1F300..0x1F3FF, // Food
                0x1F680..0x1F6FF, // Transport/Objects
                0x1F900..0x1F9FF, // Activities
                0x1FA70..0x1FAFF  // Objects/Symbols
            )
            for (range in ranges) {
                for (codePoint in range) {
                    list.add(String(Character.toChars(codePoint)))
                }
            }
            list
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityEasterEggBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupAndroid13Mosaic()

            binding.imgLogo.setOnClickListener {
                // Vibrate and Pulse Animation
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                val pop = ScaleAnimation(1f, 1.1f, 1f, 1.1f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                                             duration = 100
                                             repeatMode = ScaleAnimation.REVERSE
                                             repeatCount = 1
                                         }
                                         it.startAnimation(pop)

                                         // Random Toast: 1-20 unique Unicode stickers from the whole pool
                                         val count = random.nextInt(20) + 1
                                         val toastMsg = StringBuilder().apply {
                                             repeat(count) {
                                                 val randomIcon = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                                 append(randomIcon).append(" ")
                                             }
                                         }.toString()

                                         Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            }

            // Long press to exit
            binding.imgLogo.setOnLongClickListener {
                finish()
                true
            }
        }

        private fun setupAndroid13Mosaic() {
            binding.easterRoot.post {
                val width = binding.easterRoot.width
                val height = binding.easterRoot.height
                if (width <= 0 || height <= 0) return@post

                    // Density: 120 items spread across and bleeding off screen
                    for (i in 0 until 120) {
                        val sizeBase = random.nextInt(110) + 50
                        val sizePx = (sizeBase * resources.displayMetrics.density).toInt()

                        val view: View = if (random.nextInt(4) == 0) { // 25% custom images
                            ImageView(this).apply {
                                setImageResource(localStickers[random.nextInt(localStickers.size)])
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        } else { // 75% random system emojis
                            TextView(this).apply {
                                text = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                textSize = sizeBase.toFloat() / 2.3f
                                gravity = Gravity.CENTER
                            }
                        }

                        val params = FrameLayout.LayoutParams(sizePx, sizePx)
                        // Coordinate math allowing bleed-off (negative margins)
                        params.leftMargin = random.nextInt(width + sizePx) - sizePx
                        params.topMargin = random.nextInt(height + sizePx) - sizePx

                        view.apply {
                            layoutParams = params
                            rotation = random.nextFloat() * 360f
                            alpha = 0f
                        }

                        binding.easterRoot.addView(view, 0)

                        // Fade-in animation
                        val fadeIn = AlphaAnimation(0f, 0.3f + random.nextFloat() * 0.5f).apply {
                            duration = 800
                            startOffset = random.nextLong(1000)
                            fillAfter = true
                        }
                        view.startAnimation(fadeIn)
                    }
            }
        }
}
