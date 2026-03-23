package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
    
    /**
     * Adds a white "bubble" border around the sticker.
     */
    fun addStickerBorder(context: Context, originalFile: File): File? {
        try {
            // 1. Load and resize bitmap to a manageable size (max 512) for speed
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            
            val inSampleSize = calculateInSampleSize(options, TARGET_WIDTH, TARGET_WIDTH)
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val tempBitmap = BitmapFactory.decodeFile(originalFile.absolutePath, decodeOptions) ?: return null
            
            // Ensure exact resize if still larger than target
            val originalBitmap = if (tempBitmap.width > TARGET_WIDTH || tempBitmap.height > TARGET_WIDTH) {
                val scale = TARGET_WIDTH.toFloat() / Math.max(tempBitmap.width, tempBitmap.height)
                val resized = Bitmap.createScaledBitmap(tempBitmap, (tempBitmap.width * scale).toInt(), (tempBitmap.height * scale).toInt(), true)
                if (resized != tempBitmap) tempBitmap.recycle()
                resized
            } else {
                tempBitmap
            }
            
            // Define border size
            val borderSize = 28
            val newWidth = originalBitmap.width + (borderSize * 2)
            val newHeight = originalBitmap.height + (borderSize * 2)
            
            val resultBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            
            // 2. Draw the white border (bubble)
            val borderPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                // Thresholding alpha to make the border solid and remove noise
                val cm = android.graphics.ColorMatrix(floatArrayOf(
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 20f, -2000f 
                ))
                colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            
            // 48 iterations is a good balance between speed and smoothness for a 28px border
            val iterations = 48
            for (i in 0 until iterations) {
                val angle = 2.0 * Math.PI * i / iterations
                val dx = (Math.cos(angle) * borderSize).toFloat()
                val dy = (Math.sin(angle) * borderSize).toFloat()
                canvas.drawBitmap(originalBitmap, borderSize + dx, borderSize + dy, borderPaint)
            }
            
            // 3. Draw the original bitmap on top
            canvas.drawBitmap(originalBitmap, borderSize.toFloat(), borderSize.toFloat(), paint)
            
            // 4. Save to a new file
            val newFile = File(context.filesDir, "zsticker_${System.currentTimeMillis()}.png")
            FileOutputStream(newFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Cleanup
            originalBitmap.recycle()
            resultBitmap.recycle()
            
            return newFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
