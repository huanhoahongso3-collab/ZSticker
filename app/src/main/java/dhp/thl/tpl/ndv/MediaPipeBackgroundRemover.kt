package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions
import java.nio.ByteBuffer

class MediaPipeBackgroundRemover(private val context: Context) {
    private var imageSegmenter: ImageSegmenter? = null

    init {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("selfie_segmenter.tflite")
            
            // FIX: Force CPU to avoid the OpenGL crashes shown in your logs
            baseOptionsBuilder.setDelegate(Delegate.CPU)
            
            val options = ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
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
        
        // Android 12 Fix: Ensure we are using a Software-backed bitmap
        val softwareBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }

        return try {
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
            
            // Safety: Use remaining() to prevent BufferUnderflow on different CPUs
            val bufferLimit = byteBuffer.remaining()
            for (i in 0 until (w * h)) {
                if (i < bufferLimit) {
                    val maskValue = byteBuffer.get().toInt() and 0xFF
                    // If maskValue is NOT 0, it's the background -> make transparent
                    if (maskValue != 0) {
                        pixels[i] = Color.TRANSPARENT
                    }
                }
            }
            
            val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            outBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (softwareBitmap != bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    fun close() {
        imageSegmenter?.close()
    }
}
