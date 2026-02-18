package dhp.thl.tpl.ndv

import android.app.Application

class ZStickerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.kieronquinn.monetcompat.core.MonetCompat.setup(this)
    }
}
