package dhp.thl.tpl.ndv

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import dhp.thl.tpl.ndv.databinding.ActivityGifCropBinding
import dhp.thl.tpl.ndv.gif.GifPreview
import dhp.thl.tpl.ndv.gif.GifProcessor
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import java.io.File
import kotlin.concurrent.thread

/**
 * Crops a GIF while it plays: the animated GIF is shown under a draggable crop frame with a play / pause
 * button and a timeline to scrub through every frame. Saving applies the chosen region to all frames and
 * stores the result as a new sticker.
 */
class GifCropActivity : BaseActivity() {

    private lateinit var binding: ActivityGifCropBinding
    private lateinit var source: File
    private var preview: GifPreview? = null

    private val handler = Handler(Looper.getMainLooper())
    private var index = 0
    private var playing = false
    private var wasPlayingBeforePause = false
    private var resumeAfterScrub = false

    private val tick = object : Runnable {
        override fun run() {
            val p = preview ?: return
            if (!playing) return
            index = (index + 1) % p.frames.size
            showFrame(index, updateSeek = true)
            handler.postDelayed(this, p.delaysMs[index].toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        binding = ActivityGifCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.cropRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        val name = intent.getStringExtra(EXTRA_GIF_NAME)
        source = File(filesDir, name ?: "")
        if (name == null || !source.exists()) {
            finish()
            return
        }

        applyStyle()
        ExpressiveMotion.attachPressSpring(binding.btnBack, 0.90f)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlay.setOnClickListener { if (playing) pause() else play() }
        binding.btnSave.setOnClickListener { save() }

        binding.seekTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && preview != null) {
                    index = progress
                    showFrame(index, updateSeek = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                resumeAfterScrub = playing
                pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (resumeAfterScrub) play()
            }
        })

        thread {
            val loaded = GifPreview.load(source)
            runOnUiThread {
                if (isDestroyed) {
                    loaded?.recycle()
                    return@runOnUiThread
                }
                if (loaded == null) {
                    ToastUtils.showToast(this, getString(R.string.crop_load_failed))
                    finish()
                } else {
                    onPreviewLoaded(loaded)
                }
            }
        }
    }

    private fun onPreviewLoaded(loaded: GifPreview) {
        preview = loaded
        binding.loading.visibility = View.GONE
        binding.cropView.setSource(loaded.sourceWidth, loaded.sourceHeight)
        binding.seekTimeline.max = loaded.frames.size - 1
        binding.seekTimeline.isEnabled = true
        binding.btnPlay.isEnabled = true
        binding.btnSave.isEnabled = true
        buildAspectChips(loaded)
        showFrame(0, updateSeek = true)
        play()
    }

    private fun showFrame(i: Int, updateSeek: Boolean) {
        val p = preview ?: return
        binding.cropView.setFrame(p.frames[i])
        if (updateSeek) binding.seekTimeline.progress = i
        binding.txtTime.text = String.format(
            "%.1f / %.1f s", p.startOf(i) / 1000f, p.totalMs / 1000f
        )
    }

    private fun play() {
        val p = preview ?: return
        if (playing) return
        playing = true
        binding.btnPlay.setIconResource(R.drawable.ic_pause)
        binding.btnPlay.contentDescription = getString(R.string.crop_pause)
        handler.postDelayed(tick, p.delaysMs[index].toLong())
    }

    private fun pause() {
        playing = false
        handler.removeCallbacks(tick)
        binding.btnPlay.setIconResource(R.drawable.ic_play)
        binding.btnPlay.contentDescription = getString(R.string.crop_play)
    }

    private fun buildAspectChips(p: GifPreview) {
        val palette = ExpressiveThemeManager.resolvePalette(this)
        val density = resources.displayMetrics.density
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

        fun chip(label: String, ratio: Float?, checked: Boolean) = Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = checked
            isCheckedIconVisible = false
            chipStrokeWidth = 0f
            chipCornerRadius = 20f * density
            chipBackgroundColor = ColorStateList(states, intArrayOf(palette.secondaryContainer, palette.surfaceContainerLow))
            setTextColor(ColorStateList(states, intArrayOf(palette.onSecondaryContainer, palette.onSurface)))
            setOnClickListener { binding.cropView.setAspect(ratio) }
        }

        binding.aspectGroup.removeAllViews()
        binding.aspectGroup.addView(chip(getString(R.string.crop_aspect_free), null, true))
        binding.aspectGroup.addView(chip(getString(R.string.crop_aspect_square), 1f, false))
        binding.aspectGroup.addView(
            chip(getString(R.string.crop_aspect_original), p.sourceWidth.toFloat() / p.sourceHeight, false)
        )
    }

    private fun save() {
        pause()
        val rect = binding.cropView.getCropRect()
        val progressDialog = ProgressDialog.newInstance(getString(R.string.crop_saving))
        progressDialog.showSafe(supportFragmentManager, "gif_crop_save")
        binding.btnSave.isEnabled = false

        thread {
            val target = File(filesDir, "zsticker_${System.currentTimeMillis()}.gif")
            val ok = GifProcessor.transform(
                src = source,
                dst = target,
                maxSide = 512,
                crop = rect,
                frameStep = 1,
                quality = 10
            ) { progress -> runOnUiThread { progressDialog.updateProgress(progress) } }
            if (!ok) target.delete()
            runOnUiThread {
                progressDialog.dismissSafe()
                if (ok) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED, true))
                    finish()
                } else {
                    binding.btnSave.isEnabled = true
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }

    private fun applyStyle() {
        val palette: ExpressivePalette = ExpressiveThemeManager.resolvePalette(this)
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = !palette.isDark
        insets.isAppearanceLightNavigationBars = !palette.isDark

        binding.cropRoot.setBackgroundColor(palette.surface)
        binding.btnBack.setCardBackgroundColor(palette.secondaryContainer)
        binding.btnBack.strokeWidth = 0
        binding.imgBack.setColorFilter(palette.onSecondaryContainer)
        binding.txtHeaderTitle.setTextColor(palette.onSurface)

        binding.btnSave.backgroundTintList = ColorStateList.valueOf(palette.primary)
        binding.btnSave.setTextColor(palette.onPrimary)
        binding.btnPlay.backgroundTintList = ColorStateList.valueOf(palette.primary)
        binding.btnPlay.iconTint = ColorStateList.valueOf(palette.onPrimary)
        binding.seekTimeline.progressTintList = ColorStateList.valueOf(palette.primary)
        binding.seekTimeline.thumbTintList = ColorStateList.valueOf(palette.primary)
        binding.txtTime.setTextColor(palette.onSurfaceVariant)
        binding.loading.setIndicatorColor(palette.primary)
    }

    override fun onPause() {
        super.onPause()
        wasPlayingBeforePause = playing
        if (playing) pause()
    }

    override fun onResume() {
        super.onResume()
        if (wasPlayingBeforePause) {
            wasPlayingBeforePause = false
            play()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        preview?.recycle()
        preview = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GIF_NAME = "gif_name"
        const val EXTRA_SAVED = "gif_saved"
    }
}
