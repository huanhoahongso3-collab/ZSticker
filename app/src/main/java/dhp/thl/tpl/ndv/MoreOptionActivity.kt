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
import androidx.appcompat.app.AppCompatActivity
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively
import androidx.lifecycle.lifecycleScope
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.launch
import android.content.res.Configuration
import java.util.*

class MoreOptionActivity : BaseActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var imgLogo: ImageView
    private lateinit var txtMessage: TextView
    private lateinit var txtCounter: TextView
    private lateinit var bgLogo: ImageView
    private val random = Random()
    private var clickCount = 0
    private val eggClicks = mutableSetOf<Int>()
    private var eggFailed = false

    private val messages = listOf(
        "THL loves you!",
        "THLs love you!",
        "King loves you",
        "Kings love you",
        "Welcome to K7A2",
        "K7A2 - A place you will never forget",
        "You're gay!",
        "Your gender is true",
        "It is real!",
        "Look at us!",
        "Lam used to love you",
        "Many secrets...",
        "Waiting for you....",
        "K7A2 Mod took only 1 week to made!",
        "Surprising that everyone is here!",
        "Include developers!",
        "phucdh0110.is-best.net",
        "EOL :(((((",
        "Goodbye!",
        "New dimensions!",
        "New trees!",
        "We are rich!",
        "Finally!",
        "A whole story....",
        "K391 - The End of Time",
        "I like coding!",
        "youtube.com/@Ilikecoding2010",
        "Aether!",
        "Nostalgia!",
        "1.8.9",
        "POV: You play Minecraft in 2013",
        "Thank you for playing",
        "2013, get me back!",
        "K7A2 (2021-2025)",
        "9th Grade!",
        "Say hello or goodbye to the world",
        "Depends on you",
        "CBH still the best!",
        "Never forget about this",
        "We will never forget you.......",
        "Lastest versions!",
        "Total of 5 dimensions",
        "Tip: You can build your house in Aether",
        "Lots of K7A2 Block",
        "Have funnnnnnnnnn",
        "It should been here before all of you",
        "Begins from rc1!",
        "It added in 2.x",
        "Have a good day",
        "It is the most important...",
        "Can't study without it",
        "Finally, it has been added",
        "Oww, building blocks",
        "Same but different from paintings!",
        "5.x - Last one",
        "Building block is a replacement for skins!",
        "Everyone is included!",
        "Instant mine in survival",
        "Wow, it's so fast!",
        "Only normal, not ores",
        "Almost 5in1!",
        "Stronger than before......",
        "To be continued..",
        "Unmaintained",
        "More than 80MB",
        "Near 100MB",
        "It's too big",
        "Bigger than The Man From The Fog about 4 times!",
        "Only you!",
        "You're stupid!",
        "YOU DESERVE THIS/THAT",
        "Spawn rates = your age",
        "More than before",
        "No longer spawn?!",
        "Durability = your date+month(+year) of birth!",
        "Efficiency = date+month+year",
        "Now more like the old one",
        "Spawned mob!",
        "No more bugs??????",
        "Ok, we are fine",
        "Spawn in everywhere except 1",
        "Full of loots!",
        "Only less/more between old/new version about 0.02MB",
        "Block = Face",
        "All of K7A2 armors now all related to your birthday!",
        "Bumped to 1.19!",
        "The Secret Update!",
        "Many new features is hidden....",
        "We are coming and stronger.....",
        "New boss!",
        "Trades are OP",
        "More debug tool",
        "New music added",
        "Sad or fun?",
        "Deadend!",
        "We all have skins!",
        "More spammable than before",
        "It can fly?",
        "Bugfixes",
        "Even have the whole class?",
        "No need to install other mod",
        "It can kill the Warden for us....",
        "End of support soon!",
        "Now came back and better than before",
        "Finally, it readded!",
        "It came back and stronger than before",
        "WTF?",
        "You can fly?",
        "Yes, you can",
        "Now you love TPL?",
        "TPL + HDH?",
        "What about NDV?",
        "Fall in love with NDV?",
        "Watching NDV in new class?",
        "TPL is not here anymore",
        "Never touch her when you don't have enough evidence.",
        "If you have evidences, just do anything because she's trying to angry",
        "Despite of not having done anything, you met her mom!",
        "DHP loves THL?????!!!!!",
        "Or TPL?",
        "Choose one",
        "TPL or THL",
        "choose wisely between THL and TPL",
        "bad dream with TPL?",
        "bad dream with both",
        "comeback with TPL in the bed",
        "trying to do something with TPL",
        "such a bad dream",
        "TPL is so cute",
        "trying to .... TPL",
        "bye bye",
        "not so ended at SDK 36",
        "a bad dream about TPL",
        "such a not so funny time with TPL",
        "Capture her!",
        "Bye bye TPL",
        "Have fun with PDA",
        "It's no fun trying to go to bed with TPL",
        "Don't do anything bad with TPL",
        "Welcome to SDK 41",
        "Say goodbye to TPL for the last time",
        "Banana is a codename for TPL",
        "Bye bye .... bye bye K7A2.... bye bye TPL... bye bye all",
        "Bye bye banana for the last time"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)

        rootLayout = FrameLayout(this@MoreOptionActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootLayout)

        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // Logo background (large and subtle)
        bgLogo = ImageView(this@MoreOptionActivity).apply {
            val size = (400 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_launcher_foreground)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isDark) Color.WHITE else Color.BLACK)
            }
            alpha = 0.1f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rootLayout.addView(bgLogo)

        // Click Counter
        txtCounter = TextView(this@MoreOptionActivity).apply {
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
                    if (eggClicks.contains(clickCount)) {
                        if (clickCount == 74) {
                            startActivity(android.content.Intent(this@MoreOptionActivity, AdvancedSettingsActivity::class.java))
                        } else {
                            eggFailed = true
                        }
                    } else {
                         eggClicks.add(clickCount)
                    }
                } else {
                    eggFailed = true
                }

                if (eggClicks.size == 3 && !eggFailed) {
                    startActivity(android.content.Intent(this@MoreOptionActivity, AdvancedSettingsActivity::class.java))
                    eggClicks.clear()
                    eggFailed = false
                    // Stay at current count
                }
            }
        }
        rootLayout.addView(txtCounter)

        // Text display
        txtMessage = TextView(this@MoreOptionActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { 
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                setMargins(0, 0, 0, (100 * resources.displayMetrics.density).toInt())
            }
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }
        rootLayout.addView(txtMessage)

        // Center Logo
        imgLogo = ImageView(this@MoreOptionActivity).apply {
            val size = (126 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
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
            showRandomMessage()
            vibrate()
        }
        
        rootLayout.addView(imgLogo)

        lifecycleScope.launch {
            monet.awaitMonetReady()
            val accentColor = if (materialColorEnabled) {
                monet.getAccentColor(this@MoreOptionActivity)
            } else {
                getColor(R.color.orange_primary)
            }

            // Always black background and white/static colors for vintage look
            rootLayout.setBackgroundColor(Color.BLACK)
            
            bgLogo.clearColorFilter()
            txtCounter.setTextColor(accentColor)
            txtMessage.setTextColor(accentColor)
            (imgLogo.background as? GradientDrawable)?.setColor(Color.WHITE)
        }
    }

    private fun showRandomMessage() {
        val msg = messages[random.nextInt(messages.size)]
        txtMessage.text = msg
        txtMessage.visibility = View.VISIBLE
        
        // Shake animation for the logo
        imgLogo.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                imgLogo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
            .start()
    }

    private fun vibrate() {
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
