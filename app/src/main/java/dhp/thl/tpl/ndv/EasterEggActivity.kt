package dhp.thl.tpl.ndv

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.monetcompat.core.MonetCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.content.res.Configuration

class EasterEggActivity : BaseActivity() {
    private lateinit var rootLayout: FrameLayout
    private lateinit var imgLogo: ImageView
    private lateinit var txtCounter: TextView
    private lateinit var bgLogo: ImageView
    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        
        rootLayout = FrameLayout(this@EasterEggActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootLayout)

        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val initialBg = if (isDark) Color.BLACK else Color.WHITE
        rootLayout.setBackgroundColor(initialBg)

        // Logo background (large and subtle)
        bgLogo = ImageView(this@EasterEggActivity).apply {
            val size = (400 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_launcher_foreground)
            alpha = 0.1f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rootLayout.addView(bgLogo)

        // Click Counter
        txtCounter = TextView(this@EasterEggActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER
                setMargins(0, 0, 0, (150 * resources.displayMetrics.density).toInt())
            }
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            text = "0"
            visibility = View.INVISIBLE
            isClickable = true
            setOnClickListener {
                if (clickCount == 10 || clickCount == 56 || clickCount == 74) {
                    showEasterEggDialog()
                }
            }
        }
        rootLayout.addView(txtCounter)

        // Center Logo
        imgLogo = ImageView(this@EasterEggActivity).apply {
            val size = (126 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_launcher_foreground)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDark) Color.WHITE else Color.BLACK)
            }
            elevation = 100f
            isClickable = true
        }

        imgLogo.setOnClickListener {
            clickCount++
            txtCounter.text = clickCount.toString()
            txtCounter.visibility = View.VISIBLE
            
            // Shake animation for the logo
            imgLogo.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction {
                    imgLogo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
                .start()
                
            vibrate()
        }
        rootLayout.addView(imgLogo)

        lifecycleScope.launch {
            monet.awaitMonetReady()
            val accentColor = if (materialColorEnabled) {
                monet.getAccentColor(this@EasterEggActivity)
            } else {
                getColor(R.color.orange_primary)
            }
            
            val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            
            // Fixed Neutral Colors for Surface (as per user request)
            val backgroundColor = if (materialColorEnabled) {
                monet.getBackgroundColor(this@EasterEggActivity)
            } else {
                if (isDark) Color.BLACK else Color.WHITE
            }
            rootLayout.setBackgroundColor(backgroundColor)
            
            txtCounter.setTextColor(accentColor)
            (imgLogo.background as? GradientDrawable)?.setColor(if (isDark) Color.WHITE else Color.BLACK)
        }
    }

    private fun showEasterEggDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(boldTitle("Welcome to dhpOS 44!"))
            .setMessage("Enjoy the good old days!")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun vibrate() {
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
