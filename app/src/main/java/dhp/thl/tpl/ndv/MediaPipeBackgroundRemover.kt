package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions
import java.nio.ByteBuffer

class MediaPipeBackgroundRemover(private val context: Context) {
    private var imageSegmenter: ImageSegmenter? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("selfie_segmenter.tflite")
                .build()
            val options = ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()
            
            imageSegmenter = ImageSegmenter.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = imageSegmenter ?: return null
        
        // Fix for Android 12+ Hardware Bitmaps & Android 8 Software handling
        val softwareBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(softwareBitmap).build()
        val result = segmenter.segment(mpImage)
        val categoryMask = result.categoryMask()
        
        if (!categoryMask.isPresent) return null
        
        val mask = categoryMask.get()
        val w = mask.width
        val h = mask.height
        
        val pixels = IntArray(w * h)
        softwareBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        val byteBuffer: ByteBuffer = ByteBufferExtractor.extract(mask)
        byteBuffer.rewind()
        
        val limit = byteBuffer.limit()
        for (i in 0 until (w * h)) {
            if (i < limit) {
                val maskValue = byteBuffer.get(i).toInt() and 0xFF
                // If mask is NOT the subject, make it transparent
                if (maskValue != 0) {
                    pixels[i] = Color.TRANSPARENT
                }
            }
        }
        
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        
        // Clean up temporary bitmap if one was created
        if (softwareBitmap != bitmap) {
            softwareBitmap.recycle()
        }
        
        return outBitmap
    }

    fun close() {
        imageSegmenter?.close()
    }
}
