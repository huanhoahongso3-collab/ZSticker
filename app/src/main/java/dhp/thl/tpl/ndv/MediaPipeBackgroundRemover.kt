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
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("selfie_segmenter.tflite")
            .build()
        val options = ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
        
        imageSegmenter = ImageSegmenter.createFromOptions(context, options)
    }

    fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = imageSegmenter ?: return null
        
        // 1. Convert Bitmap to MediaPipe Image
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = segmenter.segment(mpImage)
        
        // 2. Extract Category Mask safely
        val categoryMask = result.categoryMask().orElse(null) ?: return null
        
        val w = bitmap.width
        val h = bitmap.height
        
        // 3. Prepare Pixel Arrays
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        // 4. Extract ByteBuffer
        val byteBuffer: ByteBuffer = ByteBufferExtractor.extract(categoryMask)
        byteBuffer.rewind()
        
        /**
         * FIX FOR ANDROID 12:
         * In Android 12, bytes are strictly signed. A value of 255 (often used for the person mask) 
         * reads as -1 in a signed Byte. We must use 'and 0xFF' to get the unsigned value.
         */
        for (i in 0 until (w * h)) {
            // Use absolute positioning byteBuffer.get(i) for stability
            val maskValue = byteBuffer.get(i).toInt() and 0xFF
            
            // Usually, 0 is background, and > 0 (often 1 or 255) is the person.
            if (maskValue == 0) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        
        // 5. Create Result Bitmap
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        
        return outBitmap
    }

    fun close() {
        imageSegmenter?.close()
    }
}
