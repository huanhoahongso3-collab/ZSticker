package dhp.thl.tpl.ndv

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import java.util.Locale

abstract class BaseActivity : MonetCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("lang", "system") ?: "system"
        val config = Configuration(newBase.resources.configuration)

        if (langCode != "system") {
            val locale = Locale(langCode)
            Locale.setDefault(locale)
            config.setLocale(locale)
        } else {
            val systemLocale = Configuration(newBase.resources.configuration).locales[0]
            val supportedLangs = listOf("en", "vi", "ru", "zh")
            if (supportedLangs.contains(systemLocale.language)) {
                config.setLocale(systemLocale)
            } else {
                // Fallback to English if system language not supported
                val fallbackLocale = Locale("en")
                Locale.setDefault(fallbackLocale)
                config.setLocale(fallbackLocale)
            }
        }

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    protected fun boldTitle(text: String): android.text.SpannableString {
        val ss = android.text.SpannableString(text)
        ss.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, text.length, 0)
        return ss
    }

    protected fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
