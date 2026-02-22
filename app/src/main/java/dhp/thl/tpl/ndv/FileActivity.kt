package dhp.thl.tpl.ndv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.databinding.ActivityFileBinding
import java.io.OutputStream

class FileActivity : BaseActivity() {

    private lateinit var binding: ActivityFileBinding
    private var currentExportType: String = "all"

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

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.itemImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            importLauncher.launch(intent)
        }

        binding.itemExport.setOnClickListener {
            showExportSelectionDialog()
        }

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

    private fun applyMonetIfEnabled() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("material_color_enabled", false)) {
            val primary = monet.getAccentColor(this)
            binding.imgImport.setColorFilter(primary)
            binding.imgExport.setColorFilter(primary)
            binding.imgExportGallery.setColorFilter(primary)
            
            binding.imgImport.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 40))
            binding.imgExport.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 40))
            binding.imgExportGallery.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 40))
        }
    }

    private fun showExportSelectionDialog() {
        val options = listOf(
            OptionItem(R.drawable.ic_export_image, getString(R.string.export_image)),
            OptionItem(R.drawable.ic_export_history, getString(R.string.export_history)),
            OptionItem(R.drawable.ic_export_settings, getString(R.string.export_settings)),
            OptionItem(R.drawable.ic_export_all, getString(R.string.export_all))
        )

        // Reuse the pane dialog logic from MainActivity if possible, but here I'll just use a simple Material Dialog
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.info_export_title))
            .setItems(options.map { it.text }.toTypedArray()) { _, which ->
                val type = when (which) {
                    0 -> "image"
                    1 -> "history"
                    2 -> "settings"
                    else -> "all"
                }
                
                // Validation
                if (type == "image" || type == "all") {
                    val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
                    if (stickerFiles.isEmpty() && type == "image") {
                        ToastUtils.showToast(this, getString(R.string.no_stickers_found))
                        return@setItems
                    }
                }
                if (type == "history" || type == "all") {
                    val history = getSharedPreferences("recents", MODE_PRIVATE).getString("list", "") ?: ""
                    if (history.isEmpty() && type == "history") {
                        ToastUtils.showToast(this, getString(R.string.no_history_found))
                        return@setItems
                    }
                }

                currentExportType = type
                startFilePicker(type)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
