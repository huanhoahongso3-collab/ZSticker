package dhp.thl.tpl.ndv.ui.options

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.FileActivity
import dhp.thl.tpl.ndv.LicenseActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.ToastUtils
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.FragmentOptionsBinding
import dhp.thl.tpl.ndv.ui.showDestructiveDialog
import dhp.thl.tpl.ndv.ui.showPaneDialog
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager.GroupPosition
import kotlinx.coroutines.launch

/**
 * Dedicated Options & About Screen Fragment styled with Android 16 Material Design 3 Expressive
 * Segmented Grouped List Cards (28dp outer corners, 6dp inner corners, 3dp gaps, squircle icons).
 */
class OptionsFragment : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!

    private var versionTapCount = 0
    private var lastVersionTapTimeMs = 0L

    private val startFileActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra("did_import", false) == true
        ) {
            StickerRepository.refreshAll(requireContext()) {
                activity?.recreate()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.optionsScroll.enableStretchOverscroll()
        setupInteractions()
        applyPalette(ExpressiveThemeManager.resolvePalette(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                StickerRepository.bottomScrollPaddingPx.collect { bottomPad ->
                    if (bottomPad > 0) {
                        binding.optionsScroll.updatePadding(bottom = bottomPad)
                    }
                }
            }
        }
    }

    private fun setupInteractions() {
        val hostActivity = requireActivity() as BaseActivity
        val prefs = hostActivity.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Attach bouncy spatial spring feedback to all segmented cards
        listOf(
            binding.cardTheme,
            binding.cardMaterialColor,
            binding.cardOnlineRemoveBg,
            binding.cardAutoCrop,
            binding.cardAutoResize,
            binding.cardLanguage,
            binding.cardFiles,
            binding.cardRemoveRecent,
            binding.cardRemoveAll,
            binding.cardVersion,
            binding.cardRepo,
            binding.cardLicense,
            binding.cardOpenSource
        ).forEach { card ->
            ExpressiveMotion.attachPressSpring(card, pressedScale = 0.97f)
        }

        // 1. Theme
        updateThemeText(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        binding.cardTheme.setOnClickListener {
            val themes = listOf(
                OptionItem(R.drawable.ic_theme_light, getString(R.string.theme_light)),
                OptionItem(R.drawable.ic_theme_dark, getString(R.string.theme_dark)),
                OptionItem(R.drawable.ic_settings_system, getString(R.string.theme_system))
            )
            val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val selectedIndex = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }
            hostActivity.showPaneDialog(
                getString(R.string.info_theme_title),
                themes,
                selectedIndex
            ) { which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                applyAndSaveTheme(hostActivity, prefs, newMode)
            }
        }

        // 2. Material Color (Dynamic Wallpaper Seed vs #F57C00 Stock Orange Seed)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
        binding.switchMaterialColor.isChecked = materialColorEnabled
        binding.cardMaterialColor.setOnClickListener {
            binding.switchMaterialColor.isChecked = !binding.switchMaterialColor.isChecked
        }
        binding.switchMaterialColor.setOnCheckedChangeListener { _, isChecked ->
            if (prefs.getBoolean("material_color_enabled", false) != isChecked) {
                prefs.edit().putBoolean("material_color_enabled", isChecked).commit()
                try {
                    MonetCompat.getInstance().updateMonetColors()
                } catch (_: Exception) {}
                hostActivity.recreate()
            }
        }

        // 3. Online Background Removal
        binding.switchOnlineRemoveBg.isChecked = prefs.getBoolean("removebg_online_mode", false)
        binding.cardOnlineRemoveBg.setOnClickListener {
            binding.switchOnlineRemoveBg.isChecked = !binding.switchOnlineRemoveBg.isChecked
        }
        binding.switchOnlineRemoveBg.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("removebg_online_mode", isChecked).apply()
        }

        // 3b. Auto crop after background removal / auto resize for sharing (both default ON)
        binding.switchAutoCrop.isChecked = prefs.getBoolean("auto_crop_removebg", true)
        binding.cardAutoCrop.setOnClickListener {
            binding.switchAutoCrop.isChecked = !binding.switchAutoCrop.isChecked
        }
        binding.switchAutoCrop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_crop_removebg", isChecked).apply()
        }
        binding.switchAutoResize.isChecked = prefs.getBoolean("auto_resize_share", true)
        binding.cardAutoResize.setOnClickListener {
            binding.switchAutoResize.isChecked = !binding.switchAutoResize.isChecked
        }
        binding.switchAutoResize.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_resize_share", isChecked).apply()
        }

        // 4. Language
        val currentLang = prefs.getString("lang", "system") ?: "system"
        binding.txtCurrentLanguage.text = when (currentLang) {
            "en" -> getString(R.string.lang_en)
            "vi" -> getString(R.string.lang_vi)
            "ru" -> getString(R.string.lang_ru)
            "zh" -> getString(R.string.lang_zh)
            else -> getString(R.string.lang_system)
        }
        binding.cardLanguage.setOnClickListener {
            val langs = listOf(
                OptionItem(R.drawable.ic_flag_en, getString(R.string.lang_en)),
                OptionItem(R.drawable.ic_flag_vi, getString(R.string.lang_vi)),
                OptionItem(R.drawable.ic_flag_ru, getString(R.string.lang_ru)),
                OptionItem(R.drawable.ic_flag_zh, getString(R.string.lang_zh)),
                OptionItem(R.drawable.ic_settings_system, getString(R.string.lang_system))
            )
            val langSelection = prefs.getString("lang", "system") ?: "system"
            val selectedIndex = when (langSelection) {
                "en" -> 0
                "vi" -> 1
                "ru" -> 2
                "zh" -> 3
                else -> 4
            }
            hostActivity.showPaneDialog(
                getString(R.string.info_language_title),
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
                if (langSelection != langCode) {
                    prefs.edit().putString("lang", langCode).commit()
                    hostActivity.recreate()
                }
            }
        }

        // 5. Data & Storage Actions
        binding.cardFiles.setOnClickListener {
            startFileActivity.launch(Intent(hostActivity, FileActivity::class.java))
        }
        binding.cardRemoveRecent.setOnClickListener { confirmRemoveRecent(hostActivity) }
        binding.cardRemoveAll.setOnClickListener { confirmDeleteAll(hostActivity) }

        // 6. About & Version 7-Tap Toast
        populateVersionLabel(hostActivity)
        binding.cardVersion.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastVersionTapTimeMs > 1800L) {
                versionTapCount = 0
            }
            lastVersionTapTimeMs = now
            versionTapCount++
            if (versionTapCount >= 7) {
                versionTapCount = 0
                ToastUtils.showToast(hostActivity, getString(R.string.thank_you_toast))
            }
        }
        binding.cardRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker")))
        }
        binding.cardLicense.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
        }
        binding.cardOpenSource.setOnClickListener {
            startActivity(Intent(hostActivity, LicenseActivity::class.java))
        }
    }

    private fun applyAndSaveTheme(hostActivity: BaseActivity, prefs: SharedPreferences, mode: Int) {
        prefs.edit().putInt("theme_mode", mode).commit()
        AppCompatDelegate.setDefaultNightMode(mode)
        updateThemeText(mode)
        hostActivity.recreate()
    }

    private fun updateThemeText(mode: Int) {
        val b = _binding ?: return
        b.txtCurrentTheme.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    private fun populateVersionLabel(hostActivity: BaseActivity) {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hostActivity.packageManager.getPackageInfo(
                    hostActivity.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                hostActivity.packageManager.getPackageInfo(hostActivity.packageName, 0)
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
    }

    private fun confirmRemoveRecent(hostActivity: BaseActivity) {
        MaterialAlertDialogBuilder(hostActivity)
            .setTitle(hostActivity.boldTitle(getString(R.string.info_remove_recent_confirm_title)))
            .setMessage(getString(R.string.info_remove_recent_confirm_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                StickerRepository.clearRecents(hostActivity) { hadHistory ->
                    ToastUtils.showToast(
                        hostActivity,
                        getString(if (hadHistory) R.string.success else R.string.no_history_found)
                    )
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .showDestructiveDialog(hostActivity)
    }

    private fun confirmDeleteAll(hostActivity: BaseActivity) {
        MaterialAlertDialogBuilder(hostActivity)
            .setTitle(hostActivity.boldTitle(getString(R.string.info_remove_all_confirm_title)))
            .setMessage(getString(R.string.info_remove_all_confirm_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                StickerRepository.deleteAllStickers(hostActivity) { hadStickers, deletedAny ->
                    if (!hadStickers) {
                        ToastUtils.showToast(hostActivity, getString(R.string.no_stickers_found))
                    } else {
                        ToastUtils.showToast(
                            hostActivity,
                            getString(if (deletedAny) R.string.success else R.string.failed)
                        )
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .showDestructiveDialog(hostActivity)
    }

    fun applyPalette(palette: ExpressivePalette) {
        val b = _binding ?: return

        // Section Headers
        b.headerSettings.setTextColor(palette.primary)
        b.headerStorage.setTextColor(palette.primary)
        b.headerGeneral.setTextColor(palette.primary)

        // Group 1 Segmented Cards
        ExpressiveThemeManager.styleSegmentedCard(b.cardTheme, GroupPosition.TOP, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardMaterialColor, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardOnlineRemoveBg, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardAutoCrop, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardAutoResize, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardLanguage, GroupPosition.BOTTOM, palette)

        // Group 2 Segmented Cards
        ExpressiveThemeManager.styleSegmentedCard(b.cardFiles, GroupPosition.TOP, palette)
        ExpressiveThemeManager.styleSegmentedCard(
            b.cardRemoveRecent,
            GroupPosition.MIDDLE,
            palette,
            rippleBaseColor = palette.error
        )
        ExpressiveThemeManager.styleSegmentedCard(
            b.cardRemoveAll,
            GroupPosition.BOTTOM,
            palette,
            rippleBaseColor = palette.error
        )

        // Group 3 Segmented Cards
        ExpressiveThemeManager.styleSegmentedCard(b.cardVersion, GroupPosition.TOP, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardRepo, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardLicense, GroupPosition.MIDDLE, palette)
        ExpressiveThemeManager.styleSegmentedCard(b.cardOpenSource, GroupPosition.BOTTOM, palette)

        // Standard Tonal Squircle Icons
        listOf(
            b.imgTheme,
            b.imgMaterialColor,
            b.imgOnlineRemoveBg,
            b.imgAutoCrop,
            b.imgAutoResize,
            b.imgLanguage,
            b.imgFiles,
            b.imgVersion,
            b.imgRepo,
            b.imgLicense,
            b.imgOpenSource
        ).forEach { icon ->
            ExpressiveThemeManager.styleSquircleIcon(
                icon,
                badgeBgColor = palette.secondaryContainer,
                iconTint = palette.onSecondaryContainer
            )
        }

        // Destructive Squircle Icons & Text
        ExpressiveThemeManager.styleSquircleIcon(
            b.imgRemoveRecent,
            badgeBgColor = palette.errorContainer,
            iconTint = palette.onErrorContainer
        )
        ExpressiveThemeManager.styleSquircleIcon(
            b.imgRemoveAll,
            badgeBgColor = palette.errorContainer,
            iconTint = palette.onErrorContainer
        )
        b.txtRemoveRecent.setTextColor(palette.error)
        b.txtRemoveAll.setTextColor(palette.error)

        // Titles & Subtitles
        listOf(
            b.titleTheme,
            b.titleMaterialColor,
            b.titleOnlineRemoveBg,
            b.titleAutoCrop,
            b.titleAutoResize,
            b.titleLanguage,
            b.titleFiles,
            b.titleVersion,
            b.titleRepo,
            b.titleLicense,
            b.titleOpenSource
        ).forEach { it.setTextColor(palette.onSurface) }

        listOf(
            b.txtCurrentTheme,
            b.subtitleMaterialColor,
            b.subtitleOnlineRemoveBg,
            b.subtitleAutoCrop,
            b.subtitleAutoResize,
            b.txtCurrentLanguage,
            b.txtVersion,
            b.subtitleRepo,
            b.subtitleLicense,
            b.subtitleOpenSource
        ).forEach { it.setTextColor(palette.onSurfaceVariant) }

        // Switches
        ExpressiveThemeManager.styleSwitch(b.switchMaterialColor, palette)
        ExpressiveThemeManager.styleSwitch(b.switchOnlineRemoveBg, palette)
        ExpressiveThemeManager.styleSwitch(b.switchAutoCrop, palette)
        ExpressiveThemeManager.styleSwitch(b.switchAutoResize, palette)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
