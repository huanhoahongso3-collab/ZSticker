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
            // Handle initialization failure (e.g., missing model file)
        }
    }

    fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = imageSegmenter ?: return null
        
        // 1. Force Software Bitmap to avoid HardwareBuffer issues on Android 11/12
        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null

        try {
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
            
            // 2. CRITICAL FIX: Some devices return a buffer larger/smaller than W*H 
            // due to row padding. We must use the buffer's actual limit.
            val bufferLimit = byteBuffer.remaining()
            val totalPixels = w * h
            
            for (i in 0 until totalPixels) {
                // Safety check to prevent crashes on specific Android 9/11 devices
                if (i < bufferLimit) {
                    val maskValue = byteBuffer.get().toInt() and 0xFF
                    
                    // Logic: If maskValue is 0, it's typically background in Selfie Segmenter
                    // Adjust this if your subject is the one being removed
                    if (maskValue != 0) { 
                        pixels[i] = Color.TRANSPARENT
                    }
                }
            }
            
            val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            
            return outBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            softwareBitmap.recycle()
        }
    }

    fun close() {
        imageSegmenter?.close()
        imageSegmenter = null
    }
}
