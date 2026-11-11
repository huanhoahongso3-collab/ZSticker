package dhp.thl.tpl.ndv.domain

import android.content.Context
import dhp.thl.tpl.ndv.BuildConfig
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * How a GIF sticker is handed to Zalo.
 * - [VANILLA]: the GIF is shared as-is; Zalo shows a regular animated GIF.
 * - [ANIMATED]: the GIF is shared as a sticker whose animation is loaded from a GIF host.
 */
enum class GifSendMode(val prefValue: String) {
    VANILLA("vanilla"),
    ANIMATED("animated");

    companion object {
        fun from(value: String?): GifSendMode = values().firstOrNull { it.prefValue == value } ?: VANILLA
    }
}

object GifSendSettings {
    private const val PREFS = "settings"
    private const val KEY_MODE = "gif_send_mode"
    private const val KEY_HOST = "gif_host_base_url"

    /** Intent extra read by the Zalo module to attach the animation to a photo sticker. */
    const val EXTRA_ANIMATED_URL = "zsticker_animated_url"

    /** Default GIF host, injected at build time from CI (ZS_GIF_HOST_URL); empty when not configured. */
    val DEFAULT_HOST_BASE_URL: String get() = BuildConfig.GIF_HOST_URL

    fun mode(context: Context): GifSendMode =
        GifSendMode.from(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: GifSendMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.prefValue).apply()
    }

    /** Custom host the user entered, or null when the default server is used. */
    fun customHost(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }

    fun hostBaseUrl(context: Context): String = customHost(context) ?: DEFAULT_HOST_BASE_URL

    /**
     * The official API key is only ever used with the default host. A custom (self-hosted) host gets
     * unsigned requests, so the key is never handed to a third-party server.
     */
    fun shouldSignRequests(context: Context): Boolean =
        customHost(context) == null && GifApiCredentials.isConfigured

    /** True when the default host exists in this build or the user has set a custom host. */
    fun hasHost(context: Context): Boolean = hostBaseUrl(context).isNotBlank()

    /** Saves [url] as the custom host; null or blank restores the default server. */
    fun setCustomHost(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (url.isNullOrBlank()) prefs.remove(KEY_HOST) else prefs.putString(KEY_HOST, url.trim().trimEnd('/'))
        prefs.apply()
    }

    fun isValidHost(url: String): Boolean =
        url.trim().let { it.startsWith("https://") && it.length > "https://".length && !it.contains(' ') }
}

/**
 * API credentials for the GIF host, injected at build time from CI secrets
 * (ZS_GIF_API_KEY_ID / ZS_GIF_API_SECRET). They are never stored in the repository.
 *
 * Note: anything shipped inside an APK can be extracted, so this deters casual abuse only; the server
 * relies on rate limits and expiry for real protection (see ZSticker-gif-proxy docs/SECURITY.md).
 */
object GifApiCredentials {
    val keyId: String get() = BuildConfig.GIF_API_KEY_ID
    private val secret: String get() = BuildConfig.GIF_API_SECRET

    val isConfigured: Boolean get() = keyId.isNotEmpty() && secret.isNotEmpty()

    /** hex(HMAC-SHA256(secret, "<timestamp>.<install>.<sha256(body)>")), as the server expects. */
    fun sign(timestamp: String, install: String, bodySha256Hex: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$timestamp.$install.$bodySha256Hex".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
