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
    private val listener: StickerListener,
    private val showHeaders: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class RecentSticker(val file: File, val timestamp: Long)

    interface StickerListener {
        fun onStickerClick(uri: Uri)
        fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String? = null)
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
        } else if (holder is StickerViewHolder && (item is File || item is RecentSticker)) {
            val file = if (item is RecentSticker) item.file else item as File
            Glide.with(holder.image).load(file).into(holder.image)
            
            holder.itemView.setOnClickListener {
                val uri = FileProvider.getUriForFile(
                    holder.itemView.context,
                    "${holder.itemView.context.packageName}.provider",
                    file
                )
                listener.onStickerClick(uri)
            }
            
            holder.itemView.setOnLongClickListener {
                if (item is RecentSticker) {
                    val uri = Uri.fromFile(item.file)
                    listener.onStickerLongClick(uri, true, "${item.file.name}:${item.timestamp}")
                } else {
                    val uri = Uri.fromFile(item as File)
                    listener.onStickerLongClick(uri, false)
                }
                true
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun refreshData(context: Context) {
        this.items.clear()
        this.items.addAll(if (showHeaders) loadOrdered(context) else loadRecents(context))
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
            
            val files = folder.listFiles { file ->
                file.name.startsWith("zsticker_") && file.name.endsWith(".png")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            list.addAll(files)
            return list
        }

        fun loadRecents(context: Context): MutableList<Any> {
            val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
            val recentEntries = prefs.getString("list", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            val list = mutableListOf<Any>()
            
            var lastDate = ""
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            recentEntries.forEach { entry ->
                val fileName = entry.substringBefore(":")
                val timestampStr = entry.substringAfter(":", "")
                val timestamp = timestampStr.toLongOrNull() ?: File(context.filesDir, fileName).lastModified()
                
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val date = sdf.format(Date(timestamp))
                    if (date != lastDate) {
                        list.add(date)
                        lastDate = date
                    }
                    list.add(RecentSticker(file, timestamp))
                }
            }
            return list
        }
    }
}
