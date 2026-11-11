package dhp.thl.tpl.ndv.domain

import android.content.Context
import java.io.File

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

    /** Placeholder until the default GIF host server is deployed. */
    const val DEFAULT_HOST_BASE_URL = "https://zsticker.example"

    fun mode(context: Context): GifSendMode =
        GifSendMode.from(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: GifSendMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.prefValue).apply()
    }

    /** Custom host the user entered, or null when the default server is used. */
    fun customHost(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }

    fun hostBaseUrl(context: Context): String = customHost(context) ?: DEFAULT_HOST_BASE_URL

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
 * Uploads animated GIFs to the GIF host so Zalo can load them by URL.
 * The server is not built yet, so [upload] always reports "unavailable" (null) and callers fall back
 * to the vanilla GIF flow.
 */
object AnimatedGifHost {
    /** Returns the public URL of the uploaded [gif], or null when the host is unavailable. */
    @Suppress("UNUSED_PARAMETER")
    fun upload(context: Context, gif: File): String? {
        // TODO: POST [gif] to GifSendSettings.hostBaseUrl(context) once the server exists.
        return null
    }
}
