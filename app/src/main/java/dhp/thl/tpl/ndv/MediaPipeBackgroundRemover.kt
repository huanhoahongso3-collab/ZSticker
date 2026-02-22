package dhp.thl.tpl.ndv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions

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
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = segmenter.segment(mpImage)
        
        val categoryMask = result.categoryMask()
        if (!categoryMask.isPresent) return null
        
        val w = bitmap.width
        val h = bitmap.height
        
        val origPixels = IntArray(w * h)
        bitmap.getPixels(origPixels, 0, w, 0, 0, w, h)
        
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        // Use ByteBufferExtractor
        val byteBuffer = com.google.mediapipe.framework.image.ByteBufferExtractor.extract(categoryMask.get())
        byteBuffer.rewind()
        
        for (i in 0 until (w * h)) {
            val category = byteBuffer.get().toInt()
            // Invert the mapping to fix the mask where subject was being removed
            if (category != 0) {
                origPixels[i] = Color.TRANSPARENT
            }
        }
        
        outBitmap.setPixels(origPixels, 0, w, 0, 0, w, h)
        return outBitmap
    }
}
