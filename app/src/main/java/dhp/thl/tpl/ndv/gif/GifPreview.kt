package dhp.thl.tpl.ndv.gif

import android.graphics.Bitmap
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A GIF decoded once into small in-memory frames, so the crop screen can play and scrub it instantly.
 * Frames are scaled down (and thinned out for very long GIFs) to stay within [MAX_PREVIEW_BYTES].
 */
class GifPreview(
    val frames: List<Bitmap>,
    /** Display time of each preview frame in ms (the delays of skipped frames are folded in). */
    val delaysMs: IntArray,
    /** Size of the real GIF; crop rectangles are expressed in these pixels. */
    val sourceWidth: Int,
    val sourceHeight: Int
) {
    private val starts = IntArray(delaysMs.size).also { out ->
        var t = 0
        for (i in delaysMs.indices) {
            out[i] = t
            t += delaysMs[i]
        }
    }

    val totalMs: Int = delaysMs.sum()

    /** Playback position (ms) at which frame [index] starts. */
    fun startOf(index: Int): Int = starts[index.coerceIn(0, starts.lastIndex)]

    fun recycle() {
        frames.forEach { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        private const val MAX_PREVIEW_BYTES = 48L * 1024 * 1024
        private const val MAX_PREVIEW_SIDE = 480
        private const val MAX_PREVIEW_FRAMES = 240

        private val bitmapProvider = object : GifDecoder.BitmapProvider {
            override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
                Bitmap.createBitmap(width, height, config)
            override fun release(bitmap: Bitmap) {}
            override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
            override fun release(bytes: ByteArray) {}
            override fun obtainIntArray(size: Int): IntArray = IntArray(size)
            override fun release(array: IntArray) {}
        }

        /** Blocking; call from a background thread. Returns null when the file is not a decodable GIF. */
        fun load(file: File, onProgress: ((Float) -> Unit)? = null): GifPreview? = try {
            val bytes = file.readBytes()
            val parser = GifHeaderParser()
            parser.setData(bytes)
            val header = parser.parseHeader()
            if (header.numFrames <= 0) {
                null
            } else {
                val decoder = StandardGifDecoder(bitmapProvider, header, ByteBuffer.wrap(bytes))
                decoder.advance()
                val total = decoder.frameCount
                val step = max(1, (total + MAX_PREVIEW_FRAMES - 1) / MAX_PREVIEW_FRAMES)
                val kept = (total + step - 1) / step

                val srcW = header.width
                val srcH = header.height
                var scale = min(1f, MAX_PREVIEW_SIDE.toFloat() / max(srcW, srcH))
                val bytesPerFull = srcW.toLong() * srcH * 4
                val budget = MAX_PREVIEW_BYTES / kept
                if (bytesPerFull * scale * scale > budget) {
                    scale = sqrt(budget.toFloat() / bytesPerFull)
                }
                val previewW = max(1, (srcW * scale).roundToInt())
                val previewH = max(1, (srcH * scale).roundToInt())

                val frames = ArrayList<Bitmap>(kept)
                val delays = ArrayList<Int>(kept)
                var pending = 0
                for (i in 0 until total) {
                    val frame = decoder.nextFrame
                    pending += decoder.nextDelay.coerceAtLeast(20)
                    if (frame != null && i % step == 0) {
                        frames.add(Bitmap.createScaledBitmap(frame, previewW, previewH, true))
                        delays.add(pending)
                        pending = 0
                    }
                    decoder.advance()
                    onProgress?.invoke((i + 1).toFloat() / total)
                }
                if (frames.isEmpty()) null else GifPreview(frames, delays.toIntArray(), srcW, srcH)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
