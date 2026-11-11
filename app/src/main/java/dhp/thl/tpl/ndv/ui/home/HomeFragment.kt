package dhp.thl.tpl.ndv.ui.home

import android.content.res.ColorStateList
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
import dhp.thl.tpl.ndv.MainActivity
import dhp.thl.tpl.ndv.PolygonDrawable
import dhp.thl.tpl.ndv.StickerAdapter
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.FragmentHomeBinding
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Dedicated Home Screen Fragment displaying the user's sticker collection in a responsive
 * M3 Expressive squircle grid alongside an expressive 9-sided cookie empty state.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var stickerAdapter: StickerAdapter

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

        applyPalette(ExpressiveThemeManager.resolvePalette(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        StickerRepository.homeItems,
                        StickerRepository.isHomeLoaded
                    ) { items, isLoaded -> items to isLoaded }
                        .collect { (items, isLoaded) ->
                            stickerAdapter.submitList(items)
                            val showEmpty = isLoaded && items.isEmpty()
                            val wasVisible = binding.emptyHomeLayout.visibility == View.VISIBLE
                            binding.emptyHomeLayout.visibility = if (showEmpty) View.VISIBLE else View.GONE
                            if (showEmpty && !wasVisible) {
                                ExpressiveMotion.springPopIn(binding.imgEmptyHome, initialScale = 0.80f)
                            }
                        }
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
        val b = _binding ?: return
        stickerAdapter.updatePalette(palette)

        b.imgEmptyHome.background = PolygonDrawable(cookie9Sided, palette.secondaryContainer)
        b.imgEmptyHome.setColorFilter(palette.onSecondaryContainer)
        b.txtEmptyHomeTitle.setTextColor(palette.onSurface)
        b.txtEmptyHomeSubtitle.setTextColor(palette.onSurfaceVariant)

        b.btnEmptyAddSticker.backgroundTintList = ColorStateList.valueOf(palette.primary)
        b.btnEmptyAddSticker.setTextColor(palette.onPrimary)
        b.btnEmptyAddSticker.iconTint = ColorStateList.valueOf(palette.onPrimary)
    }

    fun scrollToTop() {
        _binding?.recyclerHome?.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
