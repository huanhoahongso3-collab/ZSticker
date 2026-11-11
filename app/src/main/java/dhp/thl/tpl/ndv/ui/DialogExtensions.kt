package dhp.thl.tpl.ndv.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.R

class OptionAdapter(context: Context, objects: List<OptionItem>) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_icon_text, parent, false)
        val item = getItem(position)!!
        val textView = view.findViewById<TextView>(R.id.item_text)
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        textView.text = item.text
        iconView.setImageResource(item.iconRes)

        val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) {
            MonetCompat.getInstance().getAccentColor(context)
        } else {
            ContextCompat.getColor(context, R.color.orange_primary)
        }

        if (item.text == context.getString(R.string.delete) || item.text == context.getString(R.string.delete_history)) {
            val red = Color.parseColor("#FF3b30")
            textView.setTextColor(red)
            iconView.setColorFilter(red)
            iconView.background = ContextCompat.getDrawable(context, R.drawable.bg_circle_icon)
            iconView.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(red, 30))
        } else {
            val isFlag = item.iconRes == R.drawable.ic_flag_en ||
                item.iconRes == R.drawable.ic_flag_vi ||
                item.iconRes == R.drawable.ic_flag_ru ||
                item.iconRes == R.drawable.ic_flag_zh
            if (!isFlag) iconView.setColorFilter(primary) else iconView.clearColorFilter()
            iconView.background = ContextCompat.getDrawable(context, R.drawable.bg_circle_icon)
            iconView.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
        }
        return view
    }
}

class ThemeAdapter(
    context: Context,
    objects: List<OptionItem>,
    private val selectedIndex: Int
) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_radio_icon_text, parent, false)
        val item = getItem(position)!!
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        val radioButton = view.findViewById<RadioButton>(R.id.item_radio)
        val textView = view.findViewById<TextView>(R.id.item_text)
        iconView.setImageResource(item.iconRes)
        textView.text = item.text
        radioButton.isChecked = (position == selectedIndex)

        val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) {
            MonetCompat.getInstance().getAccentColor(context)
        } else {
            ContextCompat.getColor(context, R.color.orange_primary)
        }

        radioButton.buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primary, Color.GRAY)
        )
        val isFlag = item.iconRes == R.drawable.ic_flag_en ||
            item.iconRes == R.drawable.ic_flag_vi ||
            item.iconRes == R.drawable.ic_flag_ru ||
            item.iconRes == R.drawable.ic_flag_zh
        if (!isFlag) iconView.setColorFilter(primary) else iconView.clearColorFilter()
        iconView.background = ContextCompat.getDrawable(context, R.drawable.bg_circle_icon)
        iconView.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
        return view
    }
}

fun BaseActivity.showPaneDialog(
    title: String,
    items: List<OptionItem>,
    selectedIndex: Int = -1,
    onItemClick: (Int) -> Unit
) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_list_pane, null)
    val container = dialogView.findViewById<ViewGroup>(R.id.dialog_item_container)
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(boldTitle(title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.cancel), null)
        .create()

    val materialColorEnabled = getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("material_color_enabled", false)
    val primary = if (materialColorEnabled) {
        MonetCompat.getInstance().getAccentColor(this)
    } else {
        getColor(R.color.orange_primary)
    }

    items.forEachIndexed { index, _ ->
        val itemView = if (selectedIndex != -1) {
            ThemeAdapter(this, items, selectedIndex).getView(index, null, container)
        } else {
            OptionAdapter(this, items).getView(index, null, container)
        }
        itemView.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30)),
            itemView.background,
            null
        )
        itemView.setOnClickListener {
            onItemClick(index)
            dialog.dismiss()
        }
        container.addView(itemView)
        if (index < items.size - 1) {
            val tv = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)
            container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(tv.data)
                alpha = 0.1f
            })
        }
    }
    dialog.showMonetDialog(this)
}

fun AlertDialog.showMonetDialog(context: Context) {
    val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("material_color_enabled", false)
    if (materialColorEnabled) window?.decorView?.applyMonetRecursively()
    show()
    val primary = if (materialColorEnabled) {
        MonetCompat.getInstance().getAccentColor(context)
    } else {
        ContextCompat.getColor(context, R.color.orange_primary)
    }

    val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
        val btn = getButton(which)
        btn?.setTextColor(primary)
        if (btn is MaterialButton) {
            btn.rippleColor = rippleColor
        } else if (btn?.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(rippleColor)
        }
    }
    window?.setDimAmount(0.35f)
}

fun AlertDialog.showDestructiveDialog(context: Context) {
    val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("material_color_enabled", false)
    if (materialColorEnabled) window?.decorView?.applyMonetRecursively()
    show()
    val red = Color.parseColor("#FF3b30")
    val primary = if (materialColorEnabled) {
        MonetCompat.getInstance().getAccentColor(context)
    } else {
        ContextCompat.getColor(context, R.color.orange_primary)
    }

    val ripplePrimary = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
    val rippleRed = ColorStateList.valueOf(ColorUtils.setAlphaComponent(red, 30))

    getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn ->
        btn.setTextColor(red)
        if (btn is MaterialButton) {
            btn.rippleColor = rippleRed
        } else if (btn.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(rippleRed)
        }
    }

    getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn ->
        btn.setTextColor(primary)
        if (btn is MaterialButton) {
            btn.rippleColor = ripplePrimary
        } else if (btn.background is RippleDrawable) {
            (btn.background as RippleDrawable).setColor(ripplePrimary)
        }
    }

    window?.setDimAmount(0.35f)
}
