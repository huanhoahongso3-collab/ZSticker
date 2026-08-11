package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Small bitmap helpers used by the online (BRIA RMBG) background removal path.
 * The on-device MediaPipe path does not use these.
 */
object ImageUtils {

    /**
     * Resizes a bitmap to a specific width while maintaining aspect ratio.
     * Used to shrink the upload before sending it to the remote API.
     */
    fun resizeBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Crops transparent pixels from the edges of the bitmap returned by the remote API.
     */
    fun cropTransparent(bitmap: Bitmap): Bitmap {
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

        top@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    minY = y
                    break@top
                }
            }
        }

        // If minY is still the original value, the whole image is transparent
        if (minY == height) return bitmap

        bottom@ for (y in height - 1 downTo minY) {
            for (x in 0 until width) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    maxY = y
                    break@bottom
                }
            }
        }

        left@ for (x in 0 until width) {
            for (y in minY..maxY) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    minX = x
                    break@left
                }
            }
        }

        right@ for (x in width - 1 downTo minX) {
            for (y in minY..maxY) {
                if (((pixels[y * width + x] shr 24) and 0xff) > alphaThreshold) {
                    maxX = x
                    break@right
                }
            }
        }

        if (maxX < minX || maxY < minY) return bitmap

        return Bitmap.createBitmap(bitmap, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1)
    }

    /**
     * Fixes bitmap rotation based on EXIF data before uploading.
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
