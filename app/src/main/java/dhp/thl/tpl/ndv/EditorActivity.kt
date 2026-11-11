package dhp.thl.tpl.ndv

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.data.OriginalStore
import dhp.thl.tpl.ndv.databinding.ActivityEditorBinding
import dhp.thl.tpl.ndv.ui.StickerEditorView.Tool
import dhp.thl.tpl.ndv.ui.showMonetDialog
import dhp.thl.tpl.ndv.ui.showTextInputDialog
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Sticker editor: eraser, restore brush (brings back pixels removed by background removal), drawing and
 * text, with undo / redo. Saving writes a new sticker and leaves the source untouched.
 */
class EditorActivity : BaseActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var stickerName: String
    private lateinit var palette: ExpressivePalette
    private lateinit var toolViews: Map<Tool, LinearLayout>
    private var currentTool = Tool.ERASE

    // Remembered brush / text size per tool (SeekBar progress 0..156 = size 4..160)
    private val sizeByTool = mutableMapOf(Tool.ERASE to 36, Tool.RESTORE to 36, Tool.DRAW to 8, Tool.TEXT to 56)

    private val swatchColors = intArrayOf(
        Color.WHITE, Color.BLACK, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(),
        0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt()
    )
    private val swatchViews = mutableListOf<View>()
    private var selectedColor = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.editorRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        val name = intent.getStringExtra(EXTRA_STICKER_NAME)
        val file = File(filesDir, name ?: "")
        if (name == null || !file.exists()) {
            finish()
            return
        }
        stickerName = name

        palette = ExpressiveThemeManager.resolvePalette(this)
        toolViews = mapOf(
            Tool.ERASE to binding.toolErase,
            Tool.RESTORE to binding.toolRestore,
            Tool.DRAW to binding.toolDraw,
            Tool.TEXT to binding.toolText
        )
        applyStyle()
        buildSwatches()

        ExpressiveMotion.attachPressSpring(binding.btnBack, 0.90f)
        binding.btnBack.setOnClickListener { requestExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestExit()
        })

        toolViews.forEach { (tool, view) -> view.setOnClickListener { selectTool(tool) } }
        binding.btnUndo.setOnClickListener { binding.editorView.undo() }
        binding.btnRedo.setOnClickListener { binding.editorView.redo() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnAddText.setOnClickListener {
            showTextInputDialog(
                title = getString(R.string.editor_add_text),
                hint = getString(R.string.editor_text_hint),
                confirmLabel = getString(R.string.ok)
            ) { text -> binding.editorView.addText(text) }
        }

        binding.seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) applySize(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.editorView.onHistoryChanged = { updateHistoryButtons() }
        updateHistoryButtons()

        thread {
            val sticker: Bitmap? = BitmapFactory.decodeFile(file.absolutePath)
            val original: Bitmap? = OriginalStore.load(this, name)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (sticker == null) {
                    ToastUtils.showToast(this, getString(R.string.failed))
                    finish()
                } else {
                    binding.loading.visibility = View.GONE
                    binding.editorView.setBitmaps(sticker, original)
                    toolViews[Tool.RESTORE]?.alpha = if (binding.editorView.canRestore()) 1f else 0.4f
                    selectTool(Tool.ERASE)
                }
            }
        }
    }

    private fun applySize(progress: Int) {
        sizeByTool[currentTool] = progress
        val value = (progress + 4).toFloat()
        if (currentTool == Tool.TEXT) binding.editorView.textSize = value * 2f
        else binding.editorView.brushSize = value
    }

    private fun selectTool(tool: Tool) {
        if (tool == Tool.RESTORE && !binding.editorView.canRestore()) {
            ToastUtils.showToast(this, getString(R.string.editor_no_original))
            return
        }
        currentTool = tool
        binding.editorView.tool = tool
        binding.editorView.drawColor = selectedColor

        toolViews.forEach { (t, view) -> styleTool(view, t == tool) }
        val usesColor = tool == Tool.DRAW || tool == Tool.TEXT
        binding.colorScroll.visibility = if (usesColor) View.VISIBLE else View.GONE
        binding.btnAddText.visibility = if (tool == Tool.TEXT) View.VISIBLE else View.GONE

        val progress = sizeByTool[tool] ?: 32
        binding.seekSize.progress = progress
        applySize(progress)
    }

    private fun styleTool(view: LinearLayout, selected: Boolean) {
        val density = resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(if (selected) palette.secondaryContainer else Color.TRANSPARENT)
        }
        val tint = if (selected) palette.onSecondaryContainer else palette.onSurfaceVariant
        (view.getChildAt(0) as ImageView).setColorFilter(tint)
        (view.getChildAt(1) as TextView).setTextColor(tint)
    }

    private fun buildSwatches() {
        val density = resources.displayMetrics.density
        val size = (34 * density).toInt()
        val margin = (4 * density).toInt()
        swatchColors.forEach { color ->
            val swatch = View(this)
            swatch.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            swatch.setOnClickListener {
                selectedColor = color
                binding.editorView.drawColor = color
                refreshSwatches()
            }
            swatchViews.add(swatch)
            binding.colorRow.addView(swatch)
        }
        refreshSwatches()
    }

    private fun refreshSwatches() {
        val density = resources.displayMetrics.density
        swatchColors.forEachIndexed { i, color ->
            swatchViews[i].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                val selected = color == selectedColor
                setStroke(
                    ((if (selected) 3 else 1) * density).toInt(),
                    if (selected) palette.primary else ColorUtils.setAlphaComponent(palette.outline, 160)
                )
            }
        }
    }

    private fun updateHistoryButtons() {
        val view = binding.editorView
        binding.btnUndo.isEnabled = view.canUndo()
        binding.btnUndo.alpha = if (view.canUndo()) 1f else 0.35f
        binding.btnRedo.isEnabled = view.canRedo()
        binding.btnRedo.alpha = if (view.canRedo()) 1f else 0.35f
        binding.btnSave.isEnabled = view.hasChanges()
    }

    private fun requestExit() {
        if (!binding.editorView.hasChanges()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(boldTitle(getString(R.string.editor_discard_title)))
            .setMessage(getString(R.string.editor_discard_message))
            .setPositiveButton(getString(R.string.editor_discard)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.editor_keep), null)
            .create()
            .showMonetDialog(this)
    }

    private fun save() {
        val result = binding.editorView.renderResult()
        binding.btnSave.isEnabled = false
        thread {
            val target = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
            val ok = try {
                FileOutputStream(target).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // The copy keeps working with the Restore brush.
                OriginalStore.copy(this, stickerName, target.name)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                target.delete()
                false
            }
            runOnUiThread {
                if (ok) {
                    setResult(RESULT_OK, Intent().putExtra(GifCropActivity.EXTRA_SAVED, true))
                    finish()
                } else {
                    binding.btnSave.isEnabled = true
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }

    private fun applyStyle() {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = !palette.isDark
        insets.isAppearanceLightNavigationBars = !palette.isDark

        binding.editorRoot.setBackgroundColor(palette.surface)
        binding.btnBack.setCardBackgroundColor(palette.secondaryContainer)
        binding.btnBack.strokeWidth = 0
        binding.imgBack.setColorFilter(palette.onSecondaryContainer)
        binding.txtHeaderTitle.setTextColor(palette.onSurface)
        binding.btnUndo.setColorFilter(palette.onSurface)
        binding.btnRedo.setColorFilter(palette.onSurface)

        binding.btnSave.backgroundTintList = ColorStateList.valueOf(palette.primary)
        binding.btnSave.setTextColor(palette.onPrimary)
        binding.btnAddText.backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
        binding.btnAddText.setTextColor(palette.onSecondaryContainer)
        binding.seekSize.progressTintList = ColorStateList.valueOf(palette.primary)
        binding.seekSize.thumbTintList = ColorStateList.valueOf(palette.primary)
        binding.txtSize.setTextColor(palette.onSurfaceVariant)
        binding.loading.setIndicatorColor(palette.primary)
    }

    companion object {
        const val EXTRA_STICKER_NAME = "sticker_name"
    }
}
