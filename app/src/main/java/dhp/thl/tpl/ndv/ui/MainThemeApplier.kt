package dhp.thl.tpl.ndv.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import kotlinx.coroutines.launch

/**
 * Coordinates Edge-to-Edge WindowInsets, status/navigation bar appearance, and unified
 * Material 3 Expressive tonal palette distribution across the MainActivity shell and child Fragments.
 */
class MainThemeApplier(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding
) {
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
            val scrollBottomPaddingPx = (112 * density).toInt() + navInsets.bottom

            view.updatePadding(
                left = sideMarginPx + navInsets.left,
                right = sideMarginPx + navInsets.right,
                bottom = bottomMarginPx + navInsets.bottom
            )
            StickerRepository.updateBottomScrollPadding(scrollBottomPaddingPx)
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

    fun applyExpressiveTheme(
        materialColorEnabled: Boolean,
        bottomNavController: BottomNavController
    ) {
        // Apply instant synchronous palette first (0ms on API 31+ Dynamic Color and Stock Orange)
        val initialPalette = ExpressiveThemeManager.resolvePalette(activity, materialColorEnabled)
        distributePalette(initialPalette, bottomNavController)

        // On Android 10–11 with Dynamic Color ON, refresh once MonetCompat finishes wallpaper extraction
        if (materialColorEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.lifecycleScope.launch {
                activity.monet.awaitMonetReady()
                val updatedPalette = ExpressiveThemeManager.resolvePalette(activity, true)
                distributePalette(updatedPalette, bottomNavController)
            }
        }
    }

    fun distributePalette(
        palette: ExpressivePalette,
        bottomNavController: BottomNavController
    ) {
        binding.root.setBackgroundColor(palette.surface)
        binding.appBarLayout.setBackgroundColor(palette.surface)
        binding.expressiveHeaderRow.setBackgroundColor(palette.surface)
        binding.loadingIndicator.setIndicatorColor(palette.primary)

        bottomNavController.applyStyle(palette = palette, animateNewSelection = false)
        bottomNavController.getHomeFragment()?.applyPalette(palette)
        bottomNavController.getRecentsFragment()?.applyPalette(palette)
        bottomNavController.getOptionsFragment()?.applyPalette(palette)
    }
}
