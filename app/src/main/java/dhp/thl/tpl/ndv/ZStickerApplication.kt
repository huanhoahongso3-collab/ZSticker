package dhp.thl.tpl.ndv

import android.app.Application

class ZStickerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(savedTheme)
        com.kieronquinn.monetcompat.core.MonetCompat.setup(this)
    }
}
