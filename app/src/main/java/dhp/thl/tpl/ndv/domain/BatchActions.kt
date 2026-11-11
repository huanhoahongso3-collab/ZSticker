package dhp.thl.tpl.ndv.domain

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.BackupHelper
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.ImageUtils
import dhp.thl.tpl.ndv.ProgressDialog
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.data.StickerMetaStore
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.ui.showDestructiveDialog
import dhp.thl.tpl.ndv.ui.showFolderPicker
import java.io.File
import kotlin.concurrent.thread

/**
 * Actions applied to several selected Home stickers at once. Long work runs off the UI thread behind a
 * [ProgressDialog] and the Home list is refreshed a single time at the end.
 */
class BatchActions(private val activity: BaseActivity) {

    /** Favorites everything unless it is all favorited already, in which case it un-favorites. */
    fun toggleFavorite(files: List<File>) {
        val favorites = StickerMetaStore.meta.value.favorites
        StickerMetaStore.setFavorite(activity, files.map { it.name }, !files.all { it.name in favorites })
    }

    fun moveToFolder(files: List<File>, onDone: () -> Unit) {
        val assignments = StickerMetaStore.meta.value.assignments
        val common = files.map { assignments[it.name] }.distinct().singleOrNull()
        activity.showFolderPicker(common) { folderId ->
            StickerMetaStore.assign(activity, files.map { it.name }, folderId)
            onDone()
        }
    }

    /** Adds the sticker border to every PNG in [files] (GIFs are skipped); each result is a new sticker. */
    fun addBorder(files: List<File>, onDone: () -> Unit) {
        val pngs = files.filter { it.name.endsWith(".png", ignoreCase = true) }
        if (pngs.isEmpty()) {
            ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
            return
        }
        runWithProgress(activity.getString(R.string.border_sticker), pngs, { file ->
            ImageUtils.addStickerBorder(activity, file) != null
        }) { succeeded ->
            StickerRepository.refreshHome(activity)
            ToastUtils.showToast(activity, activity.getString(if (succeeded > 0) R.string.success else R.string.failed))
            onDone()
        }
    }

    fun exportToGallery(files: List<File>, onDone: () -> Unit) {
        runWithProgress(activity.getString(R.string.exporting_to_gallery), files, { file ->
            BackupHelper.exportSingleStickerToGallery(activity, file)
        }) { succeeded ->
            ToastUtils.showToast(
                activity,
                if (succeeded > 0) activity.getString(R.string.export_gallery_success, succeeded)
                else activity.getString(R.string.failed)
            )
            onDone()
        }
    }

    fun delete(files: List<File>, onDone: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.boldTitle(activity.getString(R.string.delete_selected_title, files.size)))
            .setMessage(activity.getString(R.string.delete_selected_message))
            .setPositiveButton(activity.getString(R.string.action_delete)) { _, _ ->
                StickerRepository.deleteStickers(activity, files) { deleted ->
                    ToastUtils.showToast(activity, activity.getString(if (deleted > 0) R.string.success else R.string.failed))
                    onDone()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()
            .showDestructiveDialog(activity)
    }

    /** Runs [work] for each file on a background thread; [onFinished] gets the success count on the UI thread. */
    private fun runWithProgress(
        title: String,
        files: List<File>,
        work: (File) -> Boolean,
        onFinished: (Int) -> Unit
    ) {
        val progressDialog = ProgressDialog.newInstance(title)
        var cancelled = false
        progressDialog.onCancel = { cancelled = true }
        progressDialog.showSafe(activity.supportFragmentManager, "batch")

        thread {
            var succeeded = 0
            for ((index, file) in files.withIndex()) {
                if (cancelled) break
                val ok = try {
                    work(file)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (ok) succeeded++
                val progress = (index + 1).toFloat() / files.size
                activity.runOnUiThread { progressDialog.updateProgress(progress) }
            }
            activity.runOnUiThread {
                progressDialog.dismissSafe()
                onFinished(succeeded)
            }
        }
    }
}
