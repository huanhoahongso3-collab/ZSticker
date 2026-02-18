package dhp.thl.tpl.ndv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.math.atan2
import kotlin.math.hypot

class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var imgLogo: ImageView
    private var rotateHandle: ImageView? = null
    private var activeSticker: View? = null

    // Secret Trigger Variables
    private var logoTapCount = 0
    private var lastTapTime: Long = 0
    private var isLongPressTriggered = false

    // Groups Logic
    // 0: THL, 1: TPL, 2: NDV, 3: THL+TPL, 4: ALL
    private val standardGroups = listOf(
        listOf(R.drawable.thl),
        listOf(R.drawable.tpl),
        listOf(R.drawable.ndv),
        listOf(R.drawable.thl, R.drawable.tpl),
        listOf(R.drawable.thl, R.drawable.tpl, R.drawable.ndv)
    )
    
    private val cycle = mutableListOf<Int>()
    private var currentCycleIndex = 0

    // UI Feedback & Timers
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        rotateHandle?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            rotateHandle?.visibility = View.GONE
        }?.start()
    }

    private val random = Random()
    private val mosaicStickers = mutableListOf<View>()
    private val emojiPool = mutableListOf<String>()
    private var isToastActive = false

    // Long Press Logic
    private val doubleTapDelay = 1000L // 1 second hold for group switch
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        logoTapCount = 0 // Reset tap count if held
        switchGroup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootLayout)

        initEmojiPool()
        setupLogo()
        setupRotateHandle()

        // Initial Shuffle
        generateNextCycle()
        
        rootLayout.post {
            spawnDenseMosaic(cycle[0])
            imgLogo.bringToFront()
        }
    }

    private fun generateNextCycle() {
        val lastGroup = if (cycle.isNotEmpty()) cycle.last() else -1
        cycle.clear()
        
        // Generate a random permutation of 0..4
        val temp = mutableListOf(0, 1, 2, 3, 4)
        temp.shuffle(random)

        // Ensure no back-to-back repeats between cycles
        if (temp[0] == lastGroup) {
            // Swap first with last if repeat occurs
            Collections.swap(temp, 0, temp.lastIndex)
        }
        
        cycle.addAll(temp)
        currentCycleIndex = 0
    }

    private fun switchGroup() {
        currentCycleIndex++
        if (currentCycleIndex >= cycle.size) {
            generateNextCycle()
        }
        
        val groupIndex = cycle[currentCycleIndex]
        
        // Clear previous stickers
        mosaicStickers.forEach { rootLayout.removeView(it) }
        mosaicStickers.clear()

        spawnDenseMosaic(groupIndex)
        imgLogo.bringToFront()
        activeSticker = null
        rotateHandle?.visibility = View.GONE
        
        vibrate()
        val lang = getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "system") ?: "system"
        val isVi = if (lang == "system") Locale.getDefault().language == "vi" else lang == "vi"
        val message = if (isVi) "Đã thay đổi nhóm!" else "Changed Group!"
        ToastUtils.showToast(this, message)
    }
    
    private fun vibrate() {
         window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun setupLogo() {
        imgLogo = ImageView(this)
        val size = (126 * resources.displayMetrics.density).toInt()
        imgLogo.layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }

        imgLogo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        imgLogo.clipToOutline = true
        imgLogo.scaleType = ImageView.ScaleType.CENTER_CROP
        imgLogo.setImageResource(R.drawable.ic_launcher_foreground)
        imgLogo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        imgLogo.elevation = 100f

        imgLogo.isClickable = true
        imgLogo.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressTriggered = false
                    longPressHandler.postDelayed(longPressRunnable, doubleTapDelay)
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (event.action == MotionEvent.ACTION_UP) {
                         if (!isLongPressTriggered) {
                             // Click Action: Reshuffle (keep zoom)
                             reshuffleMosaic()

                             // Secret Game Trigger (20 taps)
                             val now = System.currentTimeMillis()
                             logoTapCount = if (now - lastTapTime < 500) logoTapCount + 1 else 1
                             lastTapTime = now
                             if (logoTapCount >= 20) {
                                 logoTapCount = 0
                                 val lang = getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "system") ?: "system"
                                 val isVi = if (lang == "system") Locale.getDefault().language == "vi" else lang == "vi"
                                 val message = if (isVi) "Trò chơi bí mật đã được kích hoạt!" else "Secret game triggered!"
                                 ToastUtils.showToast(this, message)
                                 startActivity(Intent(this, SystemOptimizationActivity::class.java))
                             }
                         }
                    }
                    true
                }
                else -> false
            }
        }
        
        rootLayout.addView(imgLogo)
    }

    private fun setupRotateHandle() {
        val size = (32 * resources.displayMetrics.density).toInt()
        rotateHandle = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#99FFFFFF"))
                setStroke(2, Color.parseColor("#66000000"))
            }
            setImageResource(android.R.drawable.ic_menu_rotate)
            alpha = 0f
            elevation = 110f
            visibility = View.GONE
        }
        rootLayout.addView(rotateHandle)
    }

    private fun spawnDenseMosaic(groupIndex: Int) {
        val width = rootLayout.width
        val height = rootLayout.height
        val density = resources.displayMetrics.density
        
        val currentDrawables = standardGroups[groupIndex]
        
        repeat(150) {
            val sticker = ImageView(this)
            val sizePx = ((random.nextInt(60) + 70) * density).toInt()
            sticker.setImageResource(currentDrawables[random.nextInt(currentDrawables.size)])
            sticker.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)

            sticker.translationX = random.nextInt(width).toFloat() - (sizePx / 2f)
            sticker.translationY = random.nextInt(height).toFloat() - (sizePx / 2f)
            sticker.rotation = random.nextFloat() * 360f
            sticker.alpha = 0.7f

            setupTransformations(sticker)
            rootLayout.addView(sticker)
            mosaicStickers.add(sticker)
        }
    }

    private fun reshuffleMosaic() {
        hideHandler.post(hideRunnable)
        if (!isToastActive) { 
            isToastActive = true
            showRandomEmojiToast()
            hideHandler.postDelayed({ isToastActive = false }, 2000)
        }
        
        mosaicStickers.forEach { v ->
            v.animate()
            .translationX(random.nextInt(rootLayout.width).toFloat() - (v.width / 2f))
            .translationY(random.nextInt(rootLayout.height).toFloat() - (v.height / 2f))
            .rotation(random.nextFloat() * 360f)
            // Scale is NOT animated here, preserving user zoom if any (or default)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator())
            .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTransformations(view: View) {
        var lastTouchX = 0f
        var lastTouchY = 0f
        var lastFingerDist = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeSticker = v
                    v.bringToFront()
                    imgLogo.bringToFront()
                    showHandle(v)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    v.alpha = 1.0f
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) lastFingerDist = calculateDist(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    v.translationX += (event.rawX - lastTouchX)
                    v.translationY += (event.rawY - lastTouchY)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    updateHandlePosition(v)

                    if (event.pointerCount == 2) {
                        val currentDist = calculateDist(event)
                        val scaleFactor = currentDist / lastFingerDist
                        v.scaleX = (v.scaleX * scaleFactor).coerceIn(0.4f, 8.0f)
                        v.scaleY = (v.scaleY * scaleFactor).coerceIn(0.4f, 8.0f)
                        lastFingerDist = currentDist
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.7f
                    startHideTimer()
                }
            }
            true
        }

        rotateHandle?.setOnTouchListener { _, event ->
            val sticker = activeSticker ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> hideHandler.removeCallbacks(hideRunnable)
                MotionEvent.ACTION_MOVE -> {
                    val centerX = sticker.x + sticker.width / 2
                    val centerY = sticker.y + sticker.height / 2
                    val angle = Math.toDegrees(atan2((event.rawY - centerY).toDouble(), (event.rawX - centerX).toDouble())).toFloat()
                    sticker.rotation = angle + 90f
                    updateHandlePosition(sticker)
                }
                MotionEvent.ACTION_UP -> startHideTimer()
            }
            true
        }
    }

    private fun showHandle(sticker: View) {
        hideHandler.removeCallbacks(hideRunnable)
        updateHandlePosition(sticker)
        rotateHandle?.visibility = View.VISIBLE
        rotateHandle?.animate()?.alpha(1.0f)?.setDuration(150)?.start()
        rotateHandle?.bringToFront()
    }

    private fun startHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 500)
    }

    private fun updateHandlePosition(sticker: View) {
        val handle = rotateHandle ?: return
        val radius = (sticker.height * sticker.scaleY) / 2 + (20 * resources.displayMetrics.density)
        val angleRad = Math.toRadians((sticker.rotation - 90).toDouble())
        val centerX = sticker.x + sticker.width / 2
        val centerY = sticker.y + sticker.height / 2
        handle.x = (centerX + radius * Math.cos(angleRad)).toFloat() - handle.width / 2
        handle.y = (centerY + radius * Math.sin(angleRad)).toFloat() - handle.height / 2
    }

    private fun calculateDist(event: MotionEvent): Float = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private fun showRandomEmojiToast() {
        val sb = StringBuilder()
        repeat(random.nextInt(10) + 5) { sb.append(emojiPool[random.nextInt(emojiPool.size)]).append(" ") }
        ToastUtils.showToast(this, sb.toString().trim())
    }

    private fun initEmojiPool() {
        val ranges = arrayOf(0x1F600..0x1F64F, 0x1F400..0x1F4FF, 0x1F300..0x1F3FF, 0x1F680..0x1F6FF)
        for (range in ranges) { for (i in range) emojiPool.add(String(Character.toChars(i))) }
    }
}