package dhp.thl.tpl.ndv.ui

import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.data.StickerMetaStore

/**
 * Asks which folder to put stickers in: "No folder", an existing folder, or a brand-new one.
 * [onPicked] receives the folder id, or null for "No folder".
 */
fun BaseActivity.showFolderPicker(currentFolderId: String?, onPicked: (String?) -> Unit) {
    val folders = StickerMetaStore.meta.value.folders
    val items = mutableListOf(OptionItem(R.drawable.ic_close, getString(R.string.no_folder)))
    folders.forEach { items.add(OptionItem(R.drawable.ic_folder, it.name)) }
    items.add(OptionItem(R.drawable.ic_create_folder, getString(R.string.folder_new)))

    val selected = if (currentFolderId == null) {
        0
    } else {
        folders.indexOfFirst { it.id == currentFolderId }.let { if (it < 0) 0 else it + 1 }
    }

    showPaneDialog(getString(R.string.move_to_folder), items, selected) { which ->
        when {
            which == 0 -> onPicked(null)
            which <= folders.size -> onPicked(folders[which - 1].id)
            else -> showTextInputDialog(
                title = getString(R.string.folder_new_title),
                hint = getString(R.string.folder_name_hint),
                confirmLabel = getString(R.string.ok)
            ) { name ->
                StickerMetaStore.createFolder(this, name)?.let { onPicked(it.id) }
            }
        }
    }
}
