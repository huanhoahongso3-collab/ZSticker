package dhp.thl.tpl.ndv

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import dhp.thl.tpl.ndv.data.StickerListItem
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager

/**
 * DiffUtil-backed Material 3 Expressive sticker & history grid adapter.
 * Features:
 * - Tonal `surfaceContainerLow` 22dp squircle cards with `fitCenter` silhouette framing
 * - Interactive spatial spring press + 22dp -> 32dp corner-radius morphing
 * - Tonal `tertiaryContainer` GIF pill badges & `secondaryContainer` date headers
 */
class StickerAdapter(
    private val listener: StickerListener
) : ListAdapter<StickerListItem, RecyclerView.ViewHolder>(DiffCallback) {

    interface StickerListener {
        fun onStickerClick(uri: Uri)
        fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String? = null)
    }

    private var palette: ExpressivePalette? = null

    fun updatePalette(newPalette: ExpressivePalette) {
        if (palette != newPalette) {
            palette = newPalette
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StickerListItem.Header -> VIEW_TYPE_HEADER
            is StickerListItem.Sticker -> VIEW_TYPE_STICKER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
            StickerViewHolder(view).also { holder ->
                ExpressiveMotion.attachCardTouchMorph(
                    targetView = holder.card,
                    cardView = holder.card,
                    restingCornerDp = 22f,
                    pressedCornerDp = 32f,
                    pressedScale = 0.93f
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        val pal = palette ?: ExpressiveThemeManager.resolvePalette(context).also { palette = it }
        val density = context.resources.displayMetrics.density

        when (val item = getItem(position)) {
            is StickerListItem.Header -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.text.text = item.date
                headerHolder.text.setTextColor(pal.onSecondaryContainer)
                headerHolder.text.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f * density
                    setColor(pal.secondaryContainer)
                }
            }

            is StickerListItem.Sticker -> {
                val stickerHolder = holder as StickerViewHolder
                val file = item.file
                val isGif = file.name.endsWith(".gif", ignoreCase = true)

                stickerHolder.card.setCardBackgroundColor(pal.surfaceContainerLow)
                stickerHolder.card.strokeWidth = 0
                stickerHolder.card.rippleColor = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(pal.primary, if (pal.isDark) 52 else 36)
                )

                if (isGif) {
                    stickerHolder.badgeGif.visibility = View.VISIBLE
                    stickerHolder.badgeGif.setTextColor(pal.onTertiaryContainer)
                    stickerHolder.badgeGif.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 999f * density
                        setColor(ColorUtils.setAlphaComponent(pal.tertiaryContainer, 238))
                    }
                } else {
                    stickerHolder.badgeGif.visibility = View.GONE
                }

                Glide.with(stickerHolder.image)
                    .load(file)
                    .into(stickerHolder.image)

                stickerHolder.card.setOnClickListener {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    listener.onStickerClick(uri)
                }

                stickerHolder.card.setOnLongClickListener {
                    val uri = Uri.fromFile(file)
                    if (item.isRecent) {
                        listener.onStickerLongClick(uri, true, item.recentEntry)
                    } else {
                        listener.onStickerLongClick(uri, false, null)
                    }
                    true
                }
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.headerText)
    }

    class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.stickerImage)
        val card: MaterialCardView = view.findViewById(R.id.stickerCard)
        val badgeGif: TextView = view.findViewById(R.id.badgeGif)
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_STICKER = 1

        private object DiffCallback : DiffUtil.ItemCallback<StickerListItem>() {
            override fun areItemsTheSame(oldItem: StickerListItem, newItem: StickerListItem): Boolean {
                return oldItem.stableId == newItem.stableId
            }

            override fun areContentsTheSame(oldItem: StickerListItem, newItem: StickerListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
