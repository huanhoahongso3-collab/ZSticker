package dhp.thl.tpl.ndv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    
    private val allAndroidStickers: List<String> by lazy {
        val list = mutableListOf<String>()
        val ranges = listOf(0x1F600..0x1F64F, 0x1F400..0x1F4FF, 0x1F300..0x1F3FF, 0x1F680..0x1F6FF)
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

        // Wait for the UI to settle before generating stickers
        Handler(Looper.getMainLooper()).postDelayed({
            generateMosaic()
        }, 300)

        binding.imgLogo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            
            // Pop animation for the logo
            val pop = ScaleAnimation(1f, 1.15f, 1f, 1.15f, 
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, 
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 150
                repeatMode = ScaleAnimation.REVERSE
                repeatCount = 1
            }
            it.startAnimation(pop)

            // Random Toast with 1-20 unique stickers
            val count = random.nextInt(20) + 1
            val toastMsg = StringBuilder().apply {
                repeat(count) {
                    append(allAndroidStickers[random.nextInt(allAndroidStickers.size)]).append(" ")
                }
            }.toString()
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        }

        binding.imgLogo.setOnLongClickListener {
            finish()
            true
        }
    }

    private fun generateMosaic() {
        val root = binding.easterRoot
        val screenWidth = root.width
        val screenHeight = root.height

        // If screen hasn't measured yet, don't proceed
        if (screenWidth <= 0 || screenHeight <= 0) return

        for (i in 0 until 100) {
            val sizeBase = random.nextInt(80) + 60 // Size between 60-140dp
            val sizePx = (sizeBase * resources.displayMetrics.density).toInt()
            
            val view: View = if (random.nextInt(4) == 0) { 
                ImageView(this).apply {
                    setImageResource(localStickers[random.nextInt(localStickers.size)])
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            } else { 
                TextView(this).apply {
                    text = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                    textSize = sizeBase.toFloat() / 2.5f
                    gravity = Gravity.CENTER
                }
            }

            // Android 13 Style: Allow center of sticker to be anywhere on screen
            // This ensures they bleed off the edges correctly
            val params = FrameLayout.LayoutParams(sizePx, sizePx)
            params.leftMargin = random.nextInt(screenWidth) - (sizePx / 2)
            params.topMargin = random.nextInt(screenHeight) - (sizePx / 2)

            view.apply {
                layoutParams = params
                rotation = random.nextFloat() * 360f
                alpha = 0f
            }
            
            // Add stickers at the bottom of the view stack
            root.addView(view, 0)
            
            // Subtle fade-in
            val fadeIn = AlphaAnimation(0f, 0.4f + random.nextFloat() * 0.4f).apply {
                duration = 800
                startOffset = random.nextInt(1000).toLong()
                fillAfter = true
            }
            view.startAnimation(fadeIn)
        }
        
        // Force the logo to stay on top just in case
        binding.imgLogo.bringToFront()
    }
}
