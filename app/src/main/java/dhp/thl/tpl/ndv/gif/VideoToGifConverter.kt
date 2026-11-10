package dhp.thl.tpl.ndv.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object VideoToGifConverter {

    private const val MAX_STICKER_SIZE = 512
    const val MAX_VIDEO_STICKER_DURATION_MS = 3000L // 3.0 seconds

    /**
     * Reads the duration of the video in milliseconds.
     */
    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    /**
     * Converts a short video (or video segment) into a Zalo animated GIF sticker.
     * Extracts frames at a balanced frame rate (~10-12 FPS) within maxDurationMs,
     * scales to max 512px maintaining aspect ratio, and encodes via AnimatedGifEncoder.
     */
    fun convertVideoToGif(
        context: Context,
        uri: Uri,
        outputFile: File,
        startMs: Long = 0L,
        maxDurationMs: Long = MAX_VIDEO_STICKER_DURATION_MS,
        fps: Int = 10,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val durationMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            val availableMs = (durationMs - startMs).coerceAtLeast(100L)
            val actualDurationMs = availableMs.coerceAtMost(maxDurationMs)

            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 512
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 512

            val isRotated = rotation == 90 || rotation == 270
            val videoW = if (isRotated) rawH else rawW
            val videoH = if (isRotated) rawW else rawH

            val (targetW, targetH) = calculateScaledDimensions(videoW, videoH, MAX_STICKER_SIZE)

            val frameIntervalMs = 1000L / fps
            val frameIntervalUs = frameIntervalMs * 1000L
            val totalFrames = ((actualDurationMs + frameIntervalMs - 1) / frameIntervalMs).toInt().coerceAtLeast(1)

            val encoder = AnimatedGifEncoder()
            encoder.setRepeat(0)
            encoder.setSize(targetW, targetH)
            encoder.setDelay(frameIntervalMs.toInt())

            FileOutputStream(outputFile).use { fos ->
                encoder.start(fos)

                var frameIndex = 0
                var timeUs = startMs * 1000L
                val endUs = (startMs + actualDurationMs) * 1000L

                while (timeUs <= endUs && frameIndex < totalFrames) {
                    val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                    if (rawFrame != null) {
                        val orientedFrame = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            val rotated = Bitmap.createBitmap(rawFrame, 0, 0, rawFrame.width, rawFrame.height, matrix, true)
                            if (rotated != rawFrame) rawFrame.recycle()
                            rotated
                        } else {
                            rawFrame
                        }

                        val scaledFrame = if (orientedFrame.width != targetW || orientedFrame.height != targetH) {
                            val scaled = Bitmap.createScaledBitmap(orientedFrame, targetW, targetH, true)
                            if (scaled != orientedFrame) orientedFrame.recycle()
                            scaled
                        } else {
                            orientedFrame
                        }

                        encoder.addFrame(scaledFrame)
                        scaledFrame.recycle()
                    }

                    frameIndex++
                    timeUs += frameIntervalUs
                    onProgress?.invoke(frameIndex.toFloat() / totalFrames)
                }

                encoder.finish()
            }

            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
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
