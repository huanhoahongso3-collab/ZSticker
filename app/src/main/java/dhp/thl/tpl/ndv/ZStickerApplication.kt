package dhp.thl.tpl.ndv

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kieronquinn.monetcompat.core.MonetCompat

class ZStickerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        MonetCompat.wallpaperColorPicker = { availableColors ->
            val dynamicEnabled = prefs.getBoolean("material_color_enabled", false)
            if (dynamicEnabled) {
                availableColors?.firstOrNull() ?: STOCK_ORANGE_SEED
            } else {
                STOCK_ORANGE_SEED
            }
        }
        val monet = MonetCompat.setup(this)
        monet.defaultPrimaryColor = STOCK_ORANGE_SEED
        monet.updateMonetColors()
    }

    companion object {
        const val STOCK_ORANGE_SEED: Int = 0xFFF57C00.toInt()
    }
}
