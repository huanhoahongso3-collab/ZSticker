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

        // Fixed: Using toChars and a safer list builder
        private val allAndroidStickers: List<String> by lazy {
            val list = mutableListOf<String>()
            val ranges = listOf(
                0x1F600..0x1F64F, // Faces
                0x1F400..0x1F4FF, // Animals
                0x1F300..0x1F3FF, // Food
                0x1F680..0x1F6FF, // Transport
                0x1F900..0x1F9FF  // Symbols
            )
            for (range in ranges) {
                for (codePoint in range) {
                    // FIXED: Changed toChars for proper Unicode handling
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
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                val pop = ScaleAnimation(1f, 1.1f, 1f, 1.1f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                                             duration = 100
                                             repeatMode = ScaleAnimation.REVERSE
                                             repeatCount = 1
                                         }
                                         it.startAnimation(pop)

                                         val count = random.nextInt(20) + 1
                                         val toastMsg = StringBuilder().apply {
                                             repeat(count) {
                                                 val randomIcon = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                                 append(randomIcon).append(" ")
                                             }
                                         }.toString()

                                         Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            }

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

                    for (i in 0 until 120) {
                        val sizeBase = random.nextInt(110) + 50
                        val sizePx = (sizeBase * resources.displayMetrics.density).toInt()

                        val view: View = if (random.nextInt(4) == 0) {
                            ImageView(this).apply {
                                setImageResource(localStickers[random.nextInt(localStickers.size)])
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        } else {
                            TextView(this).apply {
                                text = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                                textSize = sizeBase.toFloat() / 2.3f
                                gravity = Gravity.CENTER
                            }
                        }

                        val params = FrameLayout.LayoutParams(sizePx, sizePx)
                        params.leftMargin = random.nextInt(width + sizePx) - sizePx
                        params.topMargin = random.nextInt(height + sizePx) - sizePx

                        view.apply {
                            layoutParams = params
                            rotation = random.nextFloat() * 360f
                            alpha = 0f
                        }

                        binding.easterRoot.addView(view, 0)

                        val fadeIn = AlphaAnimation(0f, 0.3f + random.nextFloat() * 0.5f).apply {
                            duration = 800
                            // FIXED: Changed random.nextLong(1000) to random.nextInt(1000).toLong()
                            startOffset = random.nextInt(1000).toLong()
                            fillAfter = true
                        }
                        view.startAnimation(fadeIn)
                    }
            }
        }
}
