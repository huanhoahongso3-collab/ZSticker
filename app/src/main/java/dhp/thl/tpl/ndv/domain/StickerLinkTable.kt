package dhp.thl.tpl.ndv.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Local table of GIFs that were already uploaded to a GIF host: original-file hash + host -> hosted URL.
 * Looking a GIF up here avoids uploading it again. The table is saved in the app's files directory,
 * exported and imported together with backups.
 */
object StickerLinkTable {
    private const val FILE_NAME = "sticker_links.json"

    /** Links older than this are re-uploaded (servers delete files after their retention period). */
    private const val MAX_AGE_MS = 80L * 24 * 60 * 60 * 1000

    data class Entry(val hash: String, val host: String, val url: String, val uploadedAt: Long)

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun find(context: Context, hash: String, host: String, now: Long = System.currentTimeMillis()): Entry? =
        read(context).firstOrNull {
            it.hash == hash && it.host == normalizeHost(host) && now - it.uploadedAt < MAX_AGE_MS
        }

    @Synchronized
    fun put(context: Context, hash: String, host: String, url: String, now: Long = System.currentTimeMillis()) {
        val entries = read(context).filterNot { it.hash == hash && it.host == normalizeHost(host) }.toMutableList()
        entries.add(Entry(hash, normalizeHost(host), url, now))
        write(context, entries)
    }

    @Synchronized
    fun exportJson(context: Context): String = toJson(read(context)).toString()

    /** Merges [json] into the table (newest entry per hash+host wins); returns how many entries were read. */
    @Synchronized
    fun importJson(context: Context, json: String): Int {
        val incoming = parse(json)
        val merged = LinkedHashMap<String, Entry>()
        for (e in read(context) + incoming) {
            val key = e.hash + "@" + e.host
            val existing = merged[key]
            if (existing == null || e.uploadedAt > existing.uploadedAt) merged[key] = e
        }
        write(context, merged.values.toList())
        return incoming.size
    }

    private fun normalizeHost(host: String) = host.trim().trimEnd('/')

    private fun read(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            parse(f.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<Entry>) {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(toJson(entries).toString())
        if (!tmp.renameTo(file(context))) {
            file(context).writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun toJson(entries: List<Entry>): JSONArray = JSONArray().also { array ->
        entries.forEach {
            array.put(
                JSONObject()
                    .put("hash", it.hash)
                    .put("host", it.host)
                    .put("url", it.url)
                    .put("uploadedAt", it.uploadedAt)
            )
        }
    }

    private fun parse(json: String): List<Entry> {
        val array = JSONArray(json)
        val out = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val hash = o.optString("hash")
            val host = o.optString("host")
            val url = o.optString("url")
            // Only well-formed https links are ever trusted from an imported file.
            if (hash.length == 64 && host.isNotBlank() && url.startsWith("https://")) {
                out.add(Entry(hash, normalizeHost(host), url, o.optLong("uploadedAt", 0L)))
            }
        }
        return out
    }
}
