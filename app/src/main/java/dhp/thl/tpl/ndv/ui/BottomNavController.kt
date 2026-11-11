package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.shape.ShapeAppearanceModel
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.ui.home.HomeFragment
import dhp.thl.tpl.ndv.ui.options.OptionsFragment
import dhp.thl.tpl.ndv.ui.recents.RecentsFragment
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager

/**
 * Manages the Material 3 Expressive Floating Bottom Navigation Bar + Fragment Screen Switching:
 * - Zero colored border (`strokeWidth = 0`, `Color.TRANSPARENT` stroke) around the 74%-translucent capsule
 * - Identical M3 tonal styling for both Dynamic Wallpaper Color and Stock Orange (`#F57C00` seed)
 * - Rounded stadium RippleDrawables + physics-based `SpringAnimation` icon pop & FAB bounce
 * - Hosts `HomeFragment`, `RecentsFragment`, and `OptionsFragment` cleanly while preserving scroll state
 */
class BottomNavController(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding
) {
    var currentTabId: Int = R.id.nav_home
        private set

    private val m3EmphasizedInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val fabOvershootInterpolator = OvershootInterpolator(1.32f)

    private var currentPalette: ExpressivePalette? = null

    fun setup() {
        enforceRoundedAddButton()
        ExpressiveMotion.attachPressSpring(binding.addButton, pressedScale = 0.90f)
        ExpressiveMotion.attachPressSpring(binding.navTabHome, pressedScale = 0.94f)
        ExpressiveMotion.attachPressSpring(binding.navTabRecents, pressedScale = 0.94f)
        ExpressiveMotion.attachPressSpring(binding.navTabOptions, pressedScale = 0.94f)

        ensureFragmentsAttached()

        binding.navTabHome.setOnClickListener {
            selectTab(R.id.nav_home, animate = true)
        }
        binding.navTabRecents.setOnClickListener {
            selectTab(R.id.nav_recents, animate = true)
        }
        binding.navTabOptions.setOnClickListener {
            selectTab(R.id.nav_options, animate = true)
        }
    }

    private fun ensureFragmentsAttached() {
        val fm = activity.supportFragmentManager
        var tx: androidx.fragment.app.FragmentTransaction? = null

        val home = fm.findFragmentByTag(TAG_HOME)
        val recents = fm.findFragmentByTag(TAG_RECENTS)
        val options = fm.findFragmentByTag(TAG_OPTIONS)

        if (home == null) {
            tx = (tx ?: fm.beginTransaction()).add(R.id.fragmentContainer, HomeFragment(), TAG_HOME)
        }
        if (recents == null) {
            val f = RecentsFragment()
            tx = (tx ?: fm.beginTransaction()).add(R.id.fragmentContainer, f, TAG_RECENTS).hide(f)
        }
        if (options == null) {
            val f = OptionsFragment()
            tx = (tx ?: fm.beginTransaction()).add(R.id.fragmentContainer, f, TAG_OPTIONS).hide(f)
        }
        tx?.commitNowAllowingStateLoss()
    }

    fun getHomeFragment(): HomeFragment? =
        activity.supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment

    fun getRecentsFragment(): RecentsFragment? =
        activity.supportFragmentManager.findFragmentByTag(TAG_RECENTS) as? RecentsFragment

    fun getOptionsFragment(): OptionsFragment? =
        activity.supportFragmentManager.findFragmentByTag(TAG_OPTIONS) as? OptionsFragment

    private fun enforceRoundedAddButton() {
        val cornerPx = ADD_BUTTON_CORNER_DP * activity.resources.displayMetrics.density
        val roundedModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerPx)
            .build()
        binding.addButton.shapeAppearanceModel = roundedModel
        binding.addButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        binding.addButton.clipToOutline = true
    }

    fun selectTab(tabId: Int, animate: Boolean = true) {
        val previousTabId = currentTabId
        if (animate && previousTabId == tabId) {
            val activeIcon = when (tabId) {
                R.id.nav_home -> binding.iconNavHome
                R.id.nav_recents -> binding.iconNavRecents
                else -> binding.iconNavOptions
            }
            ExpressiveMotion.springPopIn(activeIcon, initialScale = 0.80f)
            if (tabId == R.id.nav_home) {
                getHomeFragment()?.scrollToTop()
            }
            return
        }

        currentTabId = tabId
        activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("last_tab", tabId)
            .apply()

        if (animate) {
            TransitionManager.endTransitions(binding.floatingBottomRow)
            val navTransition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(
                    ChangeBounds().apply {
                        duration = 310L
                        interpolator = m3EmphasizedInterpolator
                    }
                )
                addTransition(
                    Fade(Fade.OUT).apply {
                        duration = 100L
                    }
                )
                addTransition(
                    Fade(Fade.IN).apply {
                        startDelay = 45L
                        duration = 210L
                        interpolator = m3EmphasizedInterpolator
                    }
                )
            }
            TransitionManager.beginDelayedTransition(binding.floatingBottomRow, navTransition)
        }

        val palette = currentPalette ?: ExpressiveThemeManager.resolvePalette(activity)
        switchFragmentScreen(tabId, animate && previousTabId != tabId)
        updateHeaderTitleAndBadge(palette)
        applyStyle(palette = palette, animateNewSelection = animate && previousTabId != tabId)
    }

    private fun switchFragmentScreen(itemId: Int, animateContent: Boolean) {
        ensureFragmentsAttached()
        val fm = activity.supportFragmentManager
        val home = fm.findFragmentByTag(TAG_HOME)
        val recents = fm.findFragmentByTag(TAG_RECENTS)
        val options = fm.findFragmentByTag(TAG_OPTIONS)

        val targetFragment: Fragment? = when (itemId) {
            R.id.nav_home -> home
            R.id.nav_recents -> recents
            else -> options
        }

        val tx = fm.beginTransaction()
        listOfNotNull(home, recents, options).forEach { frag ->
            if (frag === targetFragment) {
                tx.show(frag)
            } else {
                tx.hide(frag)
            }
        }
        tx.commitNowAllowingStateLoss()

        animateAddButtonVisibility(show = itemId == R.id.nav_home, animate = animateContent)
        if (itemId == R.id.nav_recents) {
            StickerRepository.refreshRecents(activity)
        }

        val rootView = targetFragment?.view ?: binding.fragmentContainer
        if (animateContent) {
            val offsetPx = 10f * activity.resources.displayMetrics.density
            rootView.animate().cancel()
            rootView.alpha = 0f
            rootView.translationY = offsetPx
            rootView.animate()
                .alpha(1f)
                .setDuration(190L)
                .setInterpolator(m3EmphasizedInterpolator)
                .start()
            ExpressiveMotion.springTranslationY(rootView, 0f, dampingRatio = 0.74f, stiffness = 420f)
        } else {
            ExpressiveMotion.resetToRest(rootView)
            rootView.alpha = 1f
        }
    }

    fun updateHeaderTitleAndBadge(
        palette: ExpressivePalette = currentPalette ?: ExpressiveThemeManager.resolvePalette(activity)
    ) {
        val density = activity.resources.displayMetrics.density
        when (currentTabId) {
            R.id.nav_home -> {
                binding.txtToolbarTitle.text = activity.getString(R.string.nav_home)
                val count = StickerRepository.homeCount.value
                if (count > 0) {
                    binding.txtToolbarBadge.visibility = View.VISIBLE
                    binding.txtToolbarBadge.text = count.toString()
                } else {
                    binding.txtToolbarBadge.visibility = View.GONE
                }
            }
            R.id.nav_recents -> {
                binding.txtToolbarTitle.text = activity.getString(R.string.nav_recents)
                val count = StickerRepository.recentCount.value
                if (count > 0) {
                    binding.txtToolbarBadge.visibility = View.VISIBLE
                    binding.txtToolbarBadge.text = count.toString()
                } else {
                    binding.txtToolbarBadge.visibility = View.GONE
                }
            }
            else -> {
                binding.txtToolbarTitle.text = activity.getString(R.string.nav_options)
                binding.txtToolbarBadge.visibility = View.GONE
            }
        }

        binding.txtToolbarTitle.setTextColor(palette.onSurface)
        binding.txtToolbarBadge.setTextColor(palette.onSecondaryContainer)
        binding.txtToolbarBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(palette.secondaryContainer)
        }
    }

    private fun animateAddButtonVisibility(show: Boolean, animate: Boolean) {
        val fab = binding.addButton
        fab.animate().cancel()
        if (!animate) {
            fab.scaleX = 1f
            fab.scaleY = 1f
            fab.alpha = 1f
            fab.rotation = 0f
            fab.visibility = if (show) View.VISIBLE else View.GONE
            return
        }

        if (show) {
            if (fab.visibility != View.VISIBLE) {
                fab.visibility = View.VISIBLE
                fab.scaleX = 0.55f
                fab.scaleY = 0.55f
                fab.alpha = 0f
                fab.rotation = -45f
            }
            fab.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .rotation(0f)
                .setDuration(290L)
                .setInterpolator(fabOvershootInterpolator)
                .start()
        } else if (fab.visibility == View.VISIBLE) {
            // Hide inside the nav transition already begun in selectTab (Fade OUT + ChangeBounds),
            // so the bar starts moving on press instead of waiting for a separate FAB animation.
            fab.visibility = View.GONE
        }
    }

    fun applyStyle(
        palette: ExpressivePalette = ExpressiveThemeManager.resolvePalette(activity),
        animateNewSelection: Boolean = false
    ) {
        currentPalette = palette
        enforceRoundedAddButton()
        updateHeaderTitleAndBadge(palette)

        // 74% opacity = (255 * 0.74).roundToInt() = 189
        val navBar74Alpha = 189
        val translucentNavBg = ColorUtils.setAlphaComponent(palette.surfaceContainerHigh, navBar74Alpha)
        val rippleTint = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(palette.primary, if (palette.isDark) 60 else 40)
        )

        // Zero colored border around bottom navbar — clean translucent capsule only
        binding.floatingNavCard.setCardBackgroundColor(translucentNavBg)
        binding.floatingNavCard.strokeWidth = 0
        binding.floatingNavCard.strokeColor = Color.TRANSPARENT
        binding.floatingNavCard.cardElevation = 0f

        // Expressive circular '+' FAB in vibrant M3 primary container / primary role
        val fabBgColor = ColorUtils.setAlphaComponent(palette.primary, 242)
        val fabIconColor = palette.onPrimary
        binding.addButton.backgroundTintList = ColorStateList.valueOf(fabBgColor)
        binding.addButton.imageTintList = ColorStateList.valueOf(fabIconColor)
        binding.addButton.setRippleColor(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(fabIconColor, 44))
        )

        val density = activity.resources.displayMetrics.density
        val pillCornerPx = 999f * density
        val activePaddingH = (16 * density).toInt()
        val inactivePaddingH = (14 * density).toInt()

        fun styleTab(
            tabLayout: LinearLayout,
            iconView: ImageView,
            textView: TextView,
            isSelected: Boolean
        ) {
            tabLayout.foreground = null
            tabLayout.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                }
            }
            tabLayout.clipToOutline = true

            val maskDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = pillCornerPx
                setColor(Color.WHITE)
            }

            iconView.clearColorFilter()
            ImageViewCompat.setImageTintMode(iconView, PorterDuff.Mode.SRC_IN)

            if (isSelected) {
                val activeFill = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = pillCornerPx
                    setColor(palette.secondaryContainer)
                }
                tabLayout.background = RippleDrawable(rippleTint, activeFill, maskDrawable)
                tabLayout.setPadding(activePaddingH, 0, activePaddingH, 0)

                textView.visibility = View.VISIBLE
                textView.setTextColor(palette.onSecondaryContainer)
                ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(palette.onSecondaryContainer))
                iconView.alpha = 1.0f

                if (animateNewSelection) {
                    ExpressiveMotion.springPopIn(iconView, initialScale = 0.76f)

                    textView.animate().cancel()
                    textView.alpha = 0f
                    textView.translationX = -6f * density
                    textView.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(230L)
                        .setInterpolator(m3EmphasizedInterpolator)
                        .start()
                } else {
                    iconView.scaleX = 1f
                    iconView.scaleY = 1f
                    textView.alpha = 1f
                    textView.translationX = 0f
                }
            } else {
                val idleContent = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = pillCornerPx
                    setColor(Color.TRANSPARENT)
                }
                tabLayout.background = RippleDrawable(rippleTint, idleContent, maskDrawable)
                tabLayout.setPadding(inactivePaddingH, 0, inactivePaddingH, 0)

                textView.visibility = View.GONE
                val idleTint = if (palette.isDark) palette.onSurface else palette.onSurfaceVariant
                ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(idleTint))
                iconView.alpha = if (palette.isDark) 0.90f else 0.82f
                iconView.scaleX = 1f
                iconView.scaleY = 1f
            }
        }

        styleTab(binding.navTabHome, binding.iconNavHome, binding.txtNavHome, currentTabId == R.id.nav_home)
        styleTab(binding.navTabRecents, binding.iconNavRecents, binding.txtNavRecents, currentTabId == R.id.nav_recents)
        styleTab(binding.navTabOptions, binding.iconNavOptions, binding.txtNavOptions, currentTabId == R.id.nav_options)
    }

    companion object {
        private const val ADD_BUTTON_CORNER_DP = 20f

        const val TAG_HOME = "screen_home"
        const val TAG_RECENTS = "screen_recents"
        const val TAG_OPTIONS = "screen_options"
    }
}
