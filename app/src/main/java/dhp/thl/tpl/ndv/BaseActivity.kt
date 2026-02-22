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
            val systemLocale = newBase.resources.configuration.locales[0]
            val lang = systemLocale.language
            val supportedLangs = listOf("en", "vi", "ru", "zh")
            
            // Check if any supported language is a prefix of the system language
            val matchedLang = supportedLangs.find { lang.startsWith(it) }
            if (matchedLang != null) {
                config.setLocale(Locale(matchedLang))
            } else {
                config.setLocale(Locale("en"))
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
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finishAffinity()
    }
}
