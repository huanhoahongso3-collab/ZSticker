package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dhp.thl.tpl.ndv.databinding.ActivityEasterEggBinding
import java.util.Random

class EasterEggActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEasterEggBinding
    private val random = Random()
    
    // Background mosaic images
    private val stickerPool = intArrayOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)
    
    // Emoji pool for the toast surprise
    private val emojiPool = arrayOf("✨", "🎉", "🔥", "🌈", "🎈", "🚀", "💎", "🍭", "🍀", "👻", "⭐")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEasterEggBinding.inflate(layoutInflater)
        setContentView(binding.root)

        generateBackgroundMosaic()

        // 1-10 Stickers + Random Emoji on Click
        binding.imgLogo.setOnClickListener {
            val stickerCount = random.nextInt(10) + 1
            val randomEmoji = emojiPool[random.nextInt(emojiPool.size)]
            
            val displayString = StringBuilder().apply {
                repeat(stickerCount) { append("🖼️ ") }
                append(randomEmoji)
            }.toString()

            Toast.makeText(this, displayString, Toast.LENGTH_SHORT).show()
        }

        // Long press center to return to app
        binding.imgLogo.setOnLongClickListener {
            finish()
            true
        }
    }

    private fun generateBackgroundMosaic() {
        binding.easterRoot.post {
            val width = binding.easterRoot.width
            val height = binding.easterRoot.height

            // Generate 40 random floating stickers
            for (i in 0 until 40) {
                val mosaicItem = ImageView(this).apply {
                    setImageResource(stickerPool[random.nextInt(stickerPool.size)])
                    
                    val size = ((random.nextInt(70) + 50) * resources.displayMetrics.density).toInt()
                    layoutParams = FrameLayout.LayoutParams(size, size).apply {
                        // Offset margins to scatter across screen
                        setMargins(
                            random.nextInt(width - size),
                            random.nextInt(height - size),
                            0, 0
                        )
                    }
                    
                    rotation = random.nextFloat() * 360f
                    alpha = 0.6f
                }
                // Add to root at index 0 to stay behind the logo
                binding.easterRoot.addView(mosaicItem, 0)
            }
        }
    }
}
