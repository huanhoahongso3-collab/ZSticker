package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
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
        private val androidStickers = arrayOf(
            "😎", "🐱", "🚀", "🌈", "🔥", "💎", "🍦", "🎸", "👾", "🦊",
            "🍀", "🍄", "⭐", "🌙", "🌍", "🎨", "🎭", "🍕", "🎈", "🍭",
            "🦄", "🌸", "⚡", "🐳", "🥨", "🥑", "🧿", "🛸", "🧸", "🕹️"
        )

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityEasterEggBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Generate the Android 13 style mosaic
            generateAndroid13Mosaic()

            // 1-20 Stickers + Random Emoji Toast
            binding.imgLogo.setOnClickListener {
                val stickerCount = random.nextInt(20) + 1
                val randomEmoji = androidStickers[random.nextInt(androidStickers.size)]

                val displayString = StringBuilder().apply {
                    repeat(stickerCount) { append("🖼️ ") }
                    append(randomEmoji)
                }.toString()

                Toast.makeText(this, displayString, Toast.LENGTH_SHORT).show()
            }

            binding.imgLogo.setOnLongClickListener {
                finish()
                true
            }
        }

        private fun generateAndroid13Mosaic() {
            binding.easterRoot.post {
                val width = binding.easterRoot.width
                val height = binding.easterRoot.height

                // Spawning 100 items to ensure high density across all screen sizes
                for (i in 0 until 100) {
                    // Random size: small (30dp) to very large (180dp)
                    val sizeBase = random.nextInt(150) + 30
                    val sizePx = (sizeBase * resources.displayMetrics.density).toInt()

                    val view: View = if (random.nextInt(3) == 0) { // 33% chance for local image
                        ImageView(this).apply {
                            setImageResource(localStickers[random.nextInt(localStickers.size)])
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    } else { // 66% chance for emoji sticker
                        TextView(this).apply {
                            text = androidStickers[random.nextInt(androidStickers.size)]
                            textSize = sizeBase.toFloat() / 2.2f
                            gravity = Gravity.CENTER
                        }
                    }

                    view.apply {
                        layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                            // Allow stickers to bleed off screen (Android 13 style)
                            // By subtracting sizePx from the random bound, we allow negative margins
                            leftMargin = random.nextInt(width + sizePx) - (sizePx / 1)
                            topMargin = random.nextInt(height + sizePx) - (sizePx / 1)
                        }
                        rotation = random.nextFloat() * 360f
                        alpha = 0f // Start invisible for fade-in
                    }

                    binding.easterRoot.addView(view, 0)

                    // Smooth fade-in animation
                    val fadeIn = AlphaAnimation(0f, 0.3f + random.nextFloat() * 0.5f).apply {
                        duration = 500
                        startOffset = (random.nextInt(500)).toLong()
                        fillAfter = true
                    }
                    view.startAnimation(fadeIn)
                }
            }
        }
}
