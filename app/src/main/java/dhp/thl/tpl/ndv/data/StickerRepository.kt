package dhp.thl.tpl.ndv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface StickerListItem {
    val stableId: String

    data class Header(val date: String) : StickerListItem {
        override val stableId: String = "header:$date"
    }

    data class Sticker(
        val file: File,
        val isRecent: Boolean = false,
        val recentEntry: String? = null,
        val timestamp: Long = 0L,
        val isFavorite: Boolean = false
    ) : StickerListItem {
        override val stableId: String = if (isRecent) {
            "recent:${recentEntry ?: "${file.name}:$timestamp"}"
        } else {
            "home:${file.name}:${file.length()}"
        }
    }
}

/** Which stickers the Home grid shows. */
sealed interface HomeFilter {
    data object All : HomeFilter
    data object Favorites : HomeFilter
    data class Folder(val id: String) : HomeFilter
}

/**
 * Single source of truth for Home stickers, Recents history, and shared Edge-to-Edge bottom insets.
 * All disk scanning and sorting runs off the UI thread on [Dispatchers.IO].
 */
object StickerRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Every sticker on disk (newest first); the visible Home list is derived from it, the metadata and the filter.
    private val _allHome = MutableStateFlow<List<StickerListItem.Sticker>>(emptyList())

    private val _selectedFilter = MutableStateFlow<HomeFilter>(HomeFilter.All)
    val selectedFilter: StateFlow<HomeFilter> = _selectedFilter.asStateFlow()

    private val _homeItems = MutableStateFlow<List<StickerListItem>>(emptyList())
    val homeItems: StateFlow<List<StickerListItem>> = _homeItems.asStateFlow()

    init {
        repoScope.launch {
            combine(_allHome, StickerMetaStore.meta, _selectedFilter) { all, meta, filter ->
                val effective = if (filter is HomeFilter.Folder && meta.folders.none { it.id == filter.id }) {
                    HomeFilter.All
                } else {
                    filter
                }
                all.mapNotNull { sticker ->
                    val favorite = sticker.file.name in meta.favorites
                    val keep = when (effective) {
                        HomeFilter.All -> true
                        HomeFilter.Favorites -> favorite
                        is HomeFilter.Folder -> meta.assignments[sticker.file.name] == effective.id
                    }
                    if (keep) sticker.copy(isFavorite = favorite) else null
                }
            }.collect { _homeItems.value = it }
        }
    }

    fun setFilter(filter: HomeFilter) {
        _selectedFilter.value = filter
    }

    private val _recentItems = MutableStateFlow<List<StickerListItem>>(emptyList())
    val recentItems: StateFlow<List<StickerListItem>> = _recentItems.asStateFlow()

    private val _homeCount = MutableStateFlow(0)
    val homeCount: StateFlow<Int> = _homeCount.asStateFlow()

    private val _recentCount = MutableStateFlow(0)
    val recentCount: StateFlow<Int> = _recentCount.asStateFlow()

    private val _isHomeLoaded = MutableStateFlow(false)
    val isHomeLoaded: StateFlow<Boolean> = _isHomeLoaded.asStateFlow()

    private val _isRecentsLoaded = MutableStateFlow(false)
    val isRecentsLoaded: StateFlow<Boolean> = _isRecentsLoaded.asStateFlow()

    private val _bottomScrollPaddingPx = MutableStateFlow(0)
    val bottomScrollPaddingPx: StateFlow<Int> = _bottomScrollPaddingPx.asStateFlow()

    fun updateBottomScrollPadding(paddingPx: Int) {
        _bottomScrollPaddingPx.value = paddingPx
    }

    fun refreshAll(context: Context, onCompleteMain: (() -> Unit)? = null) {
        val appCtx = context.applicationContext
        repoScope.launch {
            publishHomeBlocking(appCtx)
            val recents = loadRecentsBlocking(appCtx)

            _recentItems.value = recents
            _recentCount.value = recents.count { it is StickerListItem.Sticker }
            _isRecentsLoaded.value = true

            if (onCompleteMain != null) {
                withContext(Dispatchers.Main) {
                    onCompleteMain()
                }
            }
        }
    }

    fun refreshHome(context: Context, onCompleteMain: (() -> Unit)? = null) {
        val appCtx = context.applicationContext
        repoScope.launch {
            publishHomeBlocking(appCtx)
            if (onCompleteMain != null) {
                withContext(Dispatchers.Main) {
                    onCompleteMain()
                }
            }
        }
    }

    fun refreshRecents(context: Context, onCompleteMain: (() -> Unit)? = null) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val recents = loadRecentsBlocking(appCtx)
            _recentItems.value = recents
            _recentCount.value = recents.count { it is StickerListItem.Sticker }
            _isRecentsLoaded.value = true
            if (onCompleteMain != null) {
                withContext(Dispatchers.Main) {
                    onCompleteMain()
                }
            }
        }
    }

    fun recordRecentUsage(context: Context, fileName: String) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val prefs = appCtx.getSharedPreferences("recents", Context.MODE_PRIVATE)
            val list = prefs.getString("list", "")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.toMutableList() ?: mutableListOf()
            list.add(0, "$fileName:${System.currentTimeMillis()}")
            if (list.size > 100) {
                while (list.size > 100) list.removeAt(list.lastIndex)
            }
            prefs.edit().putString("list", list.joinToString(",")).commit()
            val recents = loadRecentsBlocking(appCtx)
            _recentItems.value = recents
            _recentCount.value = recents.count { it is StickerListItem.Sticker }
            _isRecentsLoaded.value = true
        }
    }

    fun removeRecentEntry(context: Context, entry: String, onDoneMain: (Boolean) -> Unit) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val prefs = appCtx.getSharedPreferences("recents", Context.MODE_PRIVATE)
            val list = prefs.getString("list", "")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.toMutableList() ?: mutableListOf()
            val removed = list.remove(entry)
            if (removed) {
                prefs.edit().putString("list", list.joinToString(",")).commit()
                val recents = loadRecentsBlocking(appCtx)
                _recentItems.value = recents
                _recentCount.value = recents.count { it is StickerListItem.Sticker }
                _isRecentsLoaded.value = true
            }
            withContext(Dispatchers.Main) {
                onDoneMain(removed)
            }
        }
    }

    fun clearRecents(context: Context, onDoneMain: (hadHistory: Boolean) -> Unit) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val prefs = appCtx.getSharedPreferences("recents", Context.MODE_PRIVATE)
            val hadHistory = !prefs.getString("list", "").isNullOrEmpty()
            if (hadHistory) {
                prefs.edit().remove("list").commit()
                _recentItems.value = emptyList()
                _recentCount.value = 0
                _isRecentsLoaded.value = true
            }
            withContext(Dispatchers.Main) {
                onDoneMain(hadHistory)
            }
        }
    }

    fun deleteSticker(context: Context, file: File, onDoneMain: (Boolean) -> Unit) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val deleted = file.exists() && file.delete()
            if (deleted) {
                pruneMissingRecentsBlocking(appCtx)
                publishHomeBlocking(appCtx)
                val recents = loadRecentsBlocking(appCtx)
                _recentItems.value = recents
                _recentCount.value = recents.count { it is StickerListItem.Sticker }
            }
            withContext(Dispatchers.Main) {
                onDoneMain(deleted)
            }
        }
    }

    fun deleteAllStickers(context: Context, onDoneMain: (hadStickers: Boolean, deletedAny: Boolean) -> Unit) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val stickerFiles = appCtx.filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
            if (stickerFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onDoneMain(false, false)
                }
                return@launch
            }
            var successCount = 0
            stickerFiles.forEach { if (it.delete()) successCount++ }
            OriginalStore.deleteAll(appCtx)
            pruneMissingRecentsBlocking(appCtx)

            publishHomeBlocking(appCtx)
            val recents = loadRecentsBlocking(appCtx)
            _recentItems.value = recents
            _recentCount.value = recents.count { it is StickerListItem.Sticker }

            withContext(Dispatchers.Main) {
                onDoneMain(true, successCount > 0)
            }
        }
    }

    /** Deletes several stickers at once and refreshes the lists a single time; returns how many were deleted. */
    fun deleteStickers(context: Context, files: List<File>, onDoneMain: (Int) -> Unit) {
        val appCtx = context.applicationContext
        repoScope.launch {
            val deleted = files.count { it.exists() && it.delete() }
            if (deleted > 0) {
                pruneMissingRecentsBlocking(appCtx)
                publishHomeBlocking(appCtx)
                val recents = loadRecentsBlocking(appCtx)
                _recentItems.value = recents
                _recentCount.value = recents.count { it is StickerListItem.Sticker }
            }
            withContext(Dispatchers.Main) {
                onDoneMain(deleted)
            }
        }
    }

    /** Loads the stickers from disk, drops metadata / originals of deleted ones, and publishes the result. */
    private fun publishHomeBlocking(context: Context) {
        StickerMetaStore.ensureLoaded(context)
        val home = loadOrderedBlocking(context).filterIsInstance<StickerListItem.Sticker>()
        StickerMetaStore.pruneMissing(context)
        OriginalStore.pruneMissing(context)
        _allHome.value = home
        _homeCount.value = home.size
        _isHomeLoaded.value = true
    }

    private fun pruneMissingRecentsBlocking(context: Context) {
        val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val list = prefs.getString("list", "")
            ?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        val filtered = list.filter { item ->
            val fileName = item.substringBefore(":")
            File(context.filesDir, fileName).exists()
        }.joinToString(",")
        prefs.edit().putString("list", filtered).commit()
    }

    private fun loadOrderedBlocking(context: Context): List<StickerListItem> {
        val folder = context.filesDir
        val files = folder.listFiles { file ->
            file.name.startsWith("zsticker_") &&
                (file.name.endsWith(".png", ignoreCase = true) || file.name.endsWith(".gif", ignoreCase = true))
        }?.sortedByDescending { file ->
            file.name
                .removePrefix("zsticker_")
                .removePrefix("rb_")
                .substringBefore(".")
                .toLongOrNull() ?: file.lastModified()
        } ?: emptyList()

        return files.map { file ->
            StickerListItem.Sticker(file = file, isRecent = false)
        }
    }

    private fun loadRecentsBlocking(context: Context): List<StickerListItem> {
        val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
        val recentEntries = prefs.getString("list", "")
            ?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        if (recentEntries.isEmpty()) return emptyList()

        val list = ArrayList<StickerListItem>(recentEntries.size + 8)
        var lastDate = ""
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        for (entry in recentEntries) {
            val fileName = entry.substringBefore(":")
            val file = File(context.filesDir, fileName)
            if (!file.exists()) continue

            val timestampStr = entry.substringAfter(":", "")
            val timestamp = timestampStr.toLongOrNull() ?: file.lastModified()
            val date = sdf.format(Date(timestamp))
            if (date != lastDate) {
                list.add(StickerListItem.Header(date))
                lastDate = date
            }
            list.add(
                StickerListItem.Sticker(
                    file = file,
                    isRecent = true,
                    recentEntry = "$fileName:$timestamp",
                    timestamp = timestamp
                )
            )
        }
        return list
    }
}
