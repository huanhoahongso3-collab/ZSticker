package dhp.thl.tpl.ndv

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dhp.thl.tpl.ntt.databinding.ItemStickerBinding
import org.json.JSONArray

class StickerAdapter(
    private val stickers: MutableList<Uri>,
    private val listener: StickerListener
) : RecyclerView.Adapter<StickerAdapter.StickerViewHolder>() {

    interface StickerListener {
        fun onStickerClick(uri: Uri)
        fun onStickerLongClick(uri: Uri)
    }

    inner class StickerViewHolder(val binding: ItemStickerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val binding = ItemStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StickerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val uri = stickers[position]

        // Load sticker image efficiently
        Glide.with(holder.binding.image.context)
            .load(uri)
            .centerCrop()
            .into(holder.binding.image)

        // Tap → share
        holder.binding.image.setOnClickListener {
            listener.onStickerClick(uri)
        }

        // Long press → confirm delete
        holder.binding.image.setOnLongClickListener {
            listener.onStickerLongClick(uri)
            true
        }
    }

    override fun getItemCount(): Int = stickers.size

    /** ✅ Add a new sticker, optionally at the top */
    fun addSticker(context: Context, uri: Uri, toTop: Boolean = false) {
        if (toTop) {
            stickers.add(0, uri)
            notifyItemInserted(0)
        } else {
            stickers.add(uri)
            notifyItemInserted(stickers.size - 1)
        }
        saveOrder(context)
    }

    /** ✅ Shortcut for adding new sticker at top */
    fun addStickerAtTop(context: Context, uri: Uri) {
        addSticker(context, uri, toTop = true)
    }

    /** ✅ Remove sticker safely and persist */
    fun removeSticker(context: Context, uri: Uri) {
        val index = stickers.indexOf(uri)
        if (index != -1) {
            stickers.removeAt(index)
            notifyItemRemoved(index)
            saveOrder(context)
        }
    }

    // --- Persistence helpers (fixes random order on restart) ---

    /** ✅ Save order as JSON array (keeps exact order) */
    private fun saveOrder(context: Context) {
        val prefs = context.getSharedPreferences("stickers", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        stickers.forEach { jsonArray.put(it.toString()) }
        prefs.edit().putString("uris_json", jsonArray.toString()).apply()
    }

    companion object {
        /** ✅ Load ordered stickers */
        fun loadOrdered(context: Context): MutableList<Uri> {
            val prefs = context.getSharedPreferences("stickers", Context.MODE_PRIVATE)
            val json = prefs.getString("uris_json", "[]")
            val array = JSONArray(json)
            val list = mutableListOf<Uri>()
            for (i in 0 until array.length()) {
                list.add(Uri.parse(array.getString(i)))
            }
            return list
        }
    }
}
