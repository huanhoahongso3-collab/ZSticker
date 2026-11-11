package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Matrix
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.FutureTarget
import java.io.File
import java.io.FileOutputStream
import androidx.exifinterface.media.ExifInterface

object ImageUtils {
    private const val TARGET_WIDTH = 512
    private const val CACHE_DIR_NAME = "zalo_cache"

    /**
     * Synchronously decodes and downsamples an image source ([Uri], [File], [ByteArray], etc.)
     * on a background thread using Glide's Downsampler and BitmapPool, returning an owned
     * ARGB_8888 [Bitmap] copy so callers can safely mutate or recycle it without corrupting
     * Glide's pool. Automatically applies EXIF orientation.
     */
    fun loadSampledBitmapWithGlide(
        context: Context,
        model: Any,
        targetWidth: Int = TARGET_WIDTH,
        targetHeight: Int = TARGET_WIDTH,
        upscaleToFit: Boolean = false
    ): Bitmap? {
        val appContext = context.applicationContext
        var futureTarget: FutureTarget<Bitmap>? = null
        return try {
            val request = Glide.with(appContext)
                .asBitmap()
                .load(model)
                .override(targetWidth, targetHeight)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
            val transformed = if (upscaleToFit) {
                request.downsample(DownsampleStrategy.FIT_CENTER).fitCenter()
            } else {
                request.downsample(DownsampleStrategy.CENTER_INSIDE).centerInside()
            }
            futureTarget = transformed.submit()
            val pooledBitmap = futureTarget.get()
            pooledBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (futureTarget != null) {
                try {
                    Glide.with(appContext).clear(futureTarget)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** User option (default ON): resize stickers to 512px when sharing to Zalo. */
    private fun isAutoResizeEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("auto_resize_share", true)

    /**
     * Prepares an animated sticker for Zalo sharing (max 512px).
     * If already <= 512px, uses it directly; otherwise re-encodes and caches the result.
     */
    fun getZaloAnimatedSticker(context: Context, originalFile: File): File {
        if (!isAutoResizeEnabled(context)) return originalFile
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val cachedFileName = "zalo_512_${originalFile.name}"
        val cachedFile = File(cacheDir, cachedFileName)

        if (cachedFile.exists() && cachedFile.lastModified() >= originalFile.lastModified()) {
            return cachedFile
        }

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            if (options.outWidth <= TARGET_WIDTH && options.outHeight <= TARGET_WIDTH && options.outWidth > 0) {
                originalFile
            } else {
                val success = dhp.thl.tpl.ndv.gif.AnimatedImageConverter.convertToZaloGif(
                    context,
                    Uri.fromFile(originalFile),
                    cachedFile
                )
                if (success && cachedFile.exists() && cachedFile.length() > 0) cachedFile else originalFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            originalFile
        }
    }

    /**
     * Resizes a sticker for Zalo sharing to 512px.
     * Keeps the original aspect ratio and resizes to [TARGET_WIDTH].
     * Caches the result to avoid redundant processing.
     */
    fun getZaloSticker(context: Context, originalFile: File): File {
        if (!isAutoResizeEnabled(context)) return originalFile
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

            // 3. Decode and resize via Glide BitmapPool + Downsampler to avoid OOM
            val scaledBitmap = loadSampledBitmapWithGlide(
                context = context,
                model = originalFile,
                targetWidth = TARGET_WIDTH,
                targetHeight = targetHeight,
                upscaleToFit = true
            ) ?: return originalFile

            // 4. Save to cache as PNG (to preserve transparency)
            FileOutputStream(compressedFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Cleanup
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
            // 1. Load and downsample bitmap to a manageable size (max 512) via Glide
            val originalBitmap = loadSampledBitmapWithGlide(
                context = context,
                model = originalFile,
                targetWidth = TARGET_WIDTH,
                targetHeight = TARGET_WIDTH,
                upscaleToFit = false
            ) ?: return null
            
            // Define border size
            val borderSize = 17
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
            
            // 48 iterations is a good balance between speed and smoothness for a 17px border
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

    /**
     * Resizes a bitmap to a specific width while maintaining aspect ratio.
     */
    fun resizeBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Crops transparent pixels from the edges of the bitmap.
     */
    fun cropTransparent(bitmap: Bitmap): Bitmap {
        val rect = cropTransparentRect(bitmap) ?: return bitmap
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }

    /**
     * The bounding box of the non-transparent pixels, or null when the bitmap is fully transparent.
     * Lets callers crop a second, pixel-aligned bitmap (the original photo) with the same rectangle.
     */
    fun cropTransparentRect(bitmap: Bitmap): android.graphics.Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        // Threshold of 1 to ignore almost invisible noise
        val alphaThreshold = 1

        // Find minY
        top@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    minY = y
                    break@top
                }
            }
        }

        // If minY is still original value, it means the whole image is transparent
        if (minY == height) return null

        // Find maxY
        bottom@ for (y in height - 1 downTo minY) {
            for (x in 0 until width) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    maxY = y
                    break@bottom
                }
            }
        }

        // Find minX
        left@ for (x in 0 until width) {
            for (y in minY..maxY) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    minX = x
                    break@left
                }
            }
        }

        // Find maxX
        right@ for (x in width - 1 downTo minX) {
            for (y in minY..maxY) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    maxX = x
                    break@right
                }
            }
        }

        if (maxX < minX || maxY < minY) return null

        return android.graphics.Rect(minX, minY, maxX + 1, maxY + 1)
    }
    
    /**
     * Fixes bitmap rotation based on EXIF data.
     */
    fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val ei = ExifInterface(inputStream)
                val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                
                return when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                    else -> bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmap
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }
}
