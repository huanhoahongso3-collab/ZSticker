package dhp.thl.tpl.ndv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import dhp.thl.tpl.ndv.databinding.ActivityFileBinding
import java.io.OutputStream
import java.io.File
import androidx.core.graphics.ColorUtils

class FileActivity : BaseActivity() {

    private lateinit var binding: ActivityFileBinding
    private var currentExportType: String = "all"
    private var isExportExpanded = false

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                exportFile(uri, currentExportType)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                if (BackupHelper.importBackup(this, uri)) {
                    ToastUtils.showToast(this, getString(R.string.success))
                    setResult(RESULT_OK, Intent().putExtra("did_import", true))
                    recreate() // Apply settings instantly
                } else {
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.itemImport.setOnClickListener {
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

        binding.itemExportGallery.setOnClickListener {
            val count = BackupHelper.exportAllStickersToGallery(this)
            if (count > 0) {
                ToastUtils.showToast(this, "Exported $count stickers to Gallery")
            } else {
                ToastUtils.showToast(this, getString(R.string.no_stickers_found))
            }
        }
        
        applyMonetIfEnabled()
    }

    private fun toggleExportExpand() {
        isExportExpanded = !isExportExpanded
        binding.expandableExport.visibility = if (isExportExpanded) View.VISIBLE else View.GONE
        binding.imgExpand.rotation = if (isExportExpanded) -90f else 0f
    }

    private fun startExport(type: String) {
        // Validation
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

    private fun applyMonetIfEnabled() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("material_color_enabled", false)) {
            val primary = monet.getAccentColor(this)
            val alphaPrimary = ColorUtils.setAlphaComponent(primary, 40)
            
            val iconItems = listOf(
                binding.imgImport, binding.imgExportHeader, binding.imgExportGallery,
                binding.imgExportImage, binding.imgExportHistory, binding.imgExportSettings, binding.imgExportAll
            )
            
            iconItems.forEach { icon ->
                icon.setColorFilter(primary)
                icon.backgroundTintList = android.content.res.ColorStateList.valueOf(alphaPrimary)
            }
            
            binding.btnBack.setCardBackgroundColor(alphaPrimary)
            binding.imgExpand.setColorFilter(primary)
        }
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
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (BackupHelper.exportBackup(this, outputStream, type)) {
                    ToastUtils.showToast(this, getString(R.string.success))
                } else {
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            } ?: ToastUtils.showToast(this, getString(R.string.failed))
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showToast(this, getString(R.string.failed))
        }
    }
}
