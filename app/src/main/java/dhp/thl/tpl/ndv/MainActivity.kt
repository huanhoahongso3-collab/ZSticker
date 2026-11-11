package dhp.thl.tpl.ndv

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.domain.MediaImportManager
import dhp.thl.tpl.ndv.domain.StickerActionsController
import dhp.thl.tpl.ndv.ui.BottomNavController
import dhp.thl.tpl.ndv.ui.MainThemeApplier
import dhp.thl.tpl.ndv.ui.SettingsSectionController

/**
 * Slim coordinator Activity delegating UI, navigation, theme/Monet, settings,
 * media import, and per-sticker operations to dedicated single-responsibility controllers.
 */
class MainActivity : BaseActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter
    private lateinit var adapterRecents: StickerAdapter

    private lateinit var themeApplier: MainThemeApplier
    private lateinit var bottomNavController: BottomNavController
    private lateinit var settingsController: SettingsSectionController
    private lateinit var mediaImportManager: MediaImportManager
    private lateinit var stickerActionsController: StickerActionsController

    private val startFileActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("did_import", false) == true) {
            recreate()
        }
    }

    private val cropImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        stickerActionsController.handleCropResult(result)
    }

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            mediaImportManager.handleMediaUris(uris)
        }
    }

    private val videoTrimLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mediaImportManager.handleVideoTrimResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

        AppCompatDelegate.setDefaultNightMode(savedTheme)
        super.onCreate(savedInstanceState)

        configureSplashExitAnimation(splashScreen)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initControllers()
        setupStickerGrids(materialColorEnabled)

        bottomNavController.setup()
        settingsController.setup()

        val startTab = if (savedInstanceState == null) {
            R.id.nav_home
        } else {
            prefs.getInt("last_tab", R.id.nav_home)
        }
        bottomNavController.selectTab(startTab, animate = false)

        mediaImportManager.restoreSavedState(savedInstanceState?.getString("current_temp_video_path"))

        themeApplier.handleEdgeToEdge()
        themeApplier.updateStatusBar()
        bottomNavController.applyStyle(animateNewSelection = false)
        themeApplier.applyMainThemeAsync(
            materialColorEnabled = materialColorEnabled,
            bottomNavController = bottomNavController,
            adapter = adapter,
            adapterRecents = adapterRecents
        )

        binding.addButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    private fun initControllers() {
        themeApplier = MainThemeApplier(this, binding)

        bottomNavController = BottomNavController(
            activity = this,
            binding = binding,
            adapterProvider = { adapter },
            recentsAdapterProvider = { adapterRecents },
            onUpdateEmptyState = { isEmpty, msg -> themeApplier.updateEmptyState(isEmpty, msg) }
        )

        mediaImportManager = MediaImportManager(
            activity = this,
            adapterProvider = { adapter },
            onUpdateEmptyState = { isEmpty, msg -> themeApplier.updateEmptyState(isEmpty, msg) },
            videoTrimLauncherProvider = { videoTrimLauncher }
        )

        stickerActionsController = StickerActionsController(
            activity = this,
            binding = binding,
            adapterProvider = { adapter },
            recentsAdapterProvider = { adapterRecents },
            currentTabIdProvider = { bottomNavController.currentTabId },
            getThemeColor = { attr -> themeApplier.getThemeColor(attr) },
            isVideoUri = { uri -> mediaImportManager.isVideoUri(uri) },
            onUpdateEmptyState = { isEmpty, msg -> themeApplier.updateEmptyState(isEmpty, msg) },
            cropLauncherProvider = { cropImage }
        )

        settingsController = SettingsSectionController(
            activity = this,
            binding = binding,
            adapterProvider = { adapter },
            recentsAdapterProvider = { adapterRecents },
            onUpdateEmptyState = { isEmpty, msg -> themeApplier.updateEmptyState(isEmpty, msg) },
            onSyncRecentsAfterDeletion = { stickerActionsController.syncRecentsAfterDeletion() },
            onLaunchFileActivity = { intent -> startFileActivity.launch(intent) }
        )
    }

    private fun setupStickerGrids(materialColorEnabled: Boolean) {
        val displayMetrics = resources.displayMetrics
        val columns = (displayMetrics.widthPixels / displayMetrics.density / 120).toInt().coerceAtLeast(3)

        adapter = StickerAdapter(
            StickerAdapter.loadOrdered(this),
            this,
            materialColorEnabled = materialColorEnabled
        )
        binding.recycler.layoutManager = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(pos: Int): Int =
                    if (adapter.getItemViewType(pos) == 0) columns else 1
            }
        }
        binding.recycler.adapter = adapter

        adapterRecents = StickerAdapter(
            StickerAdapter.loadRecents(this),
            this,
            isRecents = true,
            materialColorEnabled = materialColorEnabled
        )
        binding.recyclerRecents.layoutManager = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(pos: Int): Int =
                    if (adapterRecents.getItemViewType(pos) == 0) columns else 1
            }
        }
        binding.recyclerRecents.adapter = adapterRecents

        if (materialColorEnabled) {
            binding.recycler.enableStretchOverscroll()
            binding.recyclerRecents.enableStretchOverscroll()
        }
    }

    private fun configureSplashExitAnimation(splashScreen: androidx.core.splashscreen.SplashScreen) {
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = try { splashScreenView.iconView } catch (_: Exception) { null }
            if (iconView != null) {
                val zoomX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1.0f, 5.0f)
                val zoomY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1.0f, 5.0f)
                val alpha = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1.0f, 0.0f)

                zoomX.duration = 500L
                zoomY.duration = 500L
                alpha.duration = 500L

                zoomX.interpolator = AnticipateInterpolator()
                zoomY.interpolator = AnticipateInterpolator()

                zoomX.doOnEnd { splashScreenView.remove() }

                zoomX.start()
                zoomY.start()
                alpha.start()
            } else {
                splashScreenView.remove()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mediaImportManager.checkPendingShareIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaImportManager.currentTempVideoFile?.absolutePath?.let {
            outState.putString("current_temp_video_path", it)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mediaImportManager.checkPendingShareIntent(intent)
        }
    }

    override fun onDestroy() {
        if (::stickerActionsController.isInitialized) {
            stickerActionsController.close()
        }
        super.onDestroy()
    }

    override fun onStickerClick(uri: Uri) {
        stickerActionsController.onStickerClick(uri)
    }

    override fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String?) {
        stickerActionsController.onStickerLongClick(uri, isRecent, entry)
    }
}
