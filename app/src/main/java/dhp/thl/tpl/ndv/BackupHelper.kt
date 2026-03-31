package dhp.thl.tpl.ndv

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupHelper {

    private const val PASSWORD = "zalonotfound"

    fun exportBackup(context: Context, outStream: OutputStream, type: String, onProgress: ((Float) -> Unit)? = null): Boolean {
        val tempFile = File(context.cacheDir, "export_temp.zip")
        if (tempFile.exists()) tempFile.delete()

        try {
            val zipFile = ZipFile(tempFile, PASSWORD.toCharArray())
            val params = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }

            val metadata = JSONObject()
            metadata.put("version", 2)
            metadata.put("type", type)
            metadata.put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            
            val contents = mutableListOf<String>()

            if (type == "image" || type == "all") {
                val stickerFiles = context.filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
                val timestamps = JSONObject()
                val totalFiles = stickerFiles.size
                var current = 0
                for (file in stickerFiles) {
                    val p = ZipParameters(params)
                    p.fileNameInZip = "images/${file.name}"
                    zipFile.addFile(file, p)
                    timestamps.put(file.name, file.lastModified())
                    current++
                    onProgress?.invoke(current.toFloat() / totalFiles * 0.9f) // Leave room for result copy
                }
                metadata.put("timestamps", timestamps)
                contents.add("images")
            }

            if (type == "history" || type == "all") {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/recents.xml")
                if (prefsFile.exists()) {
                    val p = ZipParameters(params)
                    p.fileNameInZip = "prefs/recents.xml"
                    zipFile.addFile(prefsFile, p)
                    contents.add("history")
                }
            }

            if (type == "settings" || type == "all") {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/settings.xml")
                if (prefsFile.exists()) {
                    val p = ZipParameters(params)
                    p.fileNameInZip = "prefs/settings.xml"
                    zipFile.addFile(prefsFile, p)
                    contents.add("settings")
                }
            }

            metadata.put("contents", JSONObject().apply { 
                contents.forEach { put(it, true) }
            })

            // Add metadata.json
            val metaFile = File(context.cacheDir, "metadata.json")
            metaFile.writeText(metadata.toString(4))
            val metaParams = ZipParameters(params)
            metaParams.fileNameInZip = "metadata.json"
            zipFile.addFile(metaFile, metaParams)

            // Copy static result to output stream
            tempFile.inputStream().use { it.copyTo(outStream) }
            onProgress?.invoke(1.0f)
            outStream.flush()
            
            tempFile.delete()
            metaFile.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }

    fun importBackup(context: Context, uri: Uri, onProgress: ((Float) -> Unit)? = null): Boolean {
        val tempFile = File(context.cacheDir, "import_temp.zip")
        if (tempFile.exists()) tempFile.delete()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            val zipFile = ZipFile(tempFile)
            
            // Check if encrypted
            if (zipFile.isEncrypted) {
                zipFile.setPassword(PASSWORD.toCharArray())
            }

            // Extract to temp folder first to detect content
            val extractDir = File(context.cacheDir, "extract_temp")
            if (extractDir.exists()) extractDir.deleteRecursively()
            extractDir.mkdirs()
            
            zipFile.isRunInThread = true
            zipFile.extractAll(extractDir.absolutePath)
            
            while (!zipFile.progressMonitor.state.equals(net.lingala.zip4j.progress.ProgressMonitor.State.READY)) {
                onProgress?.invoke(zipFile.progressMonitor.percentDone.toFloat() / 100f * 0.7f)
                Thread.sleep(100)
            }
            
            // Validation: Check if it's a valid ZSticker backup
            val metaFile = File(extractDir, "metadata.json")
            if (!metaFile.exists()) {
                extractDir.deleteRecursively()
                tempFile.delete()
                return false
            }

            // Auto-discovery
            val imagesDir = File(extractDir, "images")
            val prefsDir = File(extractDir, "prefs")

            // Restore images
            if (imagesDir.exists() && imagesDir.isDirectory) {
                val metadataJson = JSONObject(metaFile.readText())
                val timestamps = metadataJson.optJSONObject("timestamps")
                
                val total = imagesDir.listFiles()?.size ?: 1
                var processed = 0
                imagesDir.listFiles()?.forEach { file ->
                    val targetFile = File(context.filesDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                    
                    // Restore original timestamp for chronological sorting
                    timestamps?.optLong(file.name, 0L)?.let { timestamp ->
                        if (timestamp > 0) targetFile.setLastModified(timestamp)
                    }
                    processed++
                    onProgress?.invoke(0.7f + (processed.toFloat() / total * 0.3f))
                }
            }

            // Restore prefs
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    val target = File(context.applicationInfo.dataDir, "shared_prefs/${file.name}")
                    if (!target.parentFile.exists()) target.parentFile.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }

            extractDir.deleteRecursively()
            tempFile.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }

    fun exportAllStickersToGallery(context: Context, onProgress: ((Float) -> Unit)? = null): Int {
        val stickerFiles = context.filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
        if (stickerFiles.isEmpty()) return 0
        
        var count = 0
        val outDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            null // Handle via MediaStore below
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!dir.exists()) dir.mkdirs()
            dir
        }

        val total = stickerFiles.size
        for ((index, file) in stickerFiles.withIndex()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "sticker_${file.name}")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZSticker")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        count++
                    }
                } else {
                    val outFile = File(outDir, "sticker_${file.name}")
                    file.inputStream().use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    count++
                }
                onProgress?.invoke((index + 1).toFloat() / total)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return count
    }

    fun exportSingleStickerToGallery(context: Context, file: File): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "sticker_${file.name}")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZSticker")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return true
                }
            } else {
                val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, "sticker_${file.name}")
                file.inputStream().use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
