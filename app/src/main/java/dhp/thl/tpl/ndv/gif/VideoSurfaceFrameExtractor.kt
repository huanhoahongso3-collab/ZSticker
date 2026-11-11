package dhp.thl.tpl.ndv.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware-accelerated sequential video frame extractor using MediaCodec + EGL pbuffer +
 * SurfaceTexture (GL_TEXTURE_EXTERNAL_OES).
 *
 * Decoding onto a GPU Surface bypasses MediaMetadataRetriever's CPU YUV ColorConverter,
 * completely eliminating green-stripe artifacts on 4K/8K resolutions, odd buffer strides,
 * vendor-tiled NV12 formats, and 10-bit HEVC/HDR (P010) videos while keeping memory tiny.
 */
object VideoSurfaceFrameExtractor {

    private const val TIMEOUT_US = 15_000L
    private const val FRAME_WAIT_MS = 1_500L

    fun extractFrames(
        context: Context,
        uri: Uri,
        startMs: Long,
        durationMs: Long,
        fps: Int,
        targetW: Int,
        targetH: Int,
        fallbackRotation: Int,
        onFrame: (frameIndex: Int, totalFrames: Int, bitmap: Bitmap) -> Unit
    ): Int {
        val extractor = MediaExtractor()
        var pfd: android.os.ParcelFileDescriptor? = null
        var codec: MediaCodec? = null
        var glOutput: OffscreenGlSurface? = null

        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: uri.toString()
                if (File(path).exists()) {
                    extractor.setDataSource(path)
                } else {
                    extractor.setDataSource(context, uri, null)
                }
            } else {
                pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (_: Exception) {
                    null
                }
                if (pfd != null) {
                    extractor.setDataSource(pfd.fileDescriptor)
                } else {
                    extractor.setDataSource(context, uri, null)
                }
            }

            val videoTrackIndex = selectVideoTrack(extractor)
            if (videoTrackIndex < 0) return 0
            extractor.selectTrack(videoTrackIndex)

            val format = extractor.getTrackFormat(videoTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0

            if (!format.containsKey(MediaFormat.KEY_ROTATION) && fallbackRotation != 0) {
                format.setInteger(MediaFormat.KEY_ROTATION, fallbackRotation)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Request hardware tone-mapping from 10-bit HDR (HLG/PQ) to SDR
                format.setInteger(
                    MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO
                )
            }

            glOutput = OffscreenGlSurface(targetW, targetH)
            glOutput.makeCurrent()

            codec = createAndConfigureDecoder(mime, format, glOutput.surface) ?: return 0
            codec.start()

            val startUs = (startMs.coerceAtLeast(0L)) * 1000L
            val actualDurationUs = (durationMs.coerceAtLeast(100L)) * 1000L
            val endUs = startUs + actualDurationUs
            val frameIntervalMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(40L)
            val frameIntervalUs = frameIntervalMs * 1000L
            val totalFrames = ((durationMs + frameIntervalMs - 1L) / frameIntervalMs)
                .toInt()
                .coerceAtLeast(1)

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var nextTargetUs = startUs
            var framesExtracted = 0
            var postInputIdleSpins = 0
            val toleranceUs = frameIntervalUs / 3L

            val reusableBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

            while (!outputDone && framesExtracted < totalFrames) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                                if (sampleTimeUs > endUs + frameIntervalUs) {
                                    // Feed one EOS after passing endUs so B-frames flush cleanly
                                    val eosIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                                    if (eosIndex >= 0) {
                                        codec.queueInputBuffer(
                                            eosIndex,
                                            0,
                                            0,
                                            0L,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        )
                                        inputDone = true
                                    }
                                }
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        postInputIdleSpins = 0
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val ptsUs = bufferInfo.presentationTimeUs
                        val hasImage = bufferInfo.size > 0

                        val shouldCapture = hasImage && (
                            ptsUs >= nextTargetUs - toleranceUs ||
                                (isEos && framesExtracted == 0)
                            )

                        codec.releaseOutputBuffer(outIndex, shouldCapture)

                        if (shouldCapture) {
                            if (glOutput.awaitNewImage(FRAME_WAIT_MS)) {
                                glOutput.drawImageAndReadPixels(reusableBitmap)
                                onFrame(framesExtracted, totalFrames, reusableBitmap)
                                framesExtracted++
                                nextTargetUs += frameIntervalUs
                                while (nextTargetUs <= ptsUs - toleranceUs && framesExtracted < totalFrames) {
                                    // Low-FPS source: duplicate frame to preserve real-time GIF speed
                                    onFrame(framesExtracted, totalFrames, reusableBitmap)
                                    framesExtracted++
                                    nextTargetUs += frameIntervalUs
                                }
                            }
                        }

                        if (isEos || ptsUs >= endUs) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            postInputIdleSpins++
                            if (postInputIdleSpins > 120) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            reusableBitmap.recycle()
            framesExtracted
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                glOutput?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return -1
    }

