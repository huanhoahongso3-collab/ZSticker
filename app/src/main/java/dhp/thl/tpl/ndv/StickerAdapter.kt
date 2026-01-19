package dhp.thl.tpl.ndv

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class StickerAdapter(
    private var items: MutableList<Any>,
    private val listener: StickerListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_STICKER = 1

        fun loadOrdered(context: Context): MutableList<Any> {
            val stickerFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("zaticker_") && file.extension == "png"
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val result = mutableListOf<Any>()
            var lastDate = ""
            val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

            for (file in stickerFiles) {
                val currentDate = sdf.format(Date(file.lastModified()))
                if (currentDate != lastDate) {
                    result.add(currentDate)
                    lastDate = currentDate
                }
                result.add(Uri.fromFile(file))
            }
            return result
        }
    }

    interface StickerListener {
        fun onStickerClick(uri: Uri)
        fun onStickerLongClick(uri: Uri)
    }

    // THE MISSING FUNCTION THAT CAUSED THE CRASH
    fun refreshData(context: Context) {
        items.clear()
        items.addAll(loadOrdered(context))
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_STICKER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            StickerViewHolder(inflater.inflate(R.layout.item_sticker, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.txtHeader.text = items[position] as String
        } else if (holder is StickerViewHolder) {
            val uri = items[position] as Uri
            Glide.with(holder.imgSticker.context)
                .load(uri)
                .into(holder.imgSticker)
            
            holder.imgSticker.setOnClickListener { listener.onStickerClick(uri) }
            holder.imgSticker.setOnLongClickListener {
                listener.onStickerLongClick(uri)
                true
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgSticker: ShapeableImageView = view.findViewById(R.id.imgSticker)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHeader: TextView = view.findViewById(R.id.txtHeader)
    }
}
