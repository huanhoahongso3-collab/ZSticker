package dhp.thl.tpl.ndv.domain

import android.content.Context
import dhp.thl.tpl.ndv.BuildConfig
import dhp.thl.tpl.ndv.gif.GifProcessor
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Uploads a compressed copy of a GIF to the configured GIF host and returns its public URL.
 *
 * Works with the official server and with any third-party / self-hosted server that implements the same
 * small contract: `POST <host>/api/upload` with the raw GIF bytes, answering 200 with JSON `{ "url": "https://..." }`.
 * Requests to the default host are signed with the build-time key; a custom host gets plain requests only
 * (no key, signature or install id), so it needs no app-specific protection and never sees our credentials.
 */
object GifHostClient {

    /** The server rejects bodies above 4 MB; stay a little below. */
    private const val MAX_UPLOAD_BYTES = 3_800_000L

    enum class Reason { NOT_CONFIGURED, COMPRESS, NETWORK, RATE_LIMITED, REJECTED, SERVER, BAD_RESPONSE }

    sealed class Result {
        data class Success(val url: String, val cached: Boolean) : Result()
        data class Failure(val reason: Reason, val detail: String? = null) : Result()
    }

    /** True when [gif] was uploaded before and its link is still fresh, so no notice or upload is needed. */
    fun isCached(context: Context, gif: File): Boolean {
        val host = GifSendSettings.hostBaseUrl(context)
        return host.isNotBlank() && StickerLinkTable.find(context, StickerLinkTable.sha256(gif), host) != null
    }

    /** Blocking; call from a background thread. */
    fun upload(context: Context, gif: File, onProgress: ((Float) -> Unit)? = null): Result {
        val host = GifSendSettings.hostBaseUrl(context).trim().trimEnd('/')
        if (host.isBlank() || !GifSendSettings.isValidHost(host)) return Result.Failure(Reason.NOT_CONFIGURED)

        val hash = StickerLinkTable.sha256(gif)
        StickerLinkTable.find(context, hash, host)?.let { return Result.Success(it.url, cached = true) }

        val compressed = File(context.cacheDir, "gif_upload_$hash.gif")
        try {
            if (!GifProcessor.compressForUpload(gif, compressed, MAX_UPLOAD_BYTES, onProgress)) {
                return Result.Failure(Reason.COMPRESS)
            }
            val body = compressed.readBytes()
            val result = post(context, host, body)
            if (result is Result.Success) StickerLinkTable.put(context, hash, host, result.url)
            return result
        } finally {
            compressed.delete()
        }
    }

    private fun post(context: Context, host: String, body: ByteArray): Result {
        val connection = try {
            URL("$host/api/upload").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Failure(Reason.NOT_CONFIGURED, e.message)
        }
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "image/gif")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ZSticker/${BuildConfig.VERSION_NAME} (Android)")

            if (GifSendSettings.shouldSignRequests(context)) {
                val install = installId(context)
                val timestamp = System.currentTimeMillis().toString()
                connection.setRequestProperty("X-ZS-Key-Id", GifApiCredentials.keyId)
                connection.setRequestProperty("X-ZS-Install", install)
                connection.setRequestProperty("X-ZS-Timestamp", timestamp)
                connection.setRequestProperty("X-ZS-Signature", GifApiCredentials.sign(timestamp, install, sha256Hex(body)))
            }

            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            if (code == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val url = try {
                    JSONObject(text).optString("url")
                } catch (_: Exception) {
                    ""
                }
                if (url.startsWith("https://")) Result.Success(url, cached = false)
                else Result.Failure(Reason.BAD_RESPONSE, "no https url in response")
            } else {
                val detail = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
                } catch (_: Exception) {
                    null
                }
                when {
                    code == 429 -> Result.Failure(Reason.RATE_LIMITED, detail)
                    code in 400..499 -> Result.Failure(Reason.REJECTED, "HTTP $code ${detail ?: ""}".trim())
                    else -> Result.Failure(Reason.SERVER, "HTTP $code ${detail ?: ""}".trim())
                }
            }
        } catch (e: Exception) {
            Result.Failure(Reason.NETWORK, e.message)
        } finally {
            connection.disconnect()
        }
    }

    /** Random per-install id, only ever sent to the official (signed) host. */
    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getString("gif_install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("gif_install_id", it).apply()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
