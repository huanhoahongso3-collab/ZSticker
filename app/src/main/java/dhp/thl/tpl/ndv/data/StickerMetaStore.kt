package dhp.thl.tpl.ndv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Favorites and folders for stickers, kept in `sticker_meta.json` in the app's files directory.
 * A sticker can be a favorite and sit in at most one folder. Included in backups.
 */
object StickerMetaStore {

    data class Folder(val id: String, val name: String)

    data class Meta(
        val favorites: Set<String> = emptySet(),
        val folders: List<Folder> = emptyList(),
        /** sticker file name -> folder id */
        val assignments: Map<String, String> = emptyMap()
    )

    private const val FILE_NAME = "sticker_meta.json"
    private const val MAX_NAME = 40

    private val _meta = MutableStateFlow(Meta())
    val meta: StateFlow<Meta> = _meta.asStateFlow()

    private var loaded = false

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        _meta.value = read(context)
        loaded = true
    }

    @Synchronized
    fun setFavorite(context: Context, names: Collection<String>, favorite: Boolean) {
        ensureLoaded(context)
        val current = _meta.value
        val favorites = if (favorite) current.favorites + names else current.favorites - names.toSet()
        commit(context, current.copy(favorites = favorites))
    }

    @Synchronized
    fun createFolder(context: Context, name: String): Folder? {
        ensureLoaded(context)
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return null
        val folder = Folder(UUID.randomUUID().toString(), clean)
        val current = _meta.value
        commit(context, current.copy(folders = current.folders + folder))
        return folder
    }

    @Synchronized
    fun renameFolder(context: Context, id: String, name: String) {
        ensureLoaded(context)
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        val current = _meta.value
        commit(context, current.copy(folders = current.folders.map { if (it.id == id) it.copy(name = clean) else it }))
    }

    /** Deletes the folder only; its stickers stay in the app and become unfiled. */
    @Synchronized
    fun deleteFolder(context: Context, id: String) {
        ensureLoaded(context)
        val current = _meta.value
        commit(
            context,
            current.copy(
                folders = current.folders.filterNot { it.id == id },
                assignments = current.assignments.filterValues { it != id }
            )
        )
    }

    /** Puts [names] into [folderId], or removes them from any folder when it is null. */
    @Synchronized
    fun assign(context: Context, names: Collection<String>, folderId: String?) {
        ensureLoaded(context)
        val current = _meta.value
        val assignments = current.assignments.toMutableMap()
        for (name in names) {
            if (folderId == null) assignments.remove(name) else assignments[name] = folderId
        }
        commit(context, current.copy(assignments = assignments))
    }

    /** Forgets stickers that were deleted. */
    @Synchronized
    fun forget(context: Context, names: Collection<String>) {
        ensureLoaded(context)
        val set = names.toSet()
        val current = _meta.value
        if (current.favorites.none { it in set } && current.assignments.keys.none { it in set }) return
        commit(
            context,
            current.copy(
                favorites = current.favorites - set,
                assignments = current.assignments.filterKeys { it !in set }
            )
        )
    }

    /** Drops entries for stickers whose files no longer exist. */
    @Synchronized
    fun pruneMissing(context: Context) {
        ensureLoaded(context)
        val current = _meta.value
        val missing = (current.favorites + current.assignments.keys).filter { !File(context.filesDir, it).exists() }
        if (missing.isNotEmpty()) forget(context, missing)
    }

    @Synchronized
    fun exportJson(context: Context): String {
        ensureLoaded(context)
        return toJson(_meta.value).toString()
    }

    /** Merges a backup's metadata into the current one (favorites united, imported folder names kept). */
    @Synchronized
    fun importJson(context: Context, json: String) {
        ensureLoaded(context)
        val incoming = parse(json)
        val current = _meta.value
        val folders = LinkedHashMap<String, Folder>()
        (current.folders + incoming.folders).forEach { folders.putIfAbsent(it.id, it) }
        val assignments = current.assignments + incoming.assignments.filterValues { it in folders }
        commit(
            context,
            Meta(
                favorites = current.favorites + incoming.favorites,
                folders = folders.values.toList(),
                assignments = assignments
            )
        )
    }

    private fun commit(context: Context, meta: Meta) {
        _meta.value = meta
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(toJson(meta).toString())
        if (!tmp.renameTo(file(context))) {
            file(context).writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun read(context: Context): Meta {
        val f = file(context)
        if (!f.exists()) return Meta()
        return try {
            parse(f.readText())
        } catch (_: Exception) {
            Meta()
        }
    }

    private fun toJson(meta: Meta): JSONObject = JSONObject().apply {
        put("favorites", JSONArray(meta.favorites.toList()))
        put("folders", JSONArray().also { array ->
            meta.folders.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        put("assignments", JSONObject().also { o -> meta.assignments.forEach { (k, v) -> o.put(k, v) } })
    }

    private fun parse(json: String): Meta {
        val root = JSONObject(json)
        val favorites = HashSet<String>()
        root.optJSONArray("favorites")?.let { for (i in 0 until it.length()) favorites.add(it.optString(i)) }
        val folders = ArrayList<Folder>()
        root.optJSONArray("folders")?.let {
            for (i in 0 until it.length()) {
                val o = it.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isNotEmpty() && name.isNotEmpty()) folders.add(Folder(id, name.take(MAX_NAME)))
            }
        }
        val assignments = HashMap<String, String>()
        root.optJSONObject("assignments")?.let { o ->
            o.keys().forEach { key -> assignments[key] = o.optString(key) }
        }
        return Meta(favorites, folders, assignments.filterValues { id -> folders.any { it.id == id } })
    }
}
