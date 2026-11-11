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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dhp.thl.tpl.ndv.data.StickerRepository
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import dhp.thl.tpl.ndv.domain.MediaImportManager
import dhp.thl.tpl.ndv.domain.StickerActionsController
import dhp.thl.tpl.ndv.ui.BottomNavController
import dhp.thl.tpl.ndv.ui.MainThemeApplier
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Lightweight multi-screen host Activity coordinating:
 * - Child screen Fragments (`HomeFragment`, `RecentsFragment`, `OptionsFragment`)
 * - Material 3 Expressive header + borderless floating capsule navbar
 * - Shared media picker, trimmer, uCrop, and incoming share intents
 */
class MainActivity : BaseActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var themeApplier: MainThemeApplier
    private lateinit var bottomNavController: BottomNavController
    private lateinit var mediaImportManager: MediaImportManager
    private lateinit var stickerActionsController: StickerActionsController

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

        StickerRepository.refreshAll(this)

        themeApplier = MainThemeApplier(this, binding)
        bottomNavController = BottomNavController(this, binding)
        mediaImportManager = MediaImportManager(
            activity = this,
            videoTrimLauncherProvider = { videoTrimLauncher },
            onStickerAdded = { bottomNavController.getHomeFragment()?.scrollToTop() }
        )
        stickerActionsController = StickerActionsController(
            activity = this,
            binding = binding,
            isVideoUri = { uri -> mediaImportManager.isVideoUri(uri) },
            cropLauncherProvider = { cropImage },
            onStickerCreated = { bottomNavController.getHomeFragment()?.scrollToTop() }
        )

        bottomNavController.setup()

        val startTab = if (savedInstanceState == null) {
            R.id.nav_home
        } else {
            prefs.getInt("last_tab", R.id.nav_home)
        }
        bottomNavController.selectTab(startTab, animate = false)

        mediaImportManager.restoreSavedState(savedInstanceState?.getString("current_temp_video_path"))

        themeApplier.handleEdgeToEdge()
        themeApplier.updateStatusBar()
        themeApplier.applyExpressiveTheme(
            materialColorEnabled = materialColorEnabled,
            bottomNavController = bottomNavController
        )

        binding.addButton.setOnClickListener {
            launchMediaPicker()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    StickerRepository.homeCount,
                    StickerRepository.recentCount
                ) { homeCount, recentCount -> homeCount to recentCount }
                    .collect {
                        bottomNavController.updateHeaderTitleAndBadge()
                    }
            }
        }
    }

    fun launchMediaPicker() {
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    private fun configureSplashExitAnimation(splashScreen: androidx.core.splashscreen.SplashScreen) {
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = try { splashScreenView.iconView } catch (_: Exception) { null }
            if (iconView != null) {
                val zoomX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1.0f, 5.0f)
                val zoomY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1.0f, 5.0f)
                val alpha = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1.0f, 0.0f)

                zoomX.duration = 450L
                zoomY.duration = 450L
                alpha.duration = 450L

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
