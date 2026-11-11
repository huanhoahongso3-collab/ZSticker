package dhp.thl.tpl.ndv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps the original photo behind a background-removed sticker, so the editor's Restore brush can bring
 * removed pixels back later. The stored bitmap is cropped to the same rectangle as the sticker, so the
 * two are pixel-aligned. Files live in `originals/` under the sticker's own file name.
 */
object OriginalStore {

    private fun dir(context: Context): File = File(context.filesDir, "originals").apply { mkdirs() }

    fun file(context: Context, stickerName: String): File = File(dir(context), stickerName)

    fun exists(context: Context, stickerName: String): Boolean = file(context, stickerName).exists()

    fun save(context: Context, stickerName: String, bitmap: Bitmap): Boolean = try {
        FileOutputStream(file(context, stickerName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    fun load(context: Context, stickerName: String): Bitmap? {
        val f = file(context, stickerName)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun copy(context: Context, fromSticker: String, toSticker: String) {
        val from = file(context, fromSticker)
        if (from.exists()) from.copyTo(file(context, toSticker), overwrite = true)
    }

    fun delete(context: Context, stickerName: String) {
        file(context, stickerName).delete()
    }

    fun deleteAll(context: Context) {
        File(context.filesDir, "originals").deleteRecursively()
    }

    /** Removes originals whose sticker no longer exists. */
    fun pruneMissing(context: Context) {
        File(context.filesDir, "originals").listFiles()?.forEach { f ->
            if (!File(context.filesDir, f.name).exists()) f.delete()
        }
    }
}
