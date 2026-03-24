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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.TextView
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                showProgressDialog(getString(R.string.info_import_title)) { onProgress ->
                    val success = BackupHelper.importBackup(this, uri, onProgress)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            ToastUtils.showToast(this@FileActivity, getString(R.string.success))
                            setResult(RESULT_OK, Intent().putExtra("did_import", true))
                            restartApp()
                        } else {
                            ToastUtils.showToast(this@FileActivity, getString(R.string.not_a_backup_file))
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isExportExpanded = savedInstanceState?.getBoolean("isExportExpanded", false) ?: false
        binding.expandableExport.visibility = if (isExportExpanded) View.VISIBLE else View.GONE
        binding.imgExpand.rotation = if (isExportExpanded) -90f else 0f

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
                ToastUtils.showToast(this, getString(R.string.export_gallery_success, count))
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
        val isMaterialColor = prefs.getBoolean("material_color_enabled", false)
        
        val primary = if (isMaterialColor) monet.getAccentColor(this) else getColor(R.color.orange_primary)
        val alphaPrimary = ColorUtils.setAlphaComponent(primary, 30) // Subtle M3 alpha
        
        val iconItems = listOf(
            binding.imgImport, binding.imgExportHeader, binding.imgExportGallery,
            binding.imgExportImage, binding.imgExportHistory, binding.imgExportSettings, binding.imgExportAll
        )
        
        iconItems.forEach { icon ->
            icon.setColorFilter(primary)
            icon.backgroundTintList = android.content.res.ColorStateList.valueOf(alphaPrimary)
        }
        
        // Match LicenseActivity's back button style (circular tinted background, no card outline)
        binding.btnBack.setCardBackgroundColor(alphaPrimary)
        binding.btnBack.strokeWidth = 0
        binding.btnBack.cardElevation = 0f
        
        val backIcon = binding.btnBack.getChildAt(0) as? android.widget.ImageView
        backIcon?.setColorFilter(primary)
        
        // Expand icon circle background and tint
        binding.imgExpand.setColorFilter(primary)
        binding.imgExpand.backgroundTintList = android.content.res.ColorStateList.valueOf(alphaPrimary)
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
        showProgressDialog(getString(R.string.info_export_title)) { onProgress ->
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val success = BackupHelper.exportBackup(this, outputStream, type, onProgress)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            ToastUtils.showToast(this@FileActivity, getString(R.string.success))
                        } else {
                            ToastUtils.showToast(this@FileActivity, getString(R.string.failed))
                        }
                    }
                } ?: withContext(Dispatchers.Main) { ToastUtils.showToast(this@FileActivity, getString(R.string.failed)) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { ToastUtils.showToast(this@FileActivity, getString(R.string.failed)) }
            }
        }
    }

    private fun showProgressDialog(title: String, action: suspend (onProgress: (Int) -> Unit) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progress_indicator)
        val txtPercentage = dialogView.findViewById<TextView>(R.id.txt_percentage)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(boldTitle(title))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            action { progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    progressBar.progress = progress
                    txtPercentage.text = "$progress%"
                }
            }
            lifecycleScope.launch(Dispatchers.Main) {
                dialog.dismiss()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isExportExpanded", isExportExpanded)
    }
}
