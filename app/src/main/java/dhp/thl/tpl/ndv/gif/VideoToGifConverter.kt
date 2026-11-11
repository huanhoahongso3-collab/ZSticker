package dhp.thl.tpl.ndv.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object VideoToGifConverter {

    private const val MAX_STICKER_SIZE = 512
    const val MAX_VIDEO_STICKER_DURATION_MS = 5000L // 5.0 seconds

    /**
     * Reads the duration of the video in milliseconds.
     */
    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            setRetrieverDataSource(context, retriever, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            retriever.setDataSource(path)
        } else {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use { retriever.setDataSource(it.fileDescriptor) }
                    return
                }
            } catch (_: Exception) {
                // fallback
            }
            retriever.setDataSource(context, uri)
        }
    }

    /**
     * Converts a short video (or video segment) into a Zalo animated GIF sticker.
     * Primary path streams zero-Bitmap RGB888 frames from GPU OES Surface decoding directly into
     * [AnimatedGifEncoder.ParallelOpaqueSession] across CPU cores.
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
            setRetrieverDataSource(context, retriever, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: maxDurationMs
            val safeStartMs = startMs.coerceAtLeast(0L).coerceAtMost((durationMs - 50L).coerceAtLeast(0L))
            val availableMs = (durationMs - safeStartMs).coerceAtLeast(100L)
            val actualDurationMs = availableMs.coerceAtMost(maxDurationMs)

            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 512
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 512

            val isRotated = rotation == 90 || rotation == 270
            val videoW = if (isRotated) rawH else rawW
            val videoH = if (isRotated) rawW else rawH

            val (targetW, targetH) = calculateScaledEvenDimensions(videoW, videoH, MAX_STICKER_SIZE)
            val frameIntervalMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(40L)

            // 1. Primary Zero-Bitmap GPU Surface + Multi-Core Parallel GIF Pipeline
            var framesAdded = encodeViaGpuSurface(
                context = context,
                uri = uri,
                outputFile = outputFile,
                startMs = safeStartMs,
                actualDurationMs = actualDurationMs,
                fps = fps,
                frameIntervalMs = frameIntervalMs,
                targetW = targetW,
                targetH = targetH,
                fallbackRotation = rotation,
                onProgress = onProgress
            )

            // 2. Hardened scaled ARGB_8888 fallback if MediaExtractor/EGL couldn't decode this container
            if (framesAdded <= 0) {
                if (outputFile.exists()) outputFile.delete()
                framesAdded = encodeViaScaledRetrieverFallback(
                    retriever = retriever,
                    outputFile = outputFile,
                    startMs = safeStartMs,
                    actualDurationMs = actualDurationMs,
                    frameIntervalMs = frameIntervalMs,
                    rawW = rawW,
                    rawH = rawH,
                    rotation = rotation,
                    targetW = targetW,
                    targetH = targetH,
                    onProgress = onProgress
                )
            }

            if (framesAdded > 0 && outputFile.exists() && outputFile.length() > 0) {
                true
            } else {
                outputFile.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                if (outputFile.exists()) outputFile.delete()
            } catch (_: Exception) {}
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun encodeViaGpuSurface(
        context: Context,
        uri: Uri,
        outputFile: File,
        startMs: Long,
        actualDurationMs: Long,
        fps: Int,
        frameIntervalMs: Long,
        targetW: Int,
        targetH: Int,
        fallbackRotation: Int,
        onProgress: ((Float) -> Unit)?
    ): Int {
        return try {
            BufferedOutputStream(FileOutputStream(outputFile), 65536).use { bos ->
                val session = AnimatedGifEncoder.ParallelOpaqueSession(
                    rawOut = bos,
                    width = targetW,
                    height = targetH,
                    delayMs = frameIntervalMs.toInt()
                )
                try {
                    VideoSurfaceFrameExtractor.extractRgbFrames(
                        context = context,
                        uri = uri,
                        startMs = startMs,
                        durationMs = actualDurationMs,
                        fps = fps,
                        targetW = targetW,
                        targetH = targetH,
                        fallbackRotation = fallbackRotation
                    ) { frameIndex, totalFrames, rgbBytes ->
                        session.submitFrame(rgbBytes)
                        // Reserve the final 8% of progress for draining parallel LZW futures
                        val p = ((frameIndex + 1).toFloat() / totalFrames.toFloat()) * 0.92f
                        onProgress?.invoke(p.coerceIn(0f, 0.95f))
                    }
                    val total = session.finish()
                    if (total > 0) {
                        onProgress?.invoke(1.0f)
                    }
                    total
                } finally {
                    // Idempotent cleanup if an exception occurred mid-stream
                    session.finish()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun encodeViaScaledRetrieverFallback(
        retriever: MediaMetadataRetriever,
        outputFile: File,
        startMs: Long,
        actualDurationMs: Long,
        frameIntervalMs: Long,
        rawW: Int,
        rawH: Int,
        rotation: Int,
        targetW: Int,
        targetH: Int,
        onProgress: ((Float) -> Unit)?
    ): Int {
        val frameIntervalUs = frameIntervalMs * 1000L
        val totalFrames = ((actualDurationMs + frameIntervalMs - 1L) / frameIntervalMs)
            .toInt()
            .coerceAtLeast(1)

        val isRotated = rotation == 90 || rotation == 270
        val reqW = if (isRotated) targetH else targetW
        val reqH = if (isRotated) targetW else targetH

        val encoder = AnimatedGifEncoder().apply {
            setRepeat(0)
            setSize(targetW, targetH)
            setDelay(frameIntervalMs.toInt())
        }

        var framesAdded = 0
        BufferedOutputStream(FileOutputStream(outputFile), 65536).use { bos ->
            if (!encoder.start(bos)) return 0

            var frameIndex = 0
            var timeUs = startMs * 1000L
            val endUs = (startMs + actualDurationMs) * 1000L

            while (timeUs <= endUs && frameIndex < totalFrames) {
                val rawFrame = getSafeScaledFrame(retriever, timeUs, reqW, reqH)
                if (rawFrame != null) {
                    val opaqueScaled = normalizeToOpaqueSrgbBitmap(rawFrame, rawW, rawH, rotation, targetW, targetH)
                    if (opaqueScaled != rawFrame) rawFrame.recycle()
                    if (encoder.addFrame(opaqueScaled)) {
                        framesAdded++
                    }
                    opaqueScaled.recycle()
                }

                frameIndex++
                timeUs += frameIntervalUs
                onProgress?.invoke(frameIndex.toFloat() / totalFrames)
            }

            if (framesAdded == 0) {
                val fallback = getSafeScaledFrame(retriever, startMs * 1000L, reqW, reqH)
                    ?: retriever.getFrameAtTime()
                if (fallback != null) {
                    val opaqueScaled = normalizeToOpaqueSrgbBitmap(fallback, rawW, rawH, rotation, targetW, targetH)
                    if (opaqueScaled != fallback) fallback.recycle()
                    if (encoder.addFrame(opaqueScaled)) {
                        framesAdded++
                    }
                    opaqueScaled.recycle()
                }
            }

            encoder.finish()
        }
        return framesAdded
    }

    private fun getSafeScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        reqW: Int,
        reqH: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val params = MediaMetadataRetriever.BitmapParams().apply {
                preferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    reqW,
                    reqH,
                    params
                )?.let { return it }
            } catch (_: Exception) {}
            try {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    reqW,
                    reqH,
                    params
                )?.let { return it }
            } catch (_: Exception) {}
        }
        return try {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, reqW, reqH)
                ?: retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqW, reqH)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeToOpaqueSrgbBitmap(
        rawFrame: Bitmap,
        rawW: Int,
        rawH: Int,
        rotation: Int,
        targetW: Int,
        targetH: Int
    ): Bitmap {
        val softFrame = if (rawFrame.config == Bitmap.Config.HARDWARE) {
            rawFrame.copy(Bitmap.Config.ARGB_8888, false) ?: rawFrame
        } else {
            rawFrame
        }

        val isRotated90or270 = rotation == 90 || rotation == 270
        val alreadyRotated = isRotated90or270 && rawW != rawH &&
            ((softFrame.width < softFrame.height) == (rawH < rawW))
        val needsRotation = rotation != 0 && !alreadyRotated

        val orientedFrame = if (needsRotation) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(softFrame, 0, 0, softFrame.width, softFrame.height, matrix, true)
            if (softFrame != rawFrame && rotated != softFrame) softFrame.recycle()
            rotated
        } else {
            softFrame
        }

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(
            orientedFrame,
            Rect(0, 0, orientedFrame.width, orientedFrame.height),
            Rect(0, 0, targetW, targetH),
            paint
        )
        if (orientedFrame != rawFrame) {
            orientedFrame.recycle()
        }
        return out
    }

    private fun calculateScaledEvenDimensions(origW: Int, origH: Int, maxDim: Int): Pair<Int, Int> {
        val safeW = origW.coerceAtLeast(2)
        val safeH = origH.coerceAtLeast(2)
        val scale = if (safeW <= maxDim && safeH <= maxDim) {
            1.0f
        } else if (safeW >= safeH) {
            maxDim.toFloat() / safeW.toFloat()
        } else {
            maxDim.toFloat() / safeH.toFloat()
        }
        val evenW = (((safeW * scale).toInt() + 1) and 1.inv()).coerceIn(2, maxDim)
        val evenH = (((safeH * scale).toInt() + 1) and 1.inv()).coerceIn(2, maxDim)
        return evenW to evenH
    }
}
