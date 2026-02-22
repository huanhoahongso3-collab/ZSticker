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

    /**
     * Removes the background from a bitmap.
     * Fixes: Android 12 compatibility and reversed mask logic.
     */
    fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = imageSegmenter ?: return null
        
        // 1. Prepare MediaPipe Image
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = segmenter.segment(mpImage)
        
        // 2. Get the Category Mask
        val categoryMask = result.categoryMask().orElse(null) ?: return null
        
        val w = bitmap.width
        val h = bitmap.height
        
        // 3. Extract pixels into a mutable array
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        // 4. Extract the mask buffer
        val byteBuffer: ByteBuffer = ByteBufferExtractor.extract(categoryMask)
        byteBuffer.rewind()
        
        // 5. Process the mask
        for (i in 0 until (w * h)) {
            // Android 12 Fix: 'and 0xFF' ensures we treat the byte as unsigned (0-255)
            // Absolute 'get(i)' prevents buffer position issues
            val maskValue = byteBuffer.get(i).toInt() and 0xFF
            
            /**
             * LOGIC FIX:
             * Based on your results, the model identifies the PERSON as Category 0.
             * Therefore, if the mask value is NOT 0, it's background -> make it transparent.
             */
            if (maskValue != 0) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        
        // 6. Output the processed bitmap
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        
        return outBitmap
    }

    fun close() {
        imageSegmenter?.close()
    }
}
