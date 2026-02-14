package dhp.thl.tpl.ndv

import android.content.Context
import android.widget.Toast

object ToastUtils {
    private var currentToast: Toast? = null

    fun showToast(context: Context, message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }
}
