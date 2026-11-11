package dhp.thl.tpl.ndv.ui.recents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.PolygonDrawable
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.FragmentRecentsBinding
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Dedicated Recents Screen Fragment displaying chronological date-grouped sticker history
 * alongside a 12-sided scalloped M3 Expressive empty state.
 */
class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var recentsAdapter: StickerAdapter

    private val sunny12Sided = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        innerRadius = 0.84f,
        rounding = CornerRounding(0.18f)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listener = requireActivity() as StickerAdapter.StickerListener
        recentsAdapter = StickerAdapter(listener)

        val dm = resources.displayMetrics
        val columns = (dm.widthPixels / dm.density / 116f).toInt().coerceAtLeast(3)
        binding.recyclerRecents.layoutManager = GridLayoutManager(requireContext(), columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (recentsAdapter.getItemViewType(position) == StickerAdapter.VIEW_TYPE_HEADER) {
                        columns
                    } else {
                        1
                    }
                }
            }
        }
        binding.recyclerRecents.adapter = recentsAdapter
        binding.recyclerRecents.itemAnimator = null
        binding.recyclerRecents.enableStretchOverscroll()

        applyPalette(ExpressiveThemeManager.resolvePalette(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        StickerRepository.recentItems,
                        StickerRepository.isRecentsLoaded
                    ) { items, isLoaded -> items to isLoaded }
                        .collect { (items, isLoaded) ->
                            recentsAdapter.submitList(items)
                            val showEmpty = isLoaded && items.isEmpty()
                            val wasVisible = binding.emptyRecentsLayout.visibility == View.VISIBLE
                            binding.emptyRecentsLayout.visibility = if (showEmpty) View.VISIBLE else View.GONE
                            if (showEmpty && !wasVisible) {
                                ExpressiveMotion.springPopIn(binding.imgEmptyRecents, initialScale = 0.80f)
                            }
                        }
                }
                launch {
                    StickerRepository.bottomScrollPaddingPx.collect { bottomPad ->
                        if (bottomPad > 0) {
                            binding.recyclerRecents.updatePadding(bottom = bottomPad)
                        }
                    }
                }
            }
        }
    }

    fun applyPalette(palette: ExpressivePalette) {
        val b = _binding ?: return
        recentsAdapter.updatePalette(palette)

        b.imgEmptyRecents.background = PolygonDrawable(sunny12Sided, palette.secondaryContainer)
        b.imgEmptyRecents.setColorFilter(palette.onSecondaryContainer)
        b.txtEmptyRecentsTitle.setTextColor(palette.onSurface)
        b.txtEmptyRecentsSubtitle.setTextColor(palette.onSurfaceVariant)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