    private fun createAndConfigureDecoder(
        mime: String,
        format: MediaFormat,
        surface: Surface
    ): MediaCodec? {
        try {
            val hwCodec = MediaCodec.createDecoderByType(mime)
            try {
                hwCodec.configure(format, surface, null, 0)
                return hwCodec
            } catch (_: Exception) {
                try { hwCodec.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Fallback: try any matching decoder from MediaCodecList (including software c2.android.*)
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            var candidate: MediaCodec? = null
            try {
                candidate = MediaCodec.createByCodecName(info.name)
                candidate.configure(format, surface, null, 0)
                return candidate
            } catch (_: Exception) {
                try { candidate?.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Self-contained EGL14 2.0 pbuffer + SurfaceTexture + OES shader renderer.
     */
    private class OffscreenGlSurface(
        private val width: Int,
        private val height: Int
    ) : SurfaceTexture.OnFrameAvailableListener {

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private val callbackThread = HandlerThread("ZStickerGlFrameCb").apply { start() }
        private val callbackHandler = Handler(callbackThread.looper)

        private val frameSyncLock = Object()
        private var frameAvailable = false

        private var textureId = 0
        private var programId = 0
        private var aPositionLoc = 0
        private var aTexCoordLoc = 0
        private var uMVPMatrixLoc = 0
        private var uSTMatrixLoc = 0

        private val mvpMatrix = FloatArray(16)
        private val stMatrix = FloatArray(16)

        private val pixelByteBuffer: ByteBuffer =
            ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
        private val rgbaBytes = ByteArray(width * height * 4)
        private val argbPixels = IntArray(width * height)

        val surfaceTexture: SurfaceTexture
        val surface: Surface

        private val triangleVertices: FloatBuffer = ByteBuffer
            .allocateDirect(20 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        // X, Y, Z, U, V
                        -1.0f, -1.0f, 0f, 0f, 0f,
                        1.0f, -1.0f, 0f, 1f, 0f,
                        -1.0f, 1.0f, 0f, 0f, 1f,
                        1.0f, 1.0f, 0f, 1f, 1f
                    )
                )
                position(0)
            }

        init {
            setupEgl()
            makeCurrent()
            setupGlProgram()
            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setOnFrameAvailableListener(this, callbackHandler)
            surface = Surface(surfaceTexture)
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.setIdentityM(stMatrix, 0)
        }

        private fun setupEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("Unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("Unable to initialize EGL14")
            }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0
            ) {
                throw RuntimeException("Unable to find RGB888+pbuffer EGLConfig")
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttribs,
                0
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("Failed to create EGLContext")
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("Failed to create EGL pbuffer surface")
            }
        }

        fun makeCurrent() {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("eglMakeCurrent failed")
            }
        }

        private fun setupGlProgram() {
            val vertexShaderSource = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()

            val fragmentShaderSource = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    vec4 c = texture2D(sTexture, vTextureCoord);
                    gl_FragColor = vec4(c.rgb, 1.0);
                }
            """.trimIndent()

            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val info = GLES20.glGetProgramInfoLog(programId)
                GLES20.glDeleteProgram(programId)
                throw RuntimeException("Could not link GL program: $info")
            }

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTextureCoord")
            uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
            uSTMatrixLoc = GLES20.glGetUniformLocation(programId, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
        }

        private fun compileShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader $shaderType: $info")
            }
            return shader
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSyncLock) {
                frameAvailable = true
                frameSyncLock.notifyAll()
            }
        }

        fun awaitNewImage(timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(frameSyncLock) {
                while (!frameAvailable) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) return false
                    try {
                        frameSyncLock.wait(remaining)
                    } catch (_: InterruptedException) {
                        return false
                    }
                }
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(stMatrix)
            return true
        }

        fun drawImageAndReadPixels(outBitmap: Bitmap) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aPositionLoc)

            triangleVertices.position(3)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)

            GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()

            pixelByteBuffer.rewind()
            GLES20.glReadPixels(
                0,
                0,
                width,
                height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixelByteBuffer
            )
            pixelByteBuffer.rewind()
            pixelByteBuffer.get(rgbaBytes)

            // Flip OpenGL bottom-up rows into Android Bitmap top-down opaque ARGB_8888
            val rowStride = width * 4
            for (y in 0 until height) {
                var srcIdx = (height - 1 - y) * rowStride
                var dstIdx = y * width
                for (x in 0 until width) {
                    val r = rgbaBytes[srcIdx].toInt() and 0xFF
                    val g = rgbaBytes[srcIdx + 1].toInt() and 0xFF
                    val b = rgbaBytes[srcIdx + 2].toInt() and 0xFF
                    argbPixels[dstIdx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    srcIdx += 4
                }
            }
            outBitmap.setPixels(argbPixels, 0, width, 0, 0, width, height)
        }

        fun release() {
            try {
                surface.release()
            } catch (_: Exception) {}
            try {
                surfaceTexture.release()
            } catch (_: Exception) {}
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (_: Exception) {}
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            try {
                callbackThread.quitSafely()
            } catch (_: Exception) {}
        }
    }
}
