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
import java.util.*

class MoreOptionActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var imgLogo: ImageView
    private lateinit var txtMessage: TextView
    private val random = Random()

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
        "choose wisely between THL and TPL"
    )

    private var clickCount = 0
    private lateinit var txtClickCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(rootLayout)

        // Logo background (large and subtle)
        val bgLogo = ImageView(this).apply {
            val size = (400 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_launcher_foreground)
            alpha = 0.1f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rootLayout.addView(bgLogo)

        // Click count display
        txtClickCount = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                setMargins(0, 0, 0, (130 * resources.displayMetrics.density).toInt())
            }
            setTextColor(Color.WHITE)
            textSize = 24f
            text = "0"
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        rootLayout.addView(txtClickCount)

        // Text display
        txtMessage = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { 
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                setMargins(0, 0, 0, (100 * resources.displayMetrics.density).toInt())
            }
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }
        rootLayout.addView(txtMessage)

        // Center Logo
        imgLogo = ImageView(this).apply {
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
                setColor(Color.WHITE)
            }
            elevation = 100f
            isClickable = true
        }
        
        imgLogo.setOnClickListener {
            clickCount++
            txtClickCount.text = clickCount.toString()
            showRandomMessage()
            vibrate()
        }
        
        rootLayout.addView(imgLogo)
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
