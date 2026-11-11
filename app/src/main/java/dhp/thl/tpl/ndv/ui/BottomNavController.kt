package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.kieronquinn.monetcompat.core.MonetCompat
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding

/**
 * Manages the Google Photos-inspired Material 3 Expressive Floating Bottom Navigation Bar:
 * - 74% opacity (alpha = 189 / 0xBD) capsule container
 * - Rounded stadium/circle RippleDrawables on every tab (eliminating square press/hover boxes)
 * - SRC_IN uniform icon tinting so idle Home, Recents, and Options match identically in Dark Mode
 * - True 50% circular Add Sticker button spaced 22dp away from the main capsule
 * - Smooth M3 Emphasized Decelerate morph + icon spring pop + coordinated FAB reveal/hide
 */
class BottomNavController(
    private val activity: BaseActivity,
    private val binding: ActivityMainBinding,
    private val adapterProvider: () -> StickerAdapter,
    private val recentsAdapterProvider: () -> StickerAdapter,
    private val onUpdateEmptyState: (isEmpty: Boolean, message: String) -> Unit
) {
    var currentTabId: Int = R.id.nav_home
        private set

    private val m3EmphasizedInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val iconPopInterpolator = OvershootInterpolator(1.75f)

    fun setup() {
        enforceCircularAddButton()

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

    private fun enforceCircularAddButton() {
        val circleModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()
        binding.addButton.shapeAppearanceModel = circleModel
        binding.addButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val size = minOf(view.width, view.height)
                val left = (view.width - size) / 2
                val top = (view.height - size) / 2
                outline.setOval(left, top, left + size, top + size)
            }
        }
        binding.addButton.clipToOutline = true
    }

    fun selectTab(tabId: Int, animate: Boolean = true) {
        val previousTabId = currentTabId
        if (animate && previousTabId == tabId) {
            // Subtle tactile pulse when re-tapping active tab
            val activeIcon = when (tabId) {
                R.id.nav_home -> binding.iconNavHome
                R.id.nav_recents -> binding.iconNavRecents
                else -> binding.iconNavOptions
            }
            activeIcon.animate().cancel()
            activeIcon.scaleX = 0.86f
            activeIcon.scaleY = 0.86f
            activeIcon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(iconPopInterpolator)
                .start()
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
                        duration = 300L
                        interpolator = m3EmphasizedInterpolator
                    }
                )
                addTransition(
                    Fade(Fade.OUT).apply {
                        duration = 110L
                    }
                )
                addTransition(
                    Fade(Fade.IN).apply {
                        startDelay = 50L
                        duration = 210L
                        interpolator = m3EmphasizedInterpolator
                    }
                )
            }
            TransitionManager.beginDelayedTransition(binding.floatingBottomRow, navTransition)
        }

        updateScreenVisibility(tabId, animate && previousTabId != tabId)
        applyStyle(animateNewSelection = animate && previousTabId != tabId)
    }

    private fun updateScreenVisibility(itemId: Int, animateContent: Boolean) {
        val targetView: View = when (itemId) {
            R.id.nav_home -> {
                binding.toolbar.title = activity.boldTitle(activity.getString(R.string.nav_home))
                binding.recycler.visibility = View.VISIBLE
                binding.recyclerRecents.visibility = View.GONE
                binding.infoLayout.visibility = View.GONE
                animateAddButtonVisibility(show = true, animate = animateContent)
                val adapter = adapterProvider()
                onUpdateEmptyState(adapter.itemCount == 0, activity.getString(R.string.no_stickers_found))
                binding.recycler
            }
            R.id.nav_recents -> {
                binding.toolbar.title = activity.boldTitle(activity.getString(R.string.nav_recents))
                binding.recycler.visibility = View.GONE
                binding.recyclerRecents.visibility = View.VISIBLE
                binding.infoLayout.visibility = View.GONE
                animateAddButtonVisibility(show = false, animate = animateContent)
                val recentsAdapter = recentsAdapterProvider()
                recentsAdapter.refreshData(activity)
                onUpdateEmptyState(recentsAdapter.itemCount == 0, activity.getString(R.string.no_history_found))
                binding.recyclerRecents
            }
            else -> {
                binding.toolbar.title = activity.boldTitle(activity.getString(R.string.nav_options))
                binding.recycler.visibility = View.GONE
                binding.recyclerRecents.visibility = View.GONE
                binding.infoLayout.visibility = View.VISIBLE
                animateAddButtonVisibility(show = false, animate = animateContent)
                binding.emptyLayout.visibility = View.GONE
                binding.infoLayout
            }
        }

        if (animateContent) {
            val offsetPx = 6f * activity.resources.displayMetrics.density
            targetView.animate().cancel()
            targetView.alpha = 0f
            targetView.translationY = offsetPx
            targetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(m3EmphasizedInterpolator)
                .start()
        } else {
            targetView.alpha = 1f
            targetView.translationY = 0f
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
                .setDuration(280L)
                .setInterpolator(OvershootInterpolator(1.25f))
                .start()
        } else if (fab.visibility == View.VISIBLE) {
            fab.animate()
                .scaleX(0.55f)
                .scaleY(0.55f)
                .alpha(0f)
                .setDuration(150L)
                .setInterpolator(m3EmphasizedInterpolator)
                .withEndAction {
                    TransitionManager.beginDelayedTransition(
                        binding.floatingBottomRow,
                        ChangeBounds().apply {
                            duration = 220L
                            interpolator = m3EmphasizedInterpolator
                        }
                    )
                    fab.visibility = View.GONE
                }
                .start()
        }
    }

    fun applyStyle(animateNewSelection: Boolean = false) {
        enforceCircularAddButton()

        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val primaryColor: Int
        val baseSurfaceColor: Int
        val activePillColor: Int
        val activeContentColor: Int
        val inactiveIconColor: Int
        val fabBgColor: Int
        val fabIconColor: Int

        if (materialColorEnabled) {
            val monetInstance = MonetCompat.getInstance()
            val monetPrimary = try { monetInstance.getAccentColor(activity) } catch (_: Exception) { null }
            val monetSurface = try { monetInstance.getBackgroundColor(activity) } catch (_: Exception) { null }
            primaryColor = monetPrimary ?: activity.getColor(
                if (isDark) R.color.orange_primary_light else R.color.orange_primary
            )
            val fallbackSurface = activity.getColor(
                if (isDark) R.color.dark_surface_container_high else R.color.light_surface_container_high
            )
            val surface = monetSurface ?: fallbackSurface
            baseSurfaceColor = ColorUtils.blendARGB(surface, primaryColor, if (isDark) 0.16f else 0.10f)
            activePillColor = ColorUtils.setAlphaComponent(primaryColor, if (isDark) 76 else 52)
            activeContentColor = if (isDark) {
                ColorUtils.blendARGB(primaryColor, Color.WHITE, 0.38f)
            } else {
                ColorUtils.blendARGB(primaryColor, Color.BLACK, 0.25f)
            }
            inactiveIconColor = if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#313033")
            fabBgColor = ColorUtils.setAlphaComponent(primaryColor, 235)
            fabIconColor = if (ColorUtils.calculateLuminance(primaryColor) > 0.5) Color.parseColor("#1C1B1F") else Color.WHITE
        } else {
            primaryColor = activity.getColor(if (isDark) R.color.orange_primary_light else R.color.orange_primary)
            baseSurfaceColor = activity.getColor(
                if (isDark) R.color.dark_surface_container_high else R.color.light_surface_container_high
            )
            activePillColor = activity.getColor(if (isDark) R.color.dark_orange_container else R.color.orange_container)
            activeContentColor = activity.getColor(
                if (isDark) R.color.dark_orange_on_container else R.color.orange_on_container
            )
            inactiveIconColor = activity.getColor(if (isDark) R.color.dark_on_surface else R.color.light_on_surface)
            fabBgColor = ColorUtils.setAlphaComponent(activePillColor, 235)
            fabIconColor = activeContentColor
        }

        // 74% opacity = (255 * 0.74).roundToInt() = 189
        val navBar74Alpha = 189
        val translucentNavBg = ColorUtils.setAlphaComponent(baseSurfaceColor, navBar74Alpha)
        val strokeTint = ColorUtils.setAlphaComponent(primaryColor, if (isDark) 76 else 48)
        val rippleTint = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, if (isDark) 64 else 44))

        binding.floatingNavCard.setCardBackgroundColor(translucentNavBg)
        binding.floatingNavCard.strokeColor = strokeTint
        binding.floatingNavCard.cardElevation = 0f

        binding.addButton.backgroundTintList = ColorStateList.valueOf(fabBgColor)
        binding.addButton.imageTintList = ColorStateList.valueOf(fabIconColor)
        binding.addButton.rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(fabIconColor, 40)
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

            // Clear any legacy SRC_ATOP colorFilter so ImageViewCompat SRC_IN tint renders pure & uniform
            iconView.clearColorFilter()
            ImageViewCompat.setImageTintMode(iconView, PorterDuff.Mode.SRC_IN)

            if (isSelected) {
                val activeFill = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = pillCornerPx
                    setColor(activePillColor)
                }
                tabLayout.background = RippleDrawable(rippleTint, activeFill, maskDrawable)
                tabLayout.setPadding(activePaddingH, 0, activePaddingH, 0)

                textView.visibility = View.VISIBLE
                textView.setTextColor(activeContentColor)
                ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(activeContentColor))
                iconView.alpha = 1.0f

                if (animateNewSelection) {
                    iconView.animate().cancel()
                    iconView.scaleX = 0.78f
                    iconView.scaleY = 0.78f
                    iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300L)
                        .setInterpolator(iconPopInterpolator)
                        .start()

                    textView.animate().cancel()
                    textView.alpha = 0f
                    textView.translationX = -6f * density
                    textView.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(240L)
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
                ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(inactiveIconColor))
                iconView.alpha = if (isDark) 0.88f else 0.75f
                iconView.scaleX = 1f
                iconView.scaleY = 1f
            }
        }

        styleTab(binding.navTabHome, binding.iconNavHome, binding.txtNavHome, currentTabId == R.id.nav_home)
        styleTab(binding.navTabRecents, binding.iconNavRecents, binding.txtNavRecents, currentTabId == R.id.nav_recents)
        styleTab(binding.navTabOptions, binding.iconNavOptions, binding.txtNavOptions, currentTabId == R.id.nav_options)
    }
}
