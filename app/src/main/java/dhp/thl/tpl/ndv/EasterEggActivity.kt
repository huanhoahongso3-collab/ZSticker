package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
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

        // Use GlobalLayoutListener to ensure width/height are fully measured
        binding.easterRoot.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.easterRoot.viewTreeObserver.removeOnGlobalLayoutListener(this)
                setupAndroid13Mosaic()
            }
        })

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
                    append(allAndroidStickers[random.nextInt(allAndroidStickers.size)]).append(" ")
                }
            }.toString()
            Toast.makeText(this@EasterEggActivity, toastMsg, Toast.LENGTH_SHORT).show()
        }

        binding.imgLogo.setOnLongClickListener {
            finish()
            true
        }
    }

    private fun setupAndroid13Mosaic() {
        val width = binding.easterRoot.width
        val height = binding.easterRoot.height

        if (width <= 0 || height <= 0) return

        for (i in 0 until 120) {
            val sizeBase = random.nextInt(100) + 60 
            val sizePx = (sizeBase * resources.displayMetrics.density).toInt()
            
            val view: View = if (random.nextInt(4) == 0) { 
                ImageView(this).apply {
                    setImageResource(localStickers[random.nextInt(localStickers.size)])
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            } else { 
                TextView(this).apply {
                    text = allAndroidStickers[random.nextInt(allAndroidStickers.size)]
                    textSize = sizeBase.toFloat() / 2.5f
                    gravity = Gravity.CENTER
                }
            }

            val params = FrameLayout.LayoutParams(sizePx, sizePx)
            // Ensure they scatter across the ENTIRE screen area
            params.leftMargin = random.nextInt(width + (sizePx / 2)) - (sizePx / 2)
            params.topMargin = random.nextInt(height + (sizePx / 2)) - (sizePx / 2)

            view.apply {
                layoutParams = params
                rotation = random.nextFloat() * 360f
                alpha = 0f
            }
            
            binding.easterRoot.addView(view)
            
            val fadeIn = AlphaAnimation(0f, 0.4f + random.nextFloat() * 0.4f).apply {
                duration = 1000
                startOffset = random.nextInt(1500).toLong()
                fillAfter = true
            }
            view.startAnimation(fadeIn)
        }
        
        // IMPORTANT: Keep the logo on top of the stickers
        binding.imgLogo.bringToFront()
    }
}
