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

/**
 * On-device background removal powered by Google's open-source MediaPipe
 * Tasks Vision Image Segmenter (Apache 2.0), using the bundled
 * `selfie_segmenter.tflite` model in `assets/`.
 *
 * This replaces the previous ML Kit Selfie Segmentation implementation,
 * which relies on a closed-source model.
 */
class MediaPipeBackgroundRemover(private val context: Context) {

    private var imageSegmenter: ImageSegmenter? = null

    /**
     * Lazily creates the segmenter on first use so construction of this
     * class stays cheap and any model-loading failure is contained to the
     * first actual segmentation attempt.
     */
    private fun getOrCreateSegmenter(): ImageSegmenter? {
        imageSegmenter?.let { return it }
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("selfie_segmenter.tflite")
                // Force CPU delegate: GPU delegates are unreliable across the
                // wide range of devices/emulators this app targets.
                .setDelegate(Delegate.CPU)
                .build()

            val options = ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()

            ImageSegmenter.createFromOptions(context, options).also { imageSegmenter = it }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Runs segmentation on [bitmap] and returns a new bitmap with the
     * background pixels made transparent, or null on failure.
     */
    fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = getOrCreateSegmenter() ?: return null

        // MediaPipe cannot read hardware-backed bitmaps; ensure a software copy.
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
            val width = mask.width
            val height = mask.height

            val pixels = IntArray(width * height)
            softwareBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val maskBuffer = ByteBufferExtractor.extract(mask)
            maskBuffer.rewind()
            val limit = maskBuffer.remaining()

            // Category 0 is the person/foreground for the selfie segmenter model;
            // any other category value marks background, which becomes transparent.
            for (i in 0 until width * height) {
                if (i >= limit) break
                val category = maskBuffer.get().toInt() and 0xFF
                if (category != 0) {
                    pixels[i] = Color.TRANSPARENT
                }
            }

            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            outputBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (softwareBitmap != bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    /** Releases native MediaPipe resources. Call from onDestroy(). */
    fun close() {
        imageSegmenter?.close()
        imageSegmenter = null
    }
}
