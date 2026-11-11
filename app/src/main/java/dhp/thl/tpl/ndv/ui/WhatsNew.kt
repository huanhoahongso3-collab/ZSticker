package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BulletSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager

/**
 * "What's new in this version" dialog. It lists what changed since the previous release (the
 * `changelog_6_7` string array; update it, and the title, for the next release) and is shown once after
 * an update. "Don't show again" turns the automatic dialog off for good; Options > What's new can always
 * open it manually.
 */
object WhatsNew {
    private const val PREFS = "settings"
    private const val KEY_LAST_SEEN = "whats_new_last_seen"
    private const val KEY_DONT_SHOW = "whats_new_dont_show"

    /** Shows the dialog when this version has not been seen yet and the user has not opted out. */
    fun showIfNeeded(activity: BaseActivity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONT_SHOW, false)) return
        if (prefs.getLong(KEY_LAST_SEEN, 0L) >= versionCode(activity)) return
        show(activity)
    }

    /** Shows the dialog regardless of the saved state (used by the Options entry). */
    fun show(activity: BaseActivity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val palette = ExpressiveThemeManager.resolvePalette(activity)
        val density = activity.resources.displayMetrics.density

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_whats_new, null)
        val dialog = MaterialAlertDialogBuilder(activity).setView(view).create()

        val icon = view.findViewById<ImageView>(R.id.icon_whats_new)
        val title = view.findViewById<TextView>(R.id.dialog_title)
        val container = view.findViewById<LinearLayout>(R.id.changelog_container)
        val checkBox = view.findViewById<MaterialCheckBox>(R.id.cb_dont_show_again)
        val button = view.findViewById<MaterialButton>(R.id.btn_got_it)

        icon.setColorFilter(palette.primary)
        title.text = activity.getString(R.string.whats_new_title, versionName(activity))
        title.setTextColor(palette.onSurface)

        activity.resources.getStringArray(R.array.changelog_6_7).forEach { item ->
            val bullet = SpannableString(item).apply {
                setSpan(
                    BulletSpan((12 * density).toInt(), palette.primary, (3 * density).toInt()),
                    0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
            }
            container.addView(TextView(activity).apply {
                text = bullet
                setTextColor(palette.onSurfaceVariant)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * density).toInt() }
            })
        }

        checkBox.isChecked = prefs.getBoolean(KEY_DONT_SHOW, false)
        checkBox.buttonTintList = ColorStateList.valueOf(palette.primary)
        checkBox.setTextColor(palette.onSurface)
        button.setTextColor(palette.onSecondaryContainer)
        button.backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
        button.setOnClickListener { dialog.dismiss() }

        // However the dialog closes (button, back, tap outside), this version counts as seen.
        dialog.setOnDismissListener {
            prefs.edit()
                .putLong(KEY_LAST_SEEN, versionCode(activity))
                .putBoolean(KEY_DONT_SHOW, checkBox.isChecked)
                .apply()
        }
        dialog.showMonetDialog(activity)
    }

    private fun versionCode(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (_: Exception) {
        0L
    }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }
}
