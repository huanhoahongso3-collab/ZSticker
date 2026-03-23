package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    private const val TARGET_WIDTH = 512
    private const val CACHE_DIR_NAME = "zalo_cache"

    /**
     * Resizes a sticker for Zalo sharing to 512px.
     * Keeps the original aspect ratio and resizes to [TARGET_WIDTH].
     * Caches the result to avoid redundant processing.
     */
    fun getZaloSticker(context: Context, originalFile: File): File {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Use a unique name based on original file name and its last modified time to ensure freshness
        val cachedFileName = "zalo_512_${originalFile.name}"
        val compressedFile = File(cacheDir, cachedFileName)

        // If cached file exists and is newer than original, skip compression
        if (compressedFile.exists() && compressedFile.lastModified() >= originalFile.lastModified()) {
            return compressedFile
        }

        try {
            // 1. Get dimensions without loading full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) return originalFile

            // 2. Calculate target height maintaining aspect ratio
            val scale = TARGET_WIDTH.toFloat() / originalWidth
            val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

            // 3. Load and resize bitmap
            val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return originalFile
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, TARGET_WIDTH, targetHeight, true)

            // 4. Save to cache as PNG (to preserve transparency)
            FileOutputStream(compressedFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Cleanup
            if (originalBitmap != scaledBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()

            return compressedFile
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to original file if compression fails
            return originalFile
        }
    }
}
