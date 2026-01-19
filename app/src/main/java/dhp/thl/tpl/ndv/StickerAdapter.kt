package dhp.thl.tpl.ndv

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class StickerAdapter(
    private var items: MutableList<Any>,
    private val listener: StickerListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface StickerListener {
        fun onStickerClick(uri: Uri)
        fun onStickerLongClick(uri: Uri)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
            StickerViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is String) {
            holder.text.text = item
        } else if (holder is StickerViewHolder && item is File) {
            Glide.with(holder.image).load(item).into(holder.image)
            
            holder.itemView.setOnClickListener {
                val uri = FileProvider.getUriForFile(
                    holder.itemView.context,
                    "${holder.itemView.context.packageName}.provider",
                    item
                )
                listener.onStickerClick(uri)
            }
            
            holder.itemView.setOnLongClickListener {
                val uri = Uri.fromFile(item)
                listener.onStickerLongClick(uri)
                true
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun refreshData(context: Context) {
        this.items.clear()
        this.items.addAll(loadOrdered(context))
        notifyDataSetChanged()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.headerText)
    }

    class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.stickerImage)
    }

    companion object {
        fun loadOrdered(context: Context): MutableList<Any> {
            val list = mutableListOf<Any>()
            val folder = context.filesDir
            
            // FIX: Updated to zsticker_
            val files = folder.listFiles { file ->
                file.name.startsWith("zsticker_") && file.name.endsWith(".png")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            var lastDate = ""
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            files.forEach { file ->
                val date = sdf.format(Date(file.lastModified()))
                if (date != lastDate) {
                    list.add(date)
                    lastDate = date
                }
                list.add(file)
            }
            return list
        }
    }
}
