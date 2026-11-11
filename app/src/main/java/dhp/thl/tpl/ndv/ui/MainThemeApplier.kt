package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.PolygonDrawable
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Handles Edge-to-Edge WindowInsets, status/navigation bar appearance, Material You (Monet)
 * dynamic colors across MainActivity surfaces/cards/switches, and the 9-sided cookie empty state.
 */
class MainThemeApplier(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding
) {
    private val cookie9Sided = RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.92f,
        rounding = CornerRounding(0.15f)
    )

    fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    fun handleEdgeToEdge() {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = topInsets.left, top = topInsets.top, right = topInsets.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.floatingBottomContainer) { view, insets ->
            val navInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val density = activity.resources.displayMetrics.density
            val sideMarginPx = (16 * density).toInt()
            val bottomMarginPx = (16 * density).toInt()
            val scrollBottomPaddingPx = (108 * density).toInt() + navInsets.bottom

            view.updatePadding(
                left = sideMarginPx + navInsets.left,
                right = sideMarginPx + navInsets.right,
                bottom = bottomMarginPx + navInsets.bottom
            )

            binding.recycler.updatePadding(bottom = scrollBottomPaddingPx)
            binding.recyclerRecents.updatePadding(bottom = scrollBottomPaddingPx)
            binding.infoLayout.updatePadding(bottom = scrollBottomPaddingPx)
            insets
        }
    }

    fun updateStatusBar() {
        val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightMode = nightMode == Configuration.UI_MODE_NIGHT_NO

        val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = isLightMode
        windowInsetsController.isAppearanceLightNavigationBars = isLightMode
    }

    fun applyMainThemeAsync(
        materialColorEnabled: Boolean,
        bottomNavController: BottomNavController,
        adapter: StickerAdapter,
        adapterRecents: StickerAdapter
    ) {
        activity.lifecycleScope.launch {
            val red = Color.parseColor("#FF3b30")
            val redAlpha = ColorUtils.setAlphaComponent(red, 30)

            if (materialColorEnabled) {
                activity.monet.awaitMonetReady()
                binding.root.applyMonetRecursively()

                val monetInstance = MonetCompat.getInstance()
                val primary = monetInstance.getAccentColor(activity)
                val surface = monetInstance.getBackgroundColor(activity)
                val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                val cardColor = if (isDark) Color.BLACK else Color.parseColor("#F5F5F5")

                binding.root.setBackgroundColor(surface)
                binding.appBarLayout.setBackgroundColor(surface)
                binding.toolbar.setBackgroundColor(surface)

                binding.sectionSettingsHeader.setTextColor(primary)
                binding.sectionGeneralHeader.setTextColor(primary)
                binding.cardSettings.setCardBackgroundColor(cardColor)
                binding.cardGeneral.setCardBackgroundColor(cardColor)

                bottomNavController.applyStyle(animateNewSelection = false)

                listOf(
                    binding.imgTheme, binding.imgMaterialColor, binding.imgLanguage,
                    binding.imgFiles, binding.imgOnlineRemoveBg,
                    binding.imgVersion, binding.imgRepo, binding.imgLicense, binding.imgOpenSource
                ).forEach { icon ->
                    icon.setColorFilter(primary)
                }

                applyDestructiveItemColors(red, redAlpha)

                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val thumbColors = intArrayOf(primary, Color.WHITE)
                val trackColors = intArrayOf(
                    ColorUtils.setAlphaComponent(primary, 128),
                    ColorUtils.setAlphaComponent(Color.LTGRAY, 128)
                )
                val thumbIconTint = ColorStateList.valueOf(
                    if (isDark) monetInstance.getBackgroundColor(activity) else Color.WHITE
                )

                binding.switchMaterialColor.thumbTintList = ColorStateList(states, thumbColors)
                binding.switchMaterialColor.trackTintList = ColorStateList(states, trackColors)
                binding.switchMaterialColor.thumbIconTintList = thumbIconTint

                binding.switchOnlineRemoveBg.thumbTintList = ColorStateList(states, thumbColors)
                binding.switchOnlineRemoveBg.trackTintList = ColorStateList(states, trackColors)
                binding.switchOnlineRemoveBg.thumbIconTintList = thumbIconTint

                binding.loadingIndicator.setIndicatorColor(primary)

                if (bottomNavController.currentTabId == R.id.nav_home) {
                    updateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                } else if (bottomNavController.currentTabId == R.id.nav_recents) {
                    updateEmptyState(adapterRecents.itemCount == 0, activity.getString(R.string.no_history_found))
                }
            } else {
                applyDestructiveItemColors(red, redAlpha)

                val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                if (!isDark) {
                    val lightOrange = activity.getColor(R.color.light_orange_background)
                    binding.root.setBackgroundColor(lightOrange)
                    binding.appBarLayout.setBackgroundColor(lightOrange)
                    binding.toolbar.setBackgroundColor(lightOrange)

                    binding.cardSettings.setCardBackgroundColor(Color.WHITE)
                    binding.cardGeneral.setCardBackgroundColor(Color.WHITE)
                }
                bottomNavController.applyStyle(animateNewSelection = false)
            }
        }
    }

    private fun applyDestructiveItemColors(red: Int, redAlpha: Int) {
        binding.imgRemoveRecent.setColorFilter(red)
        binding.imgRemoveRecent.backgroundTintList = ColorStateList.valueOf(redAlpha)
        binding.txtRemoveRecent.setTextColor(red)

        binding.imgRemoveAll.setColorFilter(red)
        binding.imgRemoveAll.backgroundTintList = ColorStateList.valueOf(redAlpha)
        binding.txtRemoveAll.setTextColor(red)
    }

    fun updateEmptyState(isEmpty: Boolean, message: String) {
        binding.emptyLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.txtEmpty.text = message

        if (isEmpty) {
            val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
            val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            val cookieBg = if (materialColorEnabled) {
                MonetCompat.getInstance().getAccentColor(activity)
            } else {
                activity.getColor(R.color.orange_primary)
            }

            val iconTint = if (materialColorEnabled) {
                if (isDark) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            binding.imgEmpty.background = PolygonDrawable(cookie9Sided, cookieBg)
            binding.imgEmpty.setColorFilter(iconTint)

            if (message == activity.getString(R.string.no_history_found)) {
                binding.imgEmpty.setImageResource(R.drawable.ic_empty_history)
            } else {
                binding.imgEmpty.setImageResource(R.drawable.ic_empty_state)
            }

            val padding = (17 * activity.resources.displayMetrics.density).toInt()
            binding.imgEmpty.setPadding(padding, padding, padding, padding)
        }
    }
}
