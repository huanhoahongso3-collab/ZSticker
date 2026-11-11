package dhp.thl.tpl.ndv.gif

import android.graphics.Bitmap
import android.graphics.Rect
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frame-level GIF operations shared by the upload path and the GIF crop feature:
 * decode every frame, optionally crop / downscale / drop frames, and re-encode.
 */
object GifProcessor {

    private val bitmapProvider = object : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) {}
        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) {}
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(array: IntArray) {}
    }

    /** One step of the progressive upload compression ladder. */
    private class Step(val maxSide: Int, val frameStep: Int, val quality: Int)

    // Strongest last. The hosted copy is converted to WebP server-side anyway, so it only has to stay
    // clearly recognisable; the original sticker on the device is never touched.
    private val uploadLadder = listOf(
        Step(384, 1, 10),
        Step(320, 2, 14),
        Step(256, 2, 20),
        Step(192, 3, 28),
        Step(160, 3, 40)
    )

    /** First frame of [file] at its full resolution, or null when it cannot be decoded. */
    fun firstFrame(file: File): Bitmap? {
        val bytes = file.readBytes()
        val parser = GifHeaderParser()
        parser.setData(bytes)
        val header = parser.parseHeader()
        if (header.numFrames <= 0) return null
        val decoder = StandardGifDecoder(bitmapProvider, header, ByteBuffer.wrap(bytes))
        decoder.advance()
        return decoder.nextFrame?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Compresses [src] into [dst] for hosting, trying progressively stronger settings until the file is
     * at most [maxBytes]. Returns true when [dst] was written (even if the last attempt is still larger,
     * the server decides whether it accepts it).
     */
    fun compressForUpload(src: File, dst: File, maxBytes: Long, onProgress: ((Float) -> Unit)? = null): Boolean {
        var wrote = false
        for ((index, step) in uploadLadder.withIndex()) {
            val ok = transform(src, dst, step.maxSide, null, step.frameStep, step.quality) { p ->
                onProgress?.invoke((index + p) / uploadLadder.size)
            }
            if (!ok) return wrote
            wrote = true
            if (dst.length() <= maxBytes) return true
        }
        return wrote
    }

    /**
     * Re-encodes [src] into [dst].
     * @param crop region in source pixels to keep, or null for the whole frame
     * @param maxSide longest side of the output; frames are scaled down (never up) to fit
     * @param frameStep keep every n-th frame; skipped frames' delays are added to the kept one
     * @param quality encoder sample factor, higher = smaller and rougher
     */
    fun transform(
        src: File,
        dst: File,
        maxSide: Int,
        crop: Rect?,
        frameStep: Int = 1,
        quality: Int = 10,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        return try {
            val bytes = src.readBytes()
            val parser = GifHeaderParser()
            parser.setData(bytes)
            val header = parser.parseHeader()
            if (header.numFrames <= 0) return false

            val decoder = StandardGifDecoder(bitmapProvider, header, ByteBuffer.wrap(bytes))
            decoder.advance()
            val frameCount = decoder.frameCount

            val region = crop?.let { clampToFrame(it, header.width, header.height) }
            val srcW = region?.width() ?: header.width
            val srcH = region?.height() ?: header.height
            val (targetW, targetH) = scaledSize(srcW, srcH, maxSide)

            val encoder = AnimatedGifEncoder()
            encoder.setRepeat(0)
            encoder.setSize(targetW, targetH)
            encoder.setQuality(quality)

            var pendingDelay = 0
            BufferedOutputStream(FileOutputStream(dst), 65536).use { out ->
                encoder.start(out)
                for (i in 0 until frameCount) {
                    val frame = decoder.nextFrame
                    pendingDelay += decoder.nextDelay.coerceAtLeast(40)
                    if (frame != null && i % frameStep.coerceAtLeast(1) == 0) {
                        encoder.setDelay(pendingDelay)
                        pendingDelay = 0

                        var bitmap: Bitmap = frame
                        if (region != null) {
                            bitmap = Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
                        }
                        if (bitmap.width != targetW || bitmap.height != targetH) {
                            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                            if (bitmap !== frame) bitmap.recycle()
                            bitmap = scaled
                        }
                        encoder.addFrame(bitmap)
                        if (bitmap !== frame) bitmap.recycle()
                    }
                    decoder.advance()
                    onProgress?.invoke((i + 1).toFloat() / frameCount)
                }
                encoder.finish()
            }
            dst.exists() && dst.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun clampToFrame(r: Rect, width: Int, height: Int): Rect {
        val left = r.left.coerceIn(0, width - 1)
        val top = r.top.coerceIn(0, height - 1)
        val right = r.right.coerceIn(left + 1, width)
        val bottom = r.bottom.coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun scaledSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(w, h)
        if (longest <= maxSide) return w to h
        val ratio = maxSide.toFloat() / longest
        return max(1, (w * ratio).roundToInt()) to max(1, (h * ratio).roundToInt())
    }
}
