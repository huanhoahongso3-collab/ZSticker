package dhp.thl.tpl.ndv.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.monetcompat.core.MonetCompat
import com.yalantis.ucrop.UCrop
import dhp.thl.tpl.ndv.BackupHelper
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.ImageUtils
import dhp.thl.tpl.ndv.MediaPipeBackgroundRemover
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.ProgressDialog
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.ui.showMonetDialog
import dhp.thl.tpl.ndv.ui.showPaneDialog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Handles all per-sticker actions: Zalo sharing, long-press context menu, single-sticker gallery
 * export, offline MediaPipe & online BRIA RMBG 1.4 background removal, border creation, uCrop
 * launching, full-size sticker preview, and recents/sticker deletion sync.
 */
class StickerActionsController(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding,
    private val adapterProvider: () -> StickerAdapter,
    private val recentsAdapterProvider: () -> StickerAdapter,
    private val currentTabIdProvider: () -> Int,
    private val getThemeColor: (Int) -> Int,
    private val isVideoUri: (Uri) -> Boolean,
    private val onUpdateEmptyState: (isEmpty: Boolean, message: String) -> Unit,
    private val cropLauncherProvider: () -> ActivityResultLauncher<Intent>
) {
    private val mediaPipeBackgroundRemover by lazy { MediaPipeBackgroundRemover(activity) }
    private var isOperationCancelled = false

    fun close() {
        mediaPipeBackgroundRemover.close()
    }

    fun onStickerClick(uri: Uri) {
        try {
            val file = File(activity.filesDir, uri.lastPathSegment ?: "")
            if (!file.exists()) return

            val isAnimated = file.name.endsWith(".gif", ignoreCase = true)
            val shareFile = if (isAnimated) {
                ImageUtils.getZaloAnimatedSticker(activity, file)
            } else {
                ImageUtils.getZaloSticker(activity, file)
            }

            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                shareFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isAnimated) "image/gif" else "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("is_sticker", true)
                putExtra("type", 3)
                setClassName("com.zing.zalo", "com.zing.zalo.ui.TempShareViaActivity")
            }
            activity.startActivity(intent)
            addToRecents(file.name)
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showToast(activity, activity.getString(R.string.zalo_share_failed))
        }
    }

    private fun addToRecents(fileName: String) {
        val prefs = activity.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val list = prefs.getString("list", "")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toMutableList() ?: mutableListOf()
        list.add(0, "$fileName:${System.currentTimeMillis()}")
        if (list.size > 100) list.removeAt(100)
        prefs.edit().putString("list", list.joinToString(",")).apply()
        if (currentTabIdProvider() == R.id.nav_recents) {
            recentsAdapterProvider().refreshData(activity)
        }
    }

    fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String?) {
        val fileName = uri.lastPathSegment ?: ""
        val isAnimated = fileName.endsWith(".gif", ignoreCase = true) || isVideoUri(uri)
        val title = activity.getString(
            if (isRecent) R.string.recent_options_title else R.string.sticker_options_title
        )
        val options = mutableListOf<OptionItem>()
        options.add(OptionItem(R.drawable.ic_export_gallery, activity.getString(R.string.export)))

        if (!isRecent && !isAnimated) {
            options.add(OptionItem(R.drawable.ic_remove_bg, activity.getString(R.string.remove_bg)))
            options.add(OptionItem(R.drawable.ic_border_sticker, activity.getString(R.string.border_sticker)))
            options.add(OptionItem(R.drawable.ic_crop, activity.getString(R.string.crop)))
        }

        options.add(OptionItem(R.drawable.ic_view_full, activity.getString(R.string.view_full_sticker)))

        if (!isRecent) {
            options.add(OptionItem(R.drawable.ic_delete, activity.getString(R.string.delete)))
        } else {
            options.add(OptionItem(R.drawable.ic_delete, activity.getString(R.string.delete_history)))
        }

        activity.showPaneDialog(title, options) { which ->
            when (options[which].text) {
                activity.getString(R.string.export) -> exportSingleSticker(uri)
                activity.getString(R.string.remove_bg) -> {
                    val onlineModeEnabled = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("removebg_online_mode", false)
                    if (onlineModeEnabled) checkAndShowBackgroundRemovalWarning(uri) else removeBackground(uri)
                }
                activity.getString(R.string.border_sticker) -> stickify(uri)
                activity.getString(R.string.crop) -> startCrop(uri)
                activity.getString(R.string.view_full_sticker) -> viewFullSticker(uri)
                activity.getString(R.string.delete) -> deleteSticker(uri)
                activity.getString(R.string.delete_history) -> entry?.let { removeFromRecents(it) }
            }
        }
    }

    private fun exportSingleSticker(uri: Uri) {
        val file = File(activity.filesDir, uri.lastPathSegment ?: "")
        if (!file.exists()) return

        val progressDialog = ProgressDialog.newInstance(activity.getString(R.string.exporting_to_gallery))
        isOperationCancelled = false
        progressDialog.onCancel = { isOperationCancelled = true }
        progressDialog.show(activity.supportFragmentManager, "export_single")

        thread {
            val success = BackupHelper.exportSingleStickerToGallery(activity, file)
            activity.runOnUiThread {
                progressDialog.dismiss()
                if (isOperationCancelled) {
                    ToastUtils.showToast(activity, activity.getString(R.string.failed))
                } else {
                    ToastUtils.showToast(
                        activity,
                        if (success) activity.getString(R.string.success) else activity.getString(R.string.failed)
                    )
                }
            }
        }
    }

    private fun showLoadingOverlay() {
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, 153))

        val materialColorEnabled = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) {
            MonetCompat.getInstance().getAccentColor(activity)
        } else {
            activity.getColor(R.color.orange_primary)
        }
        binding.loadingIndicator.setIndicatorColor(primary)
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun removeBackground(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
            return
        }

        showLoadingOverlay()

        thread {
            var isSuccess = false
            var resultUri: Uri? = null
            try {
                val resizedBitmap = ImageUtils.loadSampledBitmapWithGlide(activity, uri, 512, 512)
                    ?: throw Exception("Glide failed to decode source URI")
                val segmentedBitmap = mediaPipeBackgroundRemover.removeBackground(resizedBitmap)
                resizedBitmap.recycle()

                if (segmentedBitmap != null) {
                    val croppedBitmap = ImageUtils.cropTransparent(segmentedBitmap)
                    val file = File(activity.filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (croppedBitmap != segmentedBitmap) segmentedBitmap.recycle()
                    croppedBitmap.recycle()
                    resultUri = Uri.fromFile(file)
                    isSuccess = true
                }
            } catch (_: Exception) {
                isSuccess = false
            }

            activity.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(activity, activity.getString(R.string.rb_completed))
                } else {
                    ToastUtils.showToast(activity, activity.getString(R.string.rb_failed))
                }
            }
        }
    }

    private fun checkAndShowBackgroundRemovalWarning(uri: Uri) {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("dont_show_rb_warning", false)) {
            removeBackgroundOnline(uri)
            return
        }
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_warning, null)
        val dialog = MaterialAlertDialogBuilder(activity).setView(dialogView).create()
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon_warning)
        val checkBox = dialogView.findViewById<MaterialCheckBox>(R.id.cb_dont_show_again)

        titleView.text = activity.boldTitle(activity.getString(R.string.rb_warning_title))
        messageView.text = activity.getString(R.string.rb_warning_message)
        iconView.setImageResource(R.drawable.ic_remove_bg)

        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) {
            MonetCompat.getInstance().getAccentColor(activity)
        } else {
            activity.getColor(R.color.orange_primary)
        }
        iconView.setColorFilter(primary)
        messageView.setLinkTextColor(primary)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnContinue = dialogView.findViewById<MaterialButton>(R.id.btn_continue)
        btnCancel.setTextColor(primary)
        checkBox.buttonTintList = ColorStateList.valueOf(primary)

        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        btnContinue.setTextColor(if (isDark) Color.BLACK else Color.WHITE)
        btnContinue.backgroundTintList = ColorStateList.valueOf(primary)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnContinue.setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("dont_show_rb_warning", true).apply()
            }
            dialog.dismiss()
            removeBackgroundOnline(uri)
        }
        dialog.showMonetDialog(activity)
    }

    private fun removeBackgroundOnline(uri: Uri) {
        showLoadingOverlay()

        thread {
            var isSuccess = false
            var resultUri: Uri? = null
            val tempResponseFile = File(activity.cacheDir, "rb_net_${System.currentTimeMillis()}.png")
            try {
                val resizedBitmap = ImageUtils.loadSampledBitmapWithGlide(activity, uri, 512, 512)
                    ?: throw Exception("Glide failed to decode source URI")

                val url = URL("https://bria14proxy.vercel.app/api/nobg")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                connection.outputStream.use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                resizedBitmap.recycle()

                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempResponseFile).use { tempOut ->
                            input.copyTo(tempOut)
                        }
                    }
                    if (tempResponseFile.exists() && tempResponseFile.length() > 0) {
                        val downloadedBitmap = ImageUtils.loadSampledBitmapWithGlide(activity, tempResponseFile, 512, 512)
                        if (downloadedBitmap != null) {
                            val file = File(activity.filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                            val croppedBitmap = ImageUtils.cropTransparent(downloadedBitmap)
                            FileOutputStream(file).use { out ->
                                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            if (croppedBitmap != downloadedBitmap) downloadedBitmap.recycle()
                            croppedBitmap.recycle()
                            resultUri = Uri.fromFile(file)
                            isSuccess = true
                        }
                    }
                }
            } catch (_: Exception) {
                isSuccess = false
            } finally {
                try {
                    if (tempResponseFile.exists()) tempResponseFile.delete()
                } catch (_: Exception) {}
            }

            activity.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(activity, activity.getString(R.string.rb_completed))
                } else {
                    ToastUtils.showToast(activity, activity.getString(R.string.rb_failed))
                }
            }
        }
    }

    private fun stickify(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
            return
        }

        val file = File(activity.filesDir, uri.lastPathSegment ?: "")
        if (!file.exists()) return

        showLoadingOverlay()

        thread {
            val resultFile = ImageUtils.addStickerBorder(activity, file)
            activity.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (resultFile != null) {
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(activity, activity.getString(R.string.success))
                } else {
                    ToastUtils.showToast(activity, activity.getString(R.string.failed))
                }
            }
        }
    }

    private fun deleteSticker(uri: Uri) {
        val file = File(activity.filesDir, uri.lastPathSegment ?: "")
        if (file.exists() && file.delete()) {
            val adapter = adapterProvider()
            adapter.refreshData(activity)
            syncRecentsAfterDeletion()
            onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
            ToastUtils.showToast(activity, activity.getString(R.string.success))
        }
    }

    fun syncRecentsAfterDeletion() {
        val prefs = activity.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val list = prefs.getString("list", "")?.split(",")?.toMutableList() ?: mutableListOf()
        val filtered = list.filter { item ->
            val fileName = item.substringBefore(":")
            File(activity.filesDir, fileName).exists()
        }.joinToString(",")
        prefs.edit().putString("list", filtered).apply()
        recentsAdapterProvider().refreshData(activity)
    }

    private fun removeFromRecents(entry: String) {
        val prefs = activity.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val list = prefs.getString("list", "")?.split(",")?.toMutableList() ?: mutableListOf()
        if (list.remove(entry)) {
            prefs.edit().putString("list", list.joinToString(",")).apply()
            recentsAdapterProvider().refreshData(activity)
            ToastUtils.showToast(activity, activity.getString(R.string.success))
        }
    }

    private fun startCrop(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
            return
        }

        val materialColorEnabled = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) {
            MonetCompat.getInstance().getAccentColor(activity)
        } else {
            activity.getColor(R.color.orange_primary)
        }

        val destinationUri = Uri.fromFile(File(activity.cacheDir, "crop_temp_${System.currentTimeMillis()}.png"))

        val options = UCrop.Options().apply {
            val surface = getThemeColor(com.google.android.material.R.attr.colorSurface)
            setToolbarColor(surface)
            setToolbarWidgetColor(primary)
            setActiveControlsWidgetColor(primary)
            setRootViewBackgroundColor(surface)
            setLogoColor(primary)
            setToolbarTitle(activity.getString(R.string.crop))
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setFreeStyleCropEnabled(true)
        }

        val intent = UCrop.of(uri, destinationUri)
            .withOptions(options)
            .getIntent(activity)

        cropLauncherProvider().launch(intent)
    }

    fun handleCropResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                try {
                    val file = File(activity.filesDir, "zsticker_${System.currentTimeMillis()}.png")
                    activity.contentResolver.openInputStream(resultUri)?.use { input ->
                        FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    ToastUtils.showToast(activity, activity.getString(R.string.success))
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showToast(activity, activity.getString(R.string.failed))
                }
            }
        }
    }

    private fun viewFullSticker(uri: Uri) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_full, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.img_full_sticker)
        Glide.with(activity).load(uri).into(imageView)

        MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setPositiveButton(activity.getString(R.string.ok), null)
            .create()
            .showMonetDialog(activity)
    }
}
