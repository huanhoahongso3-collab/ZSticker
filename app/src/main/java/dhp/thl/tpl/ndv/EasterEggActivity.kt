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

        private val localStickers = intArrayOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)

        // Programmatically generate a huge list of Android system emojis
        private val allAndroidStickers: List<String> by lazy {
            val list = mutableListOf<String>()
            // Ranges for common emojis (Smilies, Food, Activities, etc.)
            val ranges = listOf(
                0x1F600..0x1F64F, // Emoticons
                0x1F680..0x1F6C0, // Transport/Maps
                0x1F300..0x1F5FF, // Misc Symbols
                0x1F900..0x1F9FF  // Supplemental Symbols
            )
            for (range in ranges) {
                for (codePoint in range) {
                    list.add(String(Character.toCodePoints(codePoint)))
                }
            }
            list
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityEasterEggBinding.inflate(layoutInflater)
            setContentView(binding.root)

            generateFullAndroidMosaic()

            binding.imgLogo.setOnClickListener {
                // Vibrate
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                // Pop Animation
                val pop = ScaleAnimation(1f, 1.1f, 1f, 1.1f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                                             duration = 100
                                             repeatMode = ScaleAnimation.REVERSE
                                             repeatCount = 1
                                         }
                                         it.startAnimation(pop)

                                         // Random 1-20 Toast
                                         val count = random.nextInt(20) + 1
                                         val randomEmoji = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                         val toastMsg = StringBuilder().apply {
                                             repeat(count) { append("🖼️ ") }
                                             append(randomEmoji)
                                         }.toString()

                                         Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            }

            binding.imgLogo.setOnLongClickListener {
                finish()
                true
            }
        }

        private fun generateFullAndroidMosaic() {
            binding.easterRoot.post {
                val width = binding.easterRoot.width
                val height = binding.easterRoot.height
                if (width <= 0 || height <= 0) return@post

                    // Increase to 120 items for a dense "Android 13" look
                    for (i in 0 until 120) {
                        val sizeBase = random.nextInt(100) + 50
                        val sizePx = (sizeBase * resources.displayMetrics.density).toInt()

                        val view: View = if (random.nextInt(5) == 0) { // 20% local images
                            ImageView(this).apply {
                                setImageResource(localStickers[random.nextInt(localStickers.size)])
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        } else { // 80% system emojis
                            TextView(this).apply {
                                text = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                textSize = sizeBase.toFloat() / 2.2f
                                gravity = Gravity.CENTER
                            }
                        }

                        val params = FrameLayout.LayoutParams(sizePx, sizePx)
                        // Expanded bounds to ensure edge-to-edge bleed
                        params.leftMargin = random.nextInt(width + sizePx) - sizePx
                        params.topMargin = random.nextInt(height + sizePx) - sizePx

                        view.apply {
                            layoutParams = params
                            rotation = random.nextFloat() * 360f
                            alpha = 0f
                        }

                        binding.easterRoot.addView(view, 0)

                        val fadeIn = AlphaAnimation(0f, 0.3f + random.nextFloat() * 0.5f).apply {
                            duration = 700
                            startOffset = random.nextLong(1000)
                            fillAfter = true
                        }
                        view.startAnimation(fadeIn)
                    }
            }
        }
}
