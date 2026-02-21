package dhp.thl.tpl.ndv

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {

    fun exportBackup(context: Context, type: String): String? {
        try {
            val time = System.currentTimeMillis()
            val fileName = "${type}backup_$time.zip"
            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!outDir.exists()) outDir.mkdirs()
            
            val zipFile = File(outDir, fileName)
            val zos = ZipOutputStream(FileOutputStream(zipFile))

            if (type == "image" || type == "all") {
                val stickerFiles = context.filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
                for (file in stickerFiles) {
                    addToZipFile(file, "images/${file.name}", zos)
                }
            }

            if (type == "history" || type == "all") {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/recents.xml")
                if (prefsFile.exists()) {
                    addToZipFile(prefsFile, "prefs/recents.xml", zos)
                }
            }

            if (type == "settings" || type == "all") {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/settings.xml")
                if (prefsFile.exists()) {
                    addToZipFile(prefsFile, "prefs/settings.xml", zos)
                }
            }

            zos.close()
            return fileName
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun addToZipFile(file: File, relativePath: String, zos: ZipOutputStream) {
        val fis = FileInputStream(file)
        val zipEntry = ZipEntry(relativePath)
        zos.putNextEntry(zipEntry)
        val bytes = ByteArray(1024)
        var length: Int
        while (fis.read(bytes).also { length = it } >= 0) {
            zos.write(bytes, 0, length)
        }
        zos.closeEntry()
        fis.close()
    }

    fun importBackup(context: Context, uri: Uri): Boolean {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return false
            val zis = ZipInputStream(inputStream)

            var entry = zis.nextEntry
            while (entry != null) {
                val fileName = entry.name
                if (fileName.startsWith("images/")) {
                    val name = fileName.substringAfter("images/")
                    val file = File(context.filesDir, name)
                    val fos = FileOutputStream(file)
                    val bytes = ByteArray(1024)
                    var length: Int
                    while (zis.read(bytes).also { length = it } >= 0) {
                        fos.write(bytes, 0, length)
                    }
                    fos.close()
                } else if (fileName.startsWith("prefs/")) {
                    val name = fileName.substringAfter("prefs/")
                    val file = File(context.applicationInfo.dataDir, "shared_prefs/$name")
                    if (!file.parentFile.exists()) file.parentFile.mkdirs()
                    val fos = FileOutputStream(file)
                    val bytes = ByteArray(1024)
                    var length: Int
                    while (zis.read(bytes).also { length = it } >= 0) {
                        fos.write(bytes, 0, length)
                    }
                    fos.close()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
