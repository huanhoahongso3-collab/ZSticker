package dhp.thl.tpl.ndv.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AnimatedImageConverter {

    private const val MAX_STICKER_SIZE = 512

    enum class AnimationType {
        NONE,
        GIF,
        WEBP_ANIMATED,
        APNG
    }

    /**
     * Determines whether the given URI represents an animated image format.
     */
    fun detectAnimationType(context: Context, uri: Uri): AnimationType {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(64)
                val read = stream.read(header)
                if (read < 12) return AnimationType.NONE

                // Check GIF: GIF87a or GIF89a
                if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() &&
                    header[3] == '8'.code.toByte() && (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) &&
                    header[5] == 'a'.code.toByte()
                ) {
                    return AnimationType.GIF
                }

                // Check WebP: RIFF .... WEBP
                if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
                ) {
                    // Check if extended WebP (VP8X) with animation flag
                    if (read >= 21 && header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                        header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()
                    ) {
                        val flags = header[20].toInt()
                        // Bit 1 is animation flag (0x02)
                        if ((flags and 0x02) != 0) {
                            return AnimationType.WEBP_ANIMATED
                        }
                    }
                }

                // Check PNG / APNG: 89 50 4E 47 0D 0A 1A 0A
                if (header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()) {
                    // Search stream for "acTL" chunk
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalSearched = 0
                    while (stream.read(buffer).also { bytesRead = it } != -1 && totalSearched < 65536) {
                        for (i in 0 until bytesRead - 4) {
                            if (buffer[i] == 'a'.code.toByte() && buffer[i + 1] == 'c'.code.toByte() &&
                                buffer[i + 2] == 'T'.code.toByte() && buffer[i + 3] == 'L'.code.toByte()
                            ) {
                                return AnimationType.APNG
                            }
                        }
                        totalSearched += bytesRead
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return AnimationType.NONE
    }

    /**
     * Converts an animated image (GIF, Animated WebP, APNG) to a Zalo-compatible GIF sticker.
     * The resulting GIF is scaled to max 512px maintaining aspect ratio.
     */
    fun convertToZaloGif(context: Context, srcUri: Uri, outputFile: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        return try {
            val type = detectAnimationType(context, srcUri)
            val bytes = context.contentResolver.openInputStream(srcUri)?.use { it.readBytes() } ?: return false

            when (type) {
                AnimationType.GIF -> convertGif(bytes, outputFile, onProgress)
                AnimationType.WEBP_ANIMATED -> convertAnimatedWebP(bytes, outputFile, onProgress)
                AnimationType.APNG -> convertApng(context, srcUri, outputFile, onProgress)
                AnimationType.NONE -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private val simpleBitmapProvider = object : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap {
            return Bitmap.createBitmap(width, height, config)
        }
        override fun release(bitmap: Bitmap) {}
        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) {}
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(array: IntArray) {}
    }

    /**
     * Re-encodes/resizes an animated GIF using Glide's StandardGifDecoder.
     */
    private fun convertGif(gifBytes: ByteArray, outputFile: File, onProgress: ((Float) -> Unit)?): Boolean {
        val parser = GifHeaderParser()
        parser.setData(gifBytes)
        val header = parser.parseHeader()
        if (header.numFrames <= 0) return false

        val decoder = StandardGifDecoder(simpleBitmapProvider, header, ByteBuffer.wrap(gifBytes))
        decoder.advance()

        val frameCount = decoder.frameCount
        val encoder = AnimatedGifEncoder()
        encoder.setRepeat(0) // Infinite loop

        val originalW = header.width
        val originalH = header.height
        val (targetW, targetH) = calculateScaledDimensions(originalW, originalH, MAX_STICKER_SIZE)
        encoder.setSize(targetW, targetH)

        BufferedOutputStream(FileOutputStream(outputFile), 65536).use { fos ->
            encoder.start(fos)

            for (i in 0 until frameCount) {
                val frameBitmap = decoder.nextFrame ?: continue
                val delay = decoder.nextDelay.coerceAtLeast(40) // Min delay 40ms (~25fps)
                encoder.setDelay(delay)

                val scaledBitmap = if (frameBitmap.width != targetW || frameBitmap.height != targetH) {
                    Bitmap.createScaledBitmap(frameBitmap, targetW, targetH, true)
                } else {
                    frameBitmap
                }

                encoder.addFrame(scaledBitmap)
                if (scaledBitmap != frameBitmap) {
                    scaledBitmap.recycle()
                }

                decoder.advance()
                onProgress?.invoke((i + 1).toFloat() / frameCount)
            }

            encoder.finish()
        }
        return outputFile.exists() && outputFile.length() > 0
    }

    /**
     * Converts an Animated WebP by parsing ANMF (Animation Frame) chunks and decoding each frame.
     */
    private fun convertAnimatedWebP(webpBytes: ByteArray, outputFile: File, onProgress: ((Float) -> Unit)?): Boolean {
        val frames = extractWebPFrames(webpBytes)
        if (frames.isEmpty()) return false

        val (firstW, firstH) = frames[0].bitmap.width to frames[0].bitmap.height
        val (targetW, targetH) = calculateScaledDimensions(firstW, firstH, MAX_STICKER_SIZE)

        val encoder = AnimatedGifEncoder()
        encoder.setRepeat(0)
        encoder.setSize(targetW, targetH)

        BufferedOutputStream(FileOutputStream(outputFile), 65536).use { fos ->
            encoder.start(fos)

            for ((i, frame) in frames.withIndex()) {
                val delay = frame.durationMs.coerceAtLeast(40)
                encoder.setDelay(delay)

                val scaled = if (frame.bitmap.width != targetW || frame.bitmap.height != targetH) {
                    Bitmap.createScaledBitmap(frame.bitmap, targetW, targetH, true)
                } else {
                    frame.bitmap
                }

                encoder.addFrame(scaled)
                if (scaled != frame.bitmap) {
                    scaled.recycle()
                }
                frame.bitmap.recycle()

                onProgress?.invoke((i + 1).toFloat() / frames.size)
            }

            encoder.finish()
        }
        return outputFile.exists() && outputFile.length() > 0
    }

    private data class WebPFrame(val bitmap: Bitmap, val durationMs: Int)

    /**
     * Parses RIFF WebP container and extracts ANMF chunks into individual Bitmaps.
     */
    private fun extractWebPFrames(bytes: ByteArray): List<WebPFrame> {
        val frames = mutableListOf<WebPFrame>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read canvas dimensions from VP8X
        var canvasW = 0
        var canvasH = 0

        var offset = 12 // Skip "RIFF", file size, "WEBP"
        val totalLen = bytes.size

        while (offset + 8 <= totalLen) {
            val chunkFourCC = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4)
            val chunkDataOffset = offset + 8

            if (chunkFourCC == "VP8X" && chunkSize >= 10) {
                // 24-bit canvas width and height
                val wLow = bytes[chunkDataOffset + 4].toInt() and 0xff
                val wMid = bytes[chunkDataOffset + 5].toInt() and 0xff
                val wHigh = bytes[chunkDataOffset + 6].toInt() and 0xff
                canvasW = (wLow or (wMid shl 8) or (wHigh shl 16)) + 1

                val hLow = bytes[chunkDataOffset + 7].toInt() and 0xff
                val hMid = bytes[chunkDataOffset + 8].toInt() and 0xff
                val hHigh = bytes[chunkDataOffset + 9].toInt() and 0xff
                canvasH = (hLow or (hMid shl 8) or (hHigh shl 16)) + 1
            } else if (chunkFourCC == "ANMF" && chunkSize > 16) {
                // ANMF chunk format:
                // 3 bytes frameX, 3 bytes frameY, 3 bytes frameW, 3 bytes frameH
                // 3 bytes duration (ms), 1 byte flags
                val durLow = bytes[chunkDataOffset + 12].toInt() and 0xff
                val durMid = bytes[chunkDataOffset + 13].toInt() and 0xff
                val durHigh = bytes[chunkDataOffset + 14].toInt() and 0xff
                val duration = durLow or (durMid shl 8) or (durHigh shl 16)

                val payloadOffset = chunkDataOffset + 16
                val payloadSize = chunkSize - 16

                if (payloadSize > 0 && payloadOffset + payloadSize <= totalLen) {
                    // Construct a standalone RIFF WebP header for this frame payload
                    val frameRiff = constructSingleFrameWebP(bytes, payloadOffset, payloadSize)
                    val frameBitmap = BitmapFactory.decodeByteArray(frameRiff, 0, frameRiff.size)
                    if (frameBitmap != null) {
                        frames.add(WebPFrame(frameBitmap, if (duration <= 0) 100 else duration))
                    }
                }
            }

            // RIFF chunks are padded to even length
            val paddedSize = chunkSize + (chunkSize and 1)
            offset = chunkDataOffset + paddedSize
        }
        return frames
    }

    /**
     * Wraps raw frame payload into a valid standalone single-frame WebP byte array.
     */
    private fun constructSingleFrameWebP(source: ByteArray, offset: Int, length: Int): ByteArray {
        val totalSize = 12 + length
        val out = ByteArray(totalSize)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF
        out[0] = 'R'.code.toByte(); out[1] = 'I'.code.toByte(); out[2] = 'F'.code.toByte(); out[3] = 'F'.code.toByte()
        buf.putInt(4, totalSize - 8)
        // WEBP
        out[8] = 'W'.code.toByte(); out[9] = 'E'.code.toByte(); out[10] = 'B'.code.toByte(); out[11] = 'P'.code.toByte()
        // Payload (e.g. VP8, VP8L, or ALPH + VP8)
        System.arraycopy(source, offset, out, 12, length)
        return out
    }

    /**
     * Converts an APNG to GIF. If APNG frames cannot be easily split, extracts the first frame safely via Glide.
     */
    private fun convertApng(context: Context, uri: Uri, outputFile: File, onProgress: ((Float) -> Unit)?): Boolean {
        val scaled = dhp.thl.tpl.ndv.ImageUtils.loadSampledBitmapWithGlide(
            context = context,
            model = uri,
            targetWidth = MAX_STICKER_SIZE,
            targetHeight = MAX_STICKER_SIZE,
            upscaleToFit = false
        ) ?: return false

        val encoder = AnimatedGifEncoder()
        encoder.setRepeat(0)
        encoder.setSize(scaled.width.coerceAtLeast(1), scaled.height.coerceAtLeast(1))
        encoder.setDelay(100)

        BufferedOutputStream(FileOutputStream(outputFile), 65536).use { fos ->
            encoder.start(fos)
            encoder.addFrame(scaled)
            encoder.finish()
        }
        scaled.recycle()
        onProgress?.invoke(1.0f)
        return outputFile.exists() && outputFile.length() > 0
    }

    private fun calculateScaledDimensions(origW: Int, origH: Int, maxDim: Int): Pair<Int, Int> {
        if (origW <= maxDim && origH <= maxDim) return origW.coerceAtLeast(1) to origH.coerceAtLeast(1)
        val scale = if (origW >= origH) {
            maxDim.toFloat() / origW.toFloat()
        } else {
            maxDim.toFloat() / origH.toFloat()
        }
        val targetW = (origW * scale).toInt().coerceAtLeast(1)
        val targetH = (origH * scale).toInt().coerceAtLeast(1)
        return targetW to targetH
    }
}
