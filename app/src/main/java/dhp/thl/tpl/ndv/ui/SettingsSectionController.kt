package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.FileActivity
import dhp.thl.tpl.ndv.LicenseActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import java.io.File

/**
 * Manages the Options / Settings & About section of MainActivity, including:
 * - Theme & Language selection dialogs
 * - Material Color & Online Background Removal switches
 * - Import/Export navigation, clear recents, and delete all stickers
 * - Version spam -> localized "Thank you for using this app!" toast
 */
class SettingsSectionController(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding,
    private val adapterProvider: () -> StickerAdapter,
    private val recentsAdapterProvider: () -> StickerAdapter,
    private val onUpdateEmptyState: (isEmpty: Boolean, message: String) -> Unit,
    private val onSyncRecentsAfterDeletion: () -> Unit,
    private val onLaunchFileActivity: (Intent) -> Unit
) {
    private var versionTapCount = 0
    private var lastVersionTapTimeMs = 0L

    fun setup() {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

        updateThemeText(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        binding.itemTheme.setOnClickListener {
            val themes = listOf(
                OptionItem(R.drawable.ic_theme_light, activity.getString(R.string.theme_light)),
                OptionItem(R.drawable.ic_theme_dark, activity.getString(R.string.theme_dark)),
                OptionItem(R.drawable.ic_settings_system, activity.getString(R.string.theme_system))
            )

            val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val selectedIndex = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            activity.showPaneDialog(
                activity.getString(R.string.info_theme_title),
                themes,
                selectedIndex
            ) { which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                applyAndSaveTheme(prefs, newMode)
            }
        }

        val currentLang = prefs.getString("lang", "system") ?: "system"
        binding.txtCurrentLanguage.text = when (currentLang) {
            "en" -> activity.getString(R.string.lang_en)
            "vi" -> activity.getString(R.string.lang_vi)
            "ru" -> activity.getString(R.string.lang_ru)
            "zh" -> activity.getString(R.string.lang_zh)
            else -> activity.getString(R.string.lang_system)
        }

        binding.itemLanguage.setOnClickListener {
            val langs = listOf(
                OptionItem(R.drawable.ic_flag_en, activity.getString(R.string.lang_en)),
                OptionItem(R.drawable.ic_flag_vi, activity.getString(R.string.lang_vi)),
                OptionItem(R.drawable.ic_flag_ru, activity.getString(R.string.lang_ru)),
                OptionItem(R.drawable.ic_flag_zh, activity.getString(R.string.lang_zh)),
                OptionItem(R.drawable.ic_settings_system, activity.getString(R.string.lang_system))
            )

            val langSelection = prefs.getString("lang", "system") ?: "system"
            val selectedIndex = when (langSelection) {
                "en" -> 0
                "vi" -> 1
                "ru" -> 2
                "zh" -> 3
                else -> 4
            }

            activity.showPaneDialog(
                activity.getString(R.string.info_language_title),
                langs,
                selectedIndex
            ) { which ->
                val langCode = when (which) {
                    0 -> "en"
                    1 -> "vi"
                    2 -> "ru"
                    3 -> "zh"
                    else -> "system"
                }
                if (currentLang != langCode) {
                    prefs.edit().putString("lang", langCode).apply()
                    activity.recreate()
                }
            }
        }

        binding.switchMaterialColor.isChecked = materialColorEnabled
        binding.switchMaterialColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("material_color_enabled", isChecked).apply()
            activity.recreate()
        }

        binding.switchOnlineRemoveBg.isChecked = prefs.getBoolean("removebg_online_mode", false)
        binding.switchOnlineRemoveBg.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("removebg_online_mode", isChecked).apply()
        }

        setupAboutAndMaintenanceActions()
    }

    private fun applyAndSaveTheme(prefs: SharedPreferences, mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        updateThemeText(mode)
        activity.recreate()
    }

    private fun updateThemeText(mode: Int) {
        binding.txtCurrentTheme.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> activity.getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> activity.getString(R.string.theme_dark)
            else -> activity.getString(R.string.theme_system)
        }
    }

    private fun setupAboutAndMaintenanceActions() {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(
                    activity.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            val versionName = pInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            binding.txtVersion.text = "$versionName ($versionCode)"
        } catch (_: Exception) {
            binding.txtVersion.text = "1.0.0 (1)"
        }

        binding.itemVersion.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastVersionTapTimeMs > 1800L) {
                versionTapCount = 0
            }
            lastVersionTapTimeMs = now
            versionTapCount++
            if (versionTapCount >= 7) {
                versionTapCount = 0
                ToastUtils.showToast(activity, activity.getString(R.string.thank_you_toast))
            }
        }

        binding.itemRepo.setOnClickListener {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker"))
            )
        }
        binding.itemLicense.setOnClickListener {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))
            )
        }
        binding.itemFiles.setOnClickListener {
            onLaunchFileActivity(Intent(activity, FileActivity::class.java))
        }
        binding.itemRemoveRecent.setOnClickListener { confirmRemoveRecent() }
        binding.itemRemoveAll.setOnClickListener { confirmDeleteAll() }
        binding.itemOpenSource.setOnClickListener {
            activity.startActivity(Intent(activity, LicenseActivity::class.java))
        }
    }

    private fun confirmRemoveRecent() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.boldTitle(activity.getString(R.string.info_remove_recent_confirm_title)))
            .setMessage(activity.getString(R.string.info_remove_recent_confirm_message))
            .setPositiveButton(activity.getString(R.string.delete)) { _, _ -> removeRecentUsage() }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()
            .showDestructiveDialog(activity)
    }

    private fun removeRecentUsage() {
        val prefs = activity.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val history = prefs.getString("list", "") ?: ""
        if (history.isEmpty()) {
            ToastUtils.showToast(activity, activity.getString(R.string.no_history_found))
            return
        }
        prefs.edit().remove("list").apply()
        recentsAdapterProvider().refreshData(activity)
        ToastUtils.showToast(activity, activity.getString(R.string.success))
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.boldTitle(activity.getString(R.string.info_remove_all_confirm_title)))
            .setMessage(activity.getString(R.string.info_remove_all_confirm_message))
            .setPositiveButton(activity.getString(R.string.delete)) { _, _ -> deleteAllStickers() }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()
            .showDestructiveDialog(activity)
    }

    private fun deleteAllStickers() {
        val stickerFiles = activity.filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
        if (stickerFiles.isEmpty()) {
            ToastUtils.showToast(activity, activity.getString(R.string.no_stickers_found))
            return
        }
        var successCount = 0
        stickerFiles.forEach { if (it.delete()) successCount++ }

        val adapter = adapterProvider()
        adapter.refreshData(activity)
        onSyncRecentsAfterDeletion()
        onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
        ToastUtils.showToast(
            activity,
            if (successCount > 0) activity.getString(R.string.success) else activity.getString(R.string.failed)
        )
    }
}
