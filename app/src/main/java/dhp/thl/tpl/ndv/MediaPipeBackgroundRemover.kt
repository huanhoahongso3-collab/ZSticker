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
        setupSegmenter()
    }

    private fun setupSegmenter() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("selfie_segmenter.tflite")
            
            // FIX for Android 12/Emulator: Force CPU Delegate.
            // Your log shows 'glCreateShader' failed (Error 0x500).
            // This bypasses the buggy GPU drivers entirely.
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
        // If segmenter failed to init, try one more time or return null
        val segmenter = imageSegmenter ?: return null
        
        // Android 12 FIX: 'HARDWARE' bitmaps cannot be read by MediaPipe.
        // We MUST create a software-backed copy (ARGB_8888).
        val softwareBitmap = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            null
        } ?: return null

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
            
            // LOGIC FIX: Buffer handling for different CPU architectures.
            // Some devices have 'padding' at the end of the buffer.
            val limit = byteBuffer.remaining()
            for (i in 0 until (w * h)) {
                if (i < limit) {
                    val maskValue = byteBuffer.get().toInt() and 0xFF
                    // If maskValue is NOT 0, it's background -> make transparent.
                    // (Category 0 is usually the Person in Selfie Segmenter).
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
            // Always recycle to prevent OutOfMemory crashes on older devices
            if (softwareBitmap != bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    fun close() {
        imageSegmenter?.close()
        imageSegmenter = null
    }
}
