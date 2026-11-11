package dhp.thl.tpl.ndv.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.BaseActivity
import dhp.thl.tpl.ndv.MainActivity
import dhp.thl.tpl.ndv.OptionItem
import dhp.thl.tpl.ndv.PolygonDrawable
import dhp.thl.tpl.ndv.R
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.data.HomeFilter
import dhp.thl.tpl.ndv.data.StickerListItem
import dhp.thl.tpl.ndv.data.StickerMetaStore
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.FragmentHomeBinding
import dhp.thl.tpl.ndv.domain.BatchActions
import dhp.thl.tpl.ndv.ui.showDestructiveDialog
import dhp.thl.tpl.ndv.ui.showPaneDialog
import dhp.thl.tpl.ndv.ui.showTextInputDialog
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Dedicated Home Screen Fragment displaying the user's sticker collection in a responsive
 * M3 Expressive squircle grid alongside an expressive 9-sided cookie empty state.
 *
 * Also hosts the All / Favorites / folder filter chips and the multi-select mode with its batch actions.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var stickerAdapter: StickerAdapter
    private lateinit var batch: BatchActions
    private var currentPalette: ExpressivePalette? = null

    private val selected = LinkedHashSet<String>()
    private var selecting = false

    private val cookie9Sided = RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.80f,
        rounding = CornerRounding(0.22f)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listener = requireActivity() as StickerAdapter.StickerListener
        stickerAdapter = StickerAdapter(listener)
        stickerAdapter.onToggleSelect = { file -> toggleSelection(file) }
        batch = BatchActions(requireActivity() as BaseActivity)

        val dm = resources.displayMetrics
        val columns = (dm.widthPixels / dm.density / 116f).toInt().coerceAtLeast(3)
        binding.recyclerHome.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.recyclerHome.adapter = stickerAdapter
        binding.recyclerHome.itemAnimator = null
        binding.recyclerHome.enableStretchOverscroll()

        ExpressiveMotion.attachPressSpring(binding.btnEmptyAddSticker, pressedScale = 0.94f)
        binding.btnEmptyAddSticker.setOnClickListener {
            (activity as? MainActivity)?.launchMediaPicker()
        }

        setupSelectionActions()
        applyPalette(ExpressiveThemeManager.resolvePalette(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        StickerRepository.homeItems,
                        StickerRepository.isHomeLoaded,
                        StickerRepository.homeCount
                    ) { items, isLoaded, total -> Triple(items, isLoaded, total) }
                        .collect { (items, isLoaded, total) ->
                            stickerAdapter.submitList(items)
                            val showEmpty = isLoaded && total == 0
                            val wasVisible = binding.emptyHomeLayout.visibility == View.VISIBLE
                            binding.emptyHomeLayout.visibility = if (showEmpty) View.VISIBLE else View.GONE
                            binding.txtFilterEmpty.visibility =
                                if (isLoaded && total > 0 && items.isEmpty()) View.VISIBLE else View.GONE
                            if (showEmpty && !wasVisible) {
                                ExpressiveMotion.springPopIn(binding.imgEmptyHome, initialScale = 0.80f)
                            }
                            dropSelectionOfMissing(items)
                        }
                }
                launch {
                    combine(
                        StickerMetaStore.meta.map { it.folders }.distinctUntilChanged(),
                        StickerRepository.selectedFilter
                    ) { folders, filter -> folders to filter }
                        .collect { rebuildChips() }
                }
                launch {
                    StickerRepository.bottomScrollPaddingPx.collect { bottomPad ->
                        if (bottomPad > 0) {
                            binding.recyclerHome.updatePadding(bottom = bottomPad)
                        }
                    }
                }
            }
        }
    }

    fun applyPalette(palette: ExpressivePalette) {
        currentPalette = palette
        val b = _binding ?: return
        stickerAdapter.updatePalette(palette)

        b.imgEmptyHome.background = PolygonDrawable(cookie9Sided, palette.secondaryContainer)
        b.imgEmptyHome.setColorFilter(palette.onSecondaryContainer)
        b.txtEmptyHomeTitle.setTextColor(palette.onSurface)
        b.txtEmptyHomeSubtitle.setTextColor(palette.onSurfaceVariant)
        b.txtFilterEmpty.setTextColor(palette.onSurfaceVariant)

        b.btnEmptyAddSticker.backgroundTintList = ColorStateList.valueOf(palette.primary)
        b.btnEmptyAddSticker.setTextColor(palette.onPrimary)
        b.btnEmptyAddSticker.iconTint = ColorStateList.valueOf(palette.onPrimary)

        // Selection bars
        b.selectionBar.setCardBackgroundColor(palette.surfaceContainerHigh)
        b.selectionActions.setCardBackgroundColor(palette.surfaceContainerHigh)
        b.btnSelectClose.setColorFilter(palette.onSurface)
        b.txtSelectionCount.setTextColor(palette.onSurface)
        b.btnSelectAll.setTextColor(palette.primary)
        listOf(
            b.actFavorite to palette.onSurface,
            b.actFolder to palette.onSurface,
            b.actBorder to palette.onSurface,
            b.actExport to palette.onSurface,
            b.actDelete to palette.error
        ).forEach { (action, color) ->
            (action.getChildAt(0) as ImageView).setColorFilter(color)
            (action.getChildAt(1) as TextView).setTextColor(color)
        }

        rebuildChips()
    }

    fun scrollToTop() {
        _binding?.recyclerHome?.scrollToPosition(0)
    }

    // ---------------------------------------------------------------------------------------------
    // Filter chips: All, Favorites, folders, "+ New folder"
    // ---------------------------------------------------------------------------------------------

    private fun checkedStates(checked: Int, unchecked: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, unchecked)
    )

    private fun rebuildChips() {
        val b = _binding ?: return
        val ctx = context ?: return
        val palette = currentPalette ?: ExpressiveThemeManager.resolvePalette(ctx).also { currentPalette = it }
        val meta = StickerMetaStore.meta.value
        val active = StickerRepository.selectedFilter.value.let { f ->
            if (f is HomeFilter.Folder && meta.folders.none { it.id == f.id }) HomeFilter.All else f
        }

        b.chipGroup.removeAllViews()
        addChip(getString(R.string.filter_all), null, HomeFilter.All, active, palette)
        addChip(getString(R.string.filter_favorites), R.drawable.ic_star, HomeFilter.Favorites, active, palette)
        meta.folders.forEach { folder ->
            addChip(folder.name, R.drawable.ic_folder, HomeFilter.Folder(folder.id), active, palette) {
                showFolderMenu(folder)
            }
        }
        addNewFolderChip(palette)
    }

    private fun styledChip(label: String, iconRes: Int?, palette: ExpressivePalette): Chip {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        return Chip(ctx).apply {
            text = label
            isCheckedIconVisible = false
            chipStrokeWidth = 0f
            chipCornerRadius = 20f * density
            chipBackgroundColor = checkedStates(palette.secondaryContainer, palette.surfaceContainerLow)
            setTextColor(checkedStates(palette.onSecondaryContainer, palette.onSurface))
            if (iconRes != null) {
                chipIcon = ContextCompat.getDrawable(ctx, iconRes)
                isChipIconVisible = true
                chipIconTint = checkedStates(palette.onSecondaryContainer, palette.onSurfaceVariant)
            }
        }
    }

    private fun addChip(
        label: String,
        iconRes: Int?,
        target: HomeFilter,
        active: HomeFilter,
        palette: ExpressivePalette,
        onLongClick: (() -> Unit)? = null
    ) {
        val chip = styledChip(label, iconRes, palette).apply {
            isCheckable = true
            isChecked = target == active
            setOnClickListener { StickerRepository.setFilter(target) }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        }
        binding.chipGroup.addView(chip)
    }

    private fun addNewFolderChip(palette: ExpressivePalette) {
        val chip = styledChip(getString(R.string.folder_new), R.drawable.ic_create_folder, palette).apply {
            isCheckable = false
            setOnClickListener {
                val act = requireActivity() as BaseActivity
                act.showTextInputDialog(
                    title = getString(R.string.folder_new_title),
                    hint = getString(R.string.folder_name_hint),
                    confirmLabel = getString(R.string.ok)
                ) { name ->
                    StickerMetaStore.createFolder(act, name)?.let { StickerRepository.setFilter(HomeFilter.Folder(it.id)) }
                }
            }
        }
        binding.chipGroup.addView(chip)
    }

    private fun showFolderMenu(folder: StickerMetaStore.Folder) {
        val act = requireActivity() as BaseActivity
        val items = listOf(
            OptionItem(R.drawable.ic_description, getString(R.string.folder_rename)),
            OptionItem(R.drawable.ic_delete, getString(R.string.folder_delete))
        )
        act.showPaneDialog(folder.name, items) { which ->
            if (which == 0) {
                act.showTextInputDialog(
                    title = getString(R.string.folder_rename_title),
                    hint = getString(R.string.folder_name_hint),
                    initial = folder.name,
                    confirmLabel = getString(R.string.ok)
                ) { name -> StickerMetaStore.renameFolder(act, folder.id, name) }
            } else {
                MaterialAlertDialogBuilder(act)
                    .setTitle(act.boldTitle(getString(R.string.folder_delete)))
                    .setMessage(getString(R.string.folder_delete_message, folder.name))
                    .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                        if (StickerRepository.selectedFilter.value == HomeFilter.Folder(folder.id)) {
                            StickerRepository.setFilter(HomeFilter.All)
                        }
                        StickerMetaStore.deleteFolder(act, folder.id)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .create()
                    .showDestructiveDialog(act)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Multi-select
    // ---------------------------------------------------------------------------------------------

    fun isSelecting(): Boolean = selecting

    /** Enters selection mode with [file] already selected (from the long-press menu's "Select"). */
    fun startSelection(file: File) {
        selecting = true
        selected.clear()
        selected.add(file.name)
        applySelectionState()
    }

    fun exitSelection() {
        selecting = false
        selected.clear()
        applySelectionState()
    }

    private fun toggleSelection(file: File) {
        if (!selected.remove(file.name)) selected.add(file.name)
        if (selected.isEmpty()) exitSelection() else applySelectionState()
    }

    private fun dropSelectionOfMissing(items: List<StickerListItem>) {
        if (!selecting) return
        val present = items.filterIsInstance<StickerListItem.Sticker>().map { it.file.name }.toSet()
        if (selected.retainAll(present)) {
            if (selected.isEmpty()) exitSelection() else applySelectionState()
        }
    }

    private fun selectedFiles(): List<File> =
        selected.map { File(requireContext().filesDir, it) }.filter { it.exists() }

    private fun setupSelectionActions() {
        val b = binding
        b.btnSelectClose.setOnClickListener { exitSelection() }
        b.btnSelectAll.setOnClickListener {
            val all = stickerAdapter.currentList
                .filterIsInstance<StickerListItem.Sticker>()
                .map { it.file.name }
            selected.addAll(all)
            applySelectionState()
        }
        b.actFavorite.setOnClickListener {
            batch.toggleFavorite(selectedFiles())
            exitSelection()
        }
        b.actFolder.setOnClickListener {
            batch.moveToFolder(selectedFiles()) { exitSelection() }
        }
        b.actBorder.setOnClickListener {
            batch.addBorder(selectedFiles()) { exitSelection() }
        }
        b.actExport.setOnClickListener {
            batch.exportToGallery(selectedFiles()) { exitSelection() }
        }
        b.actDelete.setOnClickListener {
            batch.delete(selectedFiles()) { exitSelection() }
        }
    }

    private fun applySelectionState() {
        val b = _binding ?: return
        stickerAdapter.setSelection(selecting, selected.toSet())
        b.selectionBar.visibility = if (selecting) View.VISIBLE else View.GONE
        b.selectionActions.visibility = if (selecting) View.VISIBLE else View.GONE
        b.chipScroll.visibility = if (selecting) View.INVISIBLE else View.VISIBLE
        b.txtSelectionCount.text = getString(R.string.selection_count, selected.size)

        // The floating navbar gives way to the action bar while selecting.
        activity?.findViewById<View>(R.id.floatingBottomRow)?.visibility =
            if (selecting) View.INVISIBLE else View.VISIBLE

        if (selecting) {
            val density = resources.displayMetrics.density
            val navBottom = ViewCompat.getRootWindowInsets(b.root)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            (b.selectionActions.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                navBottom + (16 * density).toInt()
            b.selectionActions.requestLayout()
        }
    }

    override fun onDestroyView() {
        if (selecting) {
            activity?.findViewById<View>(R.id.floatingBottomRow)?.visibility = View.VISIBLE
        }
        super.onDestroyView()
        _binding = null
    }
}
