package dhp.thl.tpl.ndv.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.gowtham.library.utils.TrimType
import com.gowtham.library.utils.TrimVideo
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.ProgressDialog
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.gif.AnimatedImageConverter
import dhp.thl.tpl.ndv.gif.VideoToGifConverter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * Handles media importing, share/view intents, video container detection,
 * video trimmer launching, and video-to-GIF sticker conversion.
 */
class MediaImportManager(
    private val activity: BaseActivity,
    private val adapterProvider: () -> StickerAdapter,
    private val onUpdateEmptyState: (isEmpty: Boolean, message: String) -> Unit,
    private val videoTrimLauncherProvider: () -> ActivityResultLauncher<Intent>
) {
    var currentTempVideoFile: File? = null

    fun restoreSavedState(savedPath: String?) {
        savedPath?.let { path ->
            val f = File(path)
            if (f.exists()) currentTempVideoFile = f
        }
    }

    fun checkPendingShareIntent(intent: Intent?) {
        val currentIntent = intent ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (currentIntent.getBooleanExtra("zsticker_share_handled", false)) return
        val action = currentIntent.action
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE || action == Intent.ACTION_VIEW) {
            currentIntent.putExtra("zsticker_share_handled", true)
            handleIncomingShare(currentIntent)
        }
    }

    fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                if (uris.isEmpty()) {
                    intent.data?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
                if (uris.isEmpty()) {
                    intent.getStringExtra(Intent.EXTRA_STREAM)?.let { str ->
                        if (str.isNotBlank()) uris.add(Uri.parse(str))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
                if (uris.isEmpty()) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
            }
        }

        if (uris.isNotEmpty()) {
            handleMediaUris(uris.toList(), intent.type)
        }
    }

    fun isVideoUri(uri: Uri, hintMimeType: String? = null): Boolean {
        val mimeType = try { activity.contentResolver.getType(uri) ?: "" } catch (_: Exception) { "" }
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        if (hintMimeType != null && hintMimeType.startsWith("video/", ignoreCase = true)) return true
        val uriStr = Uri.decode(uri.toString()).lowercase()
        val path = (uri.path ?: uriStr).lowercase()
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
            path.endsWith(".3gp") || path.endsWith(".mov") || path.endsWith(".avi") || path.endsWith(".m4v") ||
            uriStr.contains("/video/") || uriStr.contains("video:")
        ) return true

        // Check container magic bytes (MP4/MOV/3GP ftyp, WebM/MKV EBML, AVI RIFF)
        try {
            openUriInputStream(uri)?.use { stream ->
                val header = ByteArray(16)
                val read = stream.read(header)
                if (read >= 12) {
                    val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
                    if (boxType == "ftyp" || boxType == "moov" || boxType == "wide" || boxType == "mdat") {
                        return true
                    }
                    if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
                    ) {
                        return true
                    }
                    val riff = String(header, 0, 4, Charsets.ISO_8859_1)
                    val avi = String(header, 8, 4, Charsets.ISO_8859_1)
                    if (riff == "RIFF" && avi == "AVI ") {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                retriever.setDataSource(uri.path ?: uri.toString())
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
            } else {
                val pfd = activity.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use {
                        retriever.setDataSource(it.fileDescriptor)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
                    }
                } else {
                    retriever.setDataSource(activity, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun openUriInputStream(srcUri: Uri): InputStream? {
        try {
            if (srcUri.scheme == "file" || srcUri.scheme == null) {
                val file = File(srcUri.path ?: srcUri.toString())
                if (file.exists()) return file.inputStream()
            }
        } catch (_: Exception) {}
        try {
            activity.contentResolver.openInputStream(srcUri)?.let { return it }
        } catch (_: Exception) {}
        try {
            activity.contentResolver.openAssetFileDescriptor(srcUri, "r")?.createInputStream()?.let { return it }
        } catch (_: Exception) {}
        return null
    }

    private fun prepareVideoFile(srcUri: Uri): File? {
        return try {
            val validVideoExts = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
            val rawExt = try {
                val mime = activity.contentResolver.getType(srcUri)
                if (mime != null) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase() else null
            } catch (_: Exception) { null }
            val ext = if (rawExt != null && rawExt in validVideoExts) rawExt else "mp4"

            val tempFile = File(activity.cacheDir, "trim_input_${System.currentTimeMillis()}.$ext")
            openUriInputStream(srcUri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun handleMediaUris(uris: List<Uri>, intentMimeType: String? = null) {
        if (uris.isEmpty()) return

        thread {
            val videoMask = uris.map { uri ->
                isVideoUri(uri, if (uris.size == 1) intentMimeType else null)
            }
            val videoCount = videoMask.count { it }

            if (videoCount > 0 && uris.size > 1) {
                activity.runOnUiThread {
                    ToastUtils.showToast(activity, activity.getString(R.string.only_one_video_allowed))
                }
                return@thread
            }

            if (videoCount == 1) {
                val videoUri = uris.first()
                val progressDialog = ProgressDialog.newInstance(activity.getString(R.string.converting_video))
                activity.runOnUiThread {
                    progressDialog.showSafe(activity.supportFragmentManager, "prepare_video")
                }
                val tempVideoFile = prepareVideoFile(videoUri)
                activity.runOnUiThread {
                    progressDialog.dismissSafe()
                    if (tempVideoFile != null) {
                        launchVideoTrimmer(tempVideoFile)
                    } else {
                        ToastUtils.showToast(activity, activity.getString(R.string.failed))
                    }
                }
                return@thread
            }

            val progressDialog = ProgressDialog.newInstance(activity.getString(R.string.importing_sticker))
            activity.runOnUiThread {
                progressDialog.showSafe(activity.supportFragmentManager, "import_media")
            }

            var hasSuccess = false
            var hasUnsupported = false
            var hasFailed = false

            for ((index, uri) in uris.withIndex()) {
                val mimeType = try {
                    activity.contentResolver.getType(uri) ?: intentMimeType ?: ""
                } catch (_: Exception) { "" }
                val animType = AnimatedImageConverter.detectAnimationType(activity, uri)
                if (animType != AnimatedImageConverter.AnimationType.NONE) {
                    activity.runOnUiThread {
                        progressDialog.updateMessage(activity.getString(R.string.converting_animated_sticker))
                    }
                    val destFile = File(activity.filesDir, "zsticker_${System.currentTimeMillis()}.gif")
                    val success = AnimatedImageConverter.convertToZaloGif(activity, uri, destFile) { p ->
                        activity.runOnUiThread { progressDialog.updateProgress((index + p) / uris.size) }
                    }
                    if (success) hasSuccess = true else hasFailed = true
                } else if (
                    mimeType.startsWith("image/") ||
                    mimeType.isEmpty() ||
                    mimeType == "*/*" ||
                    mimeType == "application/octet-stream"
                ) {
                    activity.runOnUiThread {
                        progressDialog.updateMessage(activity.getString(R.string.importing_sticker))
                    }
                    try {
                        openUriInputStream(uri)?.use { input ->
                            val file = File(activity.filesDir, "zsticker_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out ->
                                if (input.copyTo(out) > 0) hasSuccess = true else {
                                    file.delete()
                                    hasFailed = true
                                }
                            }
                        } ?: run { hasFailed = true }
                    } catch (_: Exception) {
                        hasFailed = true
                    }
                } else {
                    hasUnsupported = true
                }
                activity.runOnUiThread { progressDialog.updateProgress((index + 1).toFloat() / uris.size) }
            }

            activity.runOnUiThread {
                progressDialog.dismissSafe()
                if (hasSuccess) {
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    ToastUtils.showToast(activity, activity.getString(R.string.success))
                }
                if (hasUnsupported) ToastUtils.showToast(activity, activity.getString(R.string.unsupported_file_type))
                else if (hasFailed) ToastUtils.showToast(activity, activity.getString(R.string.failed))
            }
        }
    }

    private fun launchVideoTrimmer(videoFile: File) {
        try {
            currentTempVideoFile = videoFile
            val lang = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("lang", "system") ?: "system"
            val builder = TrimVideo.activity(Uri.fromFile(videoFile).toString())
                .setTrimType(TrimType.FIXED_DURATION)
                .setFixedDuration(5L)
                .setTitle(activity.getString(R.string.trim_video_title))
                .disableCompression()
                .setHideSeekBar(false)
            if (lang != "system") {
                builder.setLocal(lang)
            }
            builder.start(activity, videoTrimLauncherProvider())
        } catch (e: Exception) {
            e.printStackTrace()
            videoFile.delete()
            currentTempVideoFile = null
            ToastUtils.showToast(activity, activity.getString(R.string.failed))
        }
    }

    fun handleVideoTrimResult(result: ActivityResult) {
        val inputTemp = currentTempVideoFile
        currentTempVideoFile = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val trimmedPath = TrimVideo.getTrimmedVideoPath(data)
            val startMs = TrimVideo.getTrimStartMs(data).coerceAtLeast(0L)
            val endMs = TrimVideo.getTrimEndMs(data)
            val clipDurationMs = if (endMs > startMs) {
                (endMs - startMs).coerceIn(100L, VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS)
            } else {
                VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS
            }

            if (!trimmedPath.isNullOrEmpty()) {
                val trimmedFile = File(trimmedPath)
                val trimmedUri = if (trimmedFile.exists()) {
                    Uri.fromFile(trimmedFile)
                } else if (inputTemp != null && inputTemp.exists()) {
                    Uri.fromFile(inputTemp)
                } else if (trimmedPath.startsWith("/")) {
                    Uri.fromFile(File(trimmedPath))
                } else {
                    Uri.parse(trimmedPath)
                }
                processVideoFile(
                    videoUri = trimmedUri,
                    startMs = startMs,
                    maxDurationMs = clipDurationMs,
                    isTempFile = true,
                    extraCleanupFile = inputTemp
                )
            } else if (inputTemp != null && inputTemp.exists()) {
                processVideoFile(
                    videoUri = Uri.fromFile(inputTemp),
                    startMs = startMs,
                    maxDurationMs = clipDurationMs,
                    isTempFile = true
                )
            } else {
                inputTemp?.delete()
                ToastUtils.showToast(activity, activity.getString(R.string.failed))
            }
        } else {
            inputTemp?.delete()
        }
    }

    private fun processVideoFile(
        videoUri: Uri,
        startMs: Long = 0L,
        maxDurationMs: Long = VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS,
        isTempFile: Boolean = false,
        extraCleanupFile: File? = null
    ) {
        val progressDialog = ProgressDialog.newInstance(activity.getString(R.string.converting_video))
        progressDialog.showSafe(activity.supportFragmentManager, "convert_video")

        thread {
            val destFile = File(activity.filesDir, "zsticker_${System.currentTimeMillis()}.gif")
            val success = VideoToGifConverter.convertVideoToGif(
                activity,
                videoUri,
                destFile,
                startMs = startMs,
                maxDurationMs = maxDurationMs,
                fps = 10
            ) { progress ->
                activity.runOnUiThread { progressDialog.updateProgress(progress) }
            }
            if (isTempFile) {
                try {
                    videoUri.path?.let { File(it).delete() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                extraCleanupFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activity.runOnUiThread {
                progressDialog.dismissSafe()
                if (success) {
                    val adapter = adapterProvider()
                    adapter.refreshData(activity)
                    onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                    ToastUtils.showToast(activity, activity.getString(R.string.success))
                } else {
                    ToastUtils.showToast(activity, activity.getString(R.string.failed))
                }
            }
        }
    }
}
