package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager.GroupPosition

private fun isFlagIcon(iconRes: Int): Boolean {
    return iconRes == R.drawable.ic_flag_en ||
        iconRes == R.drawable.ic_flag_vi ||
        iconRes == R.drawable.ic_flag_ru ||
        iconRes == R.drawable.ic_flag_zh
}


/**
 * Displays an Android 16 Material 3 Expressive Segmented Grouped List Dialog.
 * Works identically for both Dynamic Wallpaper Color and Stock Orange (`#F57C00` seed).
 */
fun BaseActivity.showPaneDialog(
    title: String,
    items: List<OptionItem>,
    selectedIndex: Int = -1,
    onItemClick: (Int) -> Unit
) {
    val palette = ExpressiveThemeManager.resolvePalette(this)
    val density = resources.displayMetrics.density
    val gapPx = (3f * density).toInt()

    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_list_pane, null)
    val container = dialogView.findViewById<ViewGroup>(R.id.dialog_item_container)
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(boldTitle(title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.cancel), null)
        .create()

    items.forEachIndexed { index, item ->
        val isDestructive = item.text == getString(R.string.delete) ||
            item.text == getString(R.string.delete_history)
        val isSelected = selectedIndex != -1 && index == selectedIndex

        val groupPos = when {
            items.size == 1 -> GroupPosition.SINGLE
            index == 0 -> GroupPosition.TOP
            index == items.lastIndex -> GroupPosition.BOTTOM
            else -> GroupPosition.MIDDLE
        }

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = gapPx
            }
            isClickable = true
            isFocusable = true
        }

        val rowContainerColor = if (isSelected) palette.secondaryContainer else palette.surfaceContainerLow
        val rowRippleColor = if (isDestructive) palette.error else palette.primary
        ExpressiveThemeManager.styleSegmentedCard(
            card = card,
            position = groupPos,
            palette = palette,
            containerColor = rowContainerColor,
            rippleBaseColor = rowRippleColor
        )
        ExpressiveMotion.attachPressSpring(card, pressedScale = 0.97f)

        val rowView = if (selectedIndex != -1) {
            LayoutInflater.from(this).inflate(R.layout.item_radio_icon_text, card, false).also { v ->
                v.background = null
                val iconView = v.findViewById<ImageView>(R.id.item_icon)
                val radioButton = v.findViewById<RadioButton>(R.id.item_radio)
                val textView = v.findViewById<TextView>(R.id.item_text)

                iconView.setImageResource(item.iconRes)
                textView.text = item.text
                textView.setTextColor(if (isSelected) palette.onSecondaryContainer else palette.onSurface)
                radioButton.isChecked = isSelected
                radioButton.buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(palette.primary, palette.outline)
                )

                val flag = isFlagIcon(item.iconRes)
                ExpressiveThemeManager.styleSquircleIcon(
                    imageView = iconView,
                    badgeBgColor = if (isSelected) palette.surfaceContainerLowest else palette.secondaryContainer,
                    iconTint = palette.onSecondaryContainer,
                    tintIcon = !flag,
                    circle = true
                )
                if (flag) {
                    val fp = (8f * density).toInt()
                    iconView.setPadding(fp, fp, fp, fp)
                }
            }
        } else {
            LayoutInflater.from(this).inflate(R.layout.item_icon_text, card, false).also { v ->
                v.background = null
                val iconView = v.findViewById<ImageView>(R.id.item_icon)
                val textView = v.findViewById<TextView>(R.id.item_text)
                val padH = (16f * density).toInt()
                val padV = (12f * density).toInt()
                v.setPadding(padH, padV, padH, padV)

                iconView.setImageResource(item.iconRes)
                textView.text = item.text

                if (isDestructive) {
                    textView.setTextColor(palette.error)
                    ExpressiveThemeManager.styleSquircleIcon(
                        imageView = iconView,
                        badgeBgColor = palette.errorContainer,
                        iconTint = palette.onErrorContainer,
                        circle = true
                    )
                } else {
                    textView.setTextColor(palette.onSurface)
                    val flag = isFlagIcon(item.iconRes)
                    ExpressiveThemeManager.styleSquircleIcon(
                        imageView = iconView,
                        badgeBgColor = palette.secondaryContainer,
                        iconTint = palette.onSecondaryContainer,
                        tintIcon = !flag,
                        circle = true
                    )
                    if (flag) {
                        val fp = (8f * density).toInt()
                        iconView.setPadding(fp, fp, fp, fp)
                    }
                }
            }
        }

        card.addView(rowView)
        card.setOnClickListener {
            onItemClick(index)
            dialog.dismiss()
        }
        container.addView(card)
    }

    dialog.showMonetDialog(this)
}

private fun AlertDialog.applyExpressiveWindowChrome(context: Context, palette: ExpressivePalette) {
    val density = context.resources.displayMetrics.density
    val bgShape = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 28f * density
        setColor(palette.surfaceContainerHigh)
    }
    val insetPx = (16f * density).toInt()
    window?.setBackgroundDrawable(InsetDrawable(bgShape, insetPx))
    window?.setDimAmount(0.42f)

    findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(palette.onSurface)
    findViewById<TextView>(android.R.id.message)?.setTextColor(palette.onSurfaceVariant)
}

fun AlertDialog.showMonetDialog(context: Context) {
    val palette = ExpressiveThemeManager.resolvePalette(context)
    show()
    applyExpressiveWindowChrome(context, palette)

    val rippleColor = ColorStateList.valueOf(
        ColorUtils.setAlphaComponent(palette.primary, if (palette.isDark) 52 else 36)
    )
    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
        val btn = getButton(which)
        btn?.setTextColor(palette.primary)
        if (btn is MaterialButton) {
            btn.rippleColor = rippleColor
        } else if (btn?.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(rippleColor)
        }
    }
}

fun AlertDialog.showDestructiveDialog(context: Context) {
    val palette = ExpressiveThemeManager.resolvePalette(context)
    show()
    applyExpressiveWindowChrome(context, palette)

    val ripplePrimary = ColorStateList.valueOf(
        ColorUtils.setAlphaComponent(palette.primary, if (palette.isDark) 52 else 36)
    )
    val rippleError = ColorStateList.valueOf(
        ColorUtils.setAlphaComponent(palette.error, if (palette.isDark) 52 else 36)
    )

    getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn ->
        btn.setTextColor(palette.error)
        if (btn is MaterialButton) {
            btn.rippleColor = rippleError
        } else if (btn.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(rippleError)
        }
    }

    getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn ->
        btn.setTextColor(palette.primary)
        if (btn is MaterialButton) {
            btn.rippleColor = ripplePrimary
        } else if (btn.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(ripplePrimary)
        }
    }
}
