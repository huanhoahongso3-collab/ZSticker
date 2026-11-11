package dhp.thl.tpl.ndv

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.databinding.ActivityFileBinding
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager.GroupPosition
import kotlin.concurrent.thread

class FileActivity : BaseActivity() {

    private lateinit var binding: ActivityFileBinding
    private var currentExportType: String = "all"
    private var isExportExpanded = false
    private var isOperationCancelled = false

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                exportFile(uri, currentExportType)
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val progressDialog = ProgressDialog.newInstance(getString(R.string.importing_backup))
                isOperationCancelled = false
                progressDialog.onCancel = { isOperationCancelled = true }
                progressDialog.showSafe(supportFragmentManager, "import")

                thread {
                    val success = BackupHelper.importBackup(
                        this,
                        uri,
                        { progress -> runOnUiThread { progressDialog.updateProgress(progress) } },
                        { isOperationCancelled }
                    )
                    runOnUiThread {
                        progressDialog.dismissSafe()
                        if (isOperationCancelled) {
                            ToastUtils.showToast(this, getString(R.string.failed))
                        } else if (success) {
                            ToastUtils.showToast(this, getString(R.string.success))
                            setResult(RESULT_OK, Intent().putExtra("did_import", true))
                            restartApp()
                        } else {
                            ToastUtils.showToast(this, getString(R.string.not_a_backup_file))
                        }
                    }
                }
            }
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
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = topInsets.left, top = topInsets.top, right = topInsets.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fileScroll) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = navInsets.left, right = navInsets.right, bottom = navInsets.bottom)
            insets
        }

        binding.fileScroll.enableStretchOverscroll()

        isExportExpanded = savedInstanceState?.getBoolean("isExportExpanded", false) ?: false
        binding.expandableExport.visibility = if (isExportExpanded) View.VISIBLE else View.GONE
        binding.imgExpand.rotation = if (isExportExpanded) 90f else 0f

        ExpressiveMotion.attachPressSpring(binding.btnBack, 0.90f)
        ExpressiveMotion.attachPressSpring(binding.cardImport, 0.97f)
        ExpressiveMotion.attachPressSpring(binding.cardExportGallery, 0.97f)

        binding.btnBack.setOnClickListener { finish() }

        binding.cardImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            importLauncher.launch(intent)
        }

        binding.itemExportHeader.setOnClickListener {
            toggleExportExpand()
        }

        binding.exportImage.setOnClickListener { startExport("image") }
        binding.exportHistory.setOnClickListener { startExport("history") }
        binding.exportSettings.setOnClickListener { startExport("settings") }
        binding.exportAll.setOnClickListener { startExport("all") }

        binding.cardExportGallery.setOnClickListener {
            val progressDialog = ProgressDialog.newInstance(getString(R.string.exporting_to_gallery))
            isOperationCancelled = false
            progressDialog.onCancel = { isOperationCancelled = true }
            progressDialog.showSafe(supportFragmentManager, "export_gallery")

            thread {
                val count = BackupHelper.exportAllStickersToGallery(
                    this,
                    { progress -> runOnUiThread { progressDialog.updateProgress(progress) } },
                    { isOperationCancelled }
                )
                runOnUiThread {
                    progressDialog.dismissSafe()
                    if (isOperationCancelled) {
                        ToastUtils.showToast(this, getString(R.string.failed))
                    } else if (count > 0) {
                        ToastUtils.showToast(this, getString(R.string.export_gallery_success, count))
                    } else {
                        ToastUtils.showToast(this, getString(R.string.no_stickers_found))
                    }
                }
            }
        }

        applyExpressiveStyle()
    }

    private fun toggleExportExpand() {
        isExportExpanded = !isExportExpanded
        TransitionManager.beginDelayedTransition(
            binding.fileScroll,
            TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds().apply { duration = 260L; interpolator = ExpressiveMotion.emphasizedDecelerate })
                addTransition(Fade().apply { duration = 200L })
            }
        )
        binding.expandableExport.visibility = if (isExportExpanded) View.VISIBLE else View.GONE
        binding.imgExpand.animate()
            .rotation(if (isExportExpanded) 90f else 0f)
            .setDuration(240L)
            .setInterpolator(ExpressiveMotion.emphasizedDecelerate)
            .start()
    }

    private fun startExport(type: String) {
        if (type == "image" || type == "all") {
            val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
            if (stickerFiles.isEmpty() && type == "image") {
                ToastUtils.showToast(this, getString(R.string.no_stickers_found))
                return
            }
        }
        if (type == "history" || type == "all") {
            val history = getSharedPreferences("recents", MODE_PRIVATE).getString("list", "") ?: ""
            if (history.isEmpty() && type == "history") {
                ToastUtils.showToast(this, getString(R.string.no_history_found))
                return
            }
        }

        currentExportType = type
        startFilePicker(type)
    }

    private fun applyExpressiveStyle() {
        val palette = ExpressiveThemeManager.resolvePalette(this)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !palette.isDark
        insetsController.isAppearanceLightNavigationBars = !palette.isDark

        binding.root.setBackgroundColor(palette.surface)
        binding.appBarLayout.setBackgroundColor(palette.surface)
        binding.headerRow.setBackgroundColor(palette.surface)
        binding.txtHeaderTitle.setTextColor(palette.onSurface)

        binding.btnBack.setCardBackgroundColor(palette.secondaryContainer)
        binding.btnBack.strokeWidth = 0
        binding.imgBack.setColorFilter(palette.onSecondaryContainer)

        ExpressiveThemeManager.styleSegmentedCard(binding.cardImport, GroupPosition.TOP, palette)
        ExpressiveThemeManager.styleSegmentedCard(binding.cardExport, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(binding.cardExportGallery, GroupPosition.BOTTOM, palette)

        listOf(
            binding.imgImport,
            binding.imgExportHeader,
            binding.imgExportGallery
        ).forEach { icon ->
            ExpressiveThemeManager.styleSquircleIcon(
                icon,
                badgeBgColor = palette.secondaryContainer,
                iconTint = palette.onSecondaryContainer
            )
        }

        listOf(
            binding.imgExportImage,
            binding.imgExportHistory,
            binding.imgExportSettings,
            binding.imgExportAll
        ).forEach { icon ->
            ExpressiveThemeManager.styleSquircleIcon(
                icon,
                badgeBgColor = palette.surfaceContainerHigh,
                iconTint = palette.primary
            )
        }

        binding.imgExpand.backgroundTintList = ColorStateList.valueOf(palette.surfaceContainerHigh)
        binding.imgExpand.setColorFilter(palette.onSurfaceVariant)

        listOf(
            binding.titleImport,
            binding.titleExport,
            binding.titleExportGallery,
            binding.txtExportImage,
            binding.txtExportHistory,
            binding.txtExportSettings,
            binding.txtExportAll
        ).forEach { it.setTextColor(palette.onSurface) }

        listOf(
            binding.noteImport,
            binding.noteExport,
            binding.noteExportGallery
        ).forEach { it.setTextColor(palette.onSurfaceVariant) }
    }

    private fun startFilePicker(type: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "${type}_backup_${System.currentTimeMillis()}.zip")
        }
        createDocumentLauncher.launch(intent)
    }

    private fun exportFile(uri: Uri, type: String) {
        val progressDialog = ProgressDialog.newInstance(getString(R.string.exporting_backup))
        isOperationCancelled = false
        progressDialog.onCancel = { isOperationCancelled = true }
        progressDialog.showSafe(supportFragmentManager, "export")

        thread {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val success = BackupHelper.exportBackup(
                        this,
                        outputStream,
                        type,
                        { progress -> runOnUiThread { progressDialog.updateProgress(progress) } },
                        { isOperationCancelled }
                    )
                    runOnUiThread {
                        progressDialog.dismissSafe()
                        if (isOperationCancelled) {
                            try {
                                contentResolver.delete(uri, null, null)
                            } catch (_: Exception) {}
                            ToastUtils.showToast(this, getString(R.string.failed))
                        } else if (success) {
                            ToastUtils.showToast(this, getString(R.string.success))
                        } else {
                            ToastUtils.showToast(this, getString(R.string.failed))
                        }
                    }
                } ?: runOnUiThread {
                    progressDialog.dismissSafe()
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismissSafe()
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isExportExpanded", isExportExpanded)
    }
}
