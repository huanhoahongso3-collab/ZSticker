package dhp.thl.tpl.ndv.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
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
import com.yalantis.ucrop.UCrop
import dhp.thl.tpl.ndv.BackupHelper
import dhp.thl.tpl.ndv.EditorActivity
import dhp.thl.tpl.ndv.GifCropActivity
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.ImageUtils
import dhp.thl.tpl.ndv.MediaPipeBackgroundRemover
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.ProgressDialog
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.data.OriginalStore
import dhp.thl.tpl.ndv.data.StickerMetaStore
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.ui.showMonetDialog
import dhp.thl.tpl.ndv.ui.showFolderPicker
import dhp.thl.tpl.ndv.ui.showPaneDialog
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Handles all per-sticker actions: Zalo sharing, long-press context menu, single-sticker gallery
 * export, offline MediaPipe & online BRIA RMBG 1.4 background removal, border creation, uCrop
 * launching, full-size sticker preview, and reactive [StickerRepository] updates.
 */
class StickerActionsController(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding,
    private val isVideoUri: (Uri) -> Boolean,
    private val cropLauncherProvider: () -> ActivityResultLauncher<Intent>,
    private val onStickerCreated: (() -> Unit)? = null,
    private val onSelectRequested: ((File) -> Unit)? = null
) {
    private val mediaPipeBackgroundRemover by lazy { MediaPipeBackgroundRemover(activity) }
    private var isOperationCancelled = false

    private fun isAutoCropEnabled(): Boolean =
        activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("auto_crop_removebg", true)

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
            StickerRepository.recordRecentUsage(activity, file.name)
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showToast(activity, activity.getString(R.string.zalo_share_failed))
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

        val isFavorite = fileName in StickerMetaStore.meta.value.favorites
        if (!isRecent) {
            options.add(
                if (isFavorite) OptionItem(R.drawable.ic_star_border, activity.getString(R.string.favorite_remove))
                else OptionItem(R.drawable.ic_star, activity.getString(R.string.favorite_add))
            )
            options.add(OptionItem(R.drawable.ic_folder, activity.getString(R.string.move_to_folder)))
            options.add(OptionItem(R.drawable.ic_check, activity.getString(R.string.select_action)))
        }

        if (!isRecent && !isAnimated) {
            options.add(OptionItem(R.drawable.ic_remove_bg, activity.getString(R.string.remove_bg)))
            options.add(OptionItem(R.drawable.ic_border_sticker, activity.getString(R.string.border_sticker)))
        }
        // The editor (eraser, restore, draw, text) works on still stickers only.
        if (!isRecent && !isAnimated) {
            options.add(OptionItem(R.drawable.ic_brush, activity.getString(R.string.edit_sticker)))
        }
        // Photos and GIFs can be cropped; videos cannot.
        if (!isRecent && (!isAnimated || fileName.endsWith(".gif", ignoreCase = true))) {
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
                activity.getString(R.string.favorite_add) -> StickerMetaStore.setFavorite(activity, listOf(fileName), true)
                activity.getString(R.string.favorite_remove) -> StickerMetaStore.setFavorite(activity, listOf(fileName), false)
                activity.getString(R.string.move_to_folder) -> {
                    val current = StickerMetaStore.meta.value.assignments[fileName]
                    activity.showFolderPicker(current) { folderId ->
                        StickerMetaStore.assign(activity, listOf(fileName), folderId)
                    }
                }
                activity.getString(R.string.select_action) -> onSelectRequested?.invoke(File(activity.filesDir, fileName))
                activity.getString(R.string.remove_bg) -> {
                    val onlineModeEnabled = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("removebg_online_mode", false)
                    if (onlineModeEnabled) checkAndShowBackgroundRemovalWarning(uri) else removeBackground(uri)
                }
                activity.getString(R.string.border_sticker) -> stickify(uri)
                activity.getString(R.string.edit_sticker) -> {
                    val intent = Intent(activity, EditorActivity::class.java)
                        .putExtra(EditorActivity.EXTRA_STICKER_NAME, fileName)
                    cropLauncherProvider().launch(intent)
                }
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
        progressDialog.showSafe(activity.supportFragmentManager, "export_single")

        thread {
            val success = BackupHelper.exportSingleStickerToGallery(activity, file)
            activity.runOnUiThread {
                progressDialog.dismissSafe()
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
        val palette = ExpressiveThemeManager.resolvePalette(activity)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(palette.surface, 178))
        binding.loadingIndicator.setIndicatorColor(palette.primary)
        binding.progressBar.visibility = View.VISIBLE
    }

    /**
     * Stores the photo behind a cut-out, scaled to the segmentation size and cropped with the same
     * rectangle as the sticker, so the two stay pixel-aligned for the Restore brush.
     */
    private fun saveOriginal(stickerName: String, source: Bitmap, rect: android.graphics.Rect?, targetW: Int, targetH: Int) {
        try {
            val aligned = if (source.width != targetW || source.height != targetH) {
                Bitmap.createScaledBitmap(source, targetW, targetH, true)
            } else {
                source
            }
            val cropped = if (rect != null) {
                Bitmap.createBitmap(aligned, rect.left, rect.top, rect.width(), rect.height())
            } else {
                aligned
            }
            OriginalStore.save(activity, stickerName, cropped)
            if (cropped !== aligned) cropped.recycle()
            if (aligned !== source) aligned.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

                if (segmentedBitmap != null) {
                    val cropRect = if (isAutoCropEnabled()) ImageUtils.cropTransparentRect(segmentedBitmap) else null
                    val croppedBitmap = cropRect?.let {
                        Bitmap.createBitmap(segmentedBitmap, it.left, it.top, it.width(), it.height())
                    } ?: segmentedBitmap
                    val file = File(activity.filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    // Keep the photo behind the cut-out so the editor's Restore brush can bring pixels back.
                    saveOriginal(file.name, resizedBitmap, cropRect, segmentedBitmap.width, segmentedBitmap.height)
                    if (croppedBitmap != segmentedBitmap) segmentedBitmap.recycle()
                    croppedBitmap.recycle()
                    resultUri = Uri.fromFile(file)
                    isSuccess = true
                }
                resizedBitmap.recycle()
            } catch (_: Exception) {
                isSuccess = false
            }

            activity.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    StickerRepository.refreshHome(activity) {
                        onStickerCreated?.invoke()
                    }
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
        val palette = ExpressiveThemeManager.resolvePalette(activity)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_warning, null)
        val dialog = MaterialAlertDialogBuilder(activity).setView(dialogView).create()
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon_warning)
        val checkBox = dialogView.findViewById<MaterialCheckBox>(R.id.cb_dont_show_again)

        titleView.text = activity.boldTitle(activity.getString(R.string.rb_warning_title))
        titleView.setTextColor(palette.onSurface)
        messageView.text = activity.getString(R.string.rb_warning_message)
        messageView.setTextColor(palette.onSurfaceVariant)
        messageView.setLinkTextColor(palette.primary)

        iconView.setImageResource(R.drawable.ic_remove_bg)
        iconView.setColorFilter(palette.primary)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnContinue = dialogView.findViewById<MaterialButton>(R.id.btn_continue)
        btnCancel.setTextColor(palette.primary)
        checkBox.buttonTintList = ColorStateList.valueOf(palette.primary)
        checkBox.setTextColor(palette.onSurface)

        btnContinue.setTextColor(palette.onPrimary)
        btnContinue.backgroundTintList = ColorStateList.valueOf(palette.primary)

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
            var originalBitmap: Bitmap? = null
            val tempResponseFile = File(activity.cacheDir, "rb_net_${System.currentTimeMillis()}.png")
            try {
                val resizedBitmap = ImageUtils.loadSampledBitmapWithGlide(activity, uri, 512, 512)
                    ?: throw Exception("Glide failed to decode source URI")
                originalBitmap = resizedBitmap

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
                            val cropRect = if (isAutoCropEnabled()) ImageUtils.cropTransparentRect(downloadedBitmap) else null
                            val croppedBitmap = cropRect?.let {
                                Bitmap.createBitmap(downloadedBitmap, it.left, it.top, it.width(), it.height())
                            } ?: downloadedBitmap
                            FileOutputStream(file).use { out ->
                                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            originalBitmap?.let {
                                saveOriginal(file.name, it, cropRect, downloadedBitmap.width, downloadedBitmap.height)
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
                originalBitmap?.recycle()
            }

            activity.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    StickerRepository.refreshHome(activity) {
                        onStickerCreated?.invoke()
                    }
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
                    StickerRepository.refreshHome(activity) {
                        onStickerCreated?.invoke()
                    }
                    ToastUtils.showToast(activity, activity.getString(R.string.success))
                } else {
                    ToastUtils.showToast(activity, activity.getString(R.string.failed))
                }
            }
        }
    }

    private fun deleteSticker(uri: Uri) {
        val file = File(activity.filesDir, uri.lastPathSegment ?: "")
        StickerRepository.deleteSticker(activity, file) { deleted ->
            if (deleted) {
                ToastUtils.showToast(activity, activity.getString(R.string.success))
            }
        }
    }

    private fun removeFromRecents(entry: String) {
        StickerRepository.removeRecentEntry(activity, entry) { removed ->
            if (removed) {
                ToastUtils.showToast(activity, activity.getString(R.string.success))
            }
        }
    }

    private fun startCrop(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (isVideoUri(uri)) {
            ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
            return
        }

        if (fileName.endsWith(".gif")) {
            // GIFs get their own crop screen: live animated preview, play / pause and a timeline.
            val intent = Intent(activity, GifCropActivity::class.java)
                .putExtra(GifCropActivity.EXTRA_GIF_NAME, uri.lastPathSegment)
            cropLauncherProvider().launch(intent)
            return
        }

        val palette = ExpressiveThemeManager.resolvePalette(activity)
        val destinationUri = Uri.fromFile(File(activity.cacheDir, "crop_temp_${System.currentTimeMillis()}.png"))

        val options = UCrop.Options().apply {
            setToolbarColor(palette.surface)
            setToolbarWidgetColor(palette.onSurface)
            setActiveControlsWidgetColor(palette.primary)
            setRootViewBackgroundColor(palette.surface)
            setLogoColor(palette.primary)
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
        if (result.resultCode != Activity.RESULT_OK) return
        val data = result.data ?: return

        // The GIF crop screen saves the new sticker itself.
        if (data.getBooleanExtra(GifCropActivity.EXTRA_SAVED, false)) {
            StickerRepository.refreshHome(activity) {
                onStickerCreated?.invoke()
            }
            ToastUtils.showToast(activity, activity.getString(R.string.success))
            return
        }

        val resultUri = UCrop.getOutput(data)
        if (resultUri != null) {
            try {
                val file = File(activity.filesDir, "zsticker_${System.currentTimeMillis()}.png")
                activity.contentResolver.openInputStream(resultUri)?.use { input ->
                    FileOutputStream(file).use { out -> input.copyTo(out) }
                }
                StickerRepository.refreshHome(activity) {
                    onStickerCreated?.invoke()
                }
                ToastUtils.showToast(activity, activity.getString(R.string.success))
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showToast(activity, activity.getString(R.string.failed))
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
