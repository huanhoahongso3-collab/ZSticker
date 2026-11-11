package dhp.thl.tpl.ndv

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.animation.ObjectAnimator
import android.view.animation.AnticipateInterpolator
import androidx.core.animation.doOnEnd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively
import com.kieronquinn.monetcompat.extensions.views.applyMonet
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bumptech.glide.Glide
import dhp.thl.tpl.ndv.gif.AnimatedImageConverter
import dhp.thl.tpl.ndv.gif.VideoToGifConverter
import com.gowtham.library.utils.TrimVideo
import com.gowtham.library.utils.TrimType
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.nio.ByteBuffer
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.webkit.MimeTypeMap
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.RadioButton
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.star

class MainActivity : BaseActivity(), StickerAdapter.StickerListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter
    private lateinit var adapterRecents: StickerAdapter

    // Open-source on-device background remover (Google MediaPipe Tasks Vision)
    private val mediaPipeBackgroundRemover by lazy { MediaPipeBackgroundRemover(this) }

    
    private val Cookie9Sided = RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.92f,
        rounding = CornerRounding(0.15f)
    )

    private val startFileActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("did_import", false) == true) {
            recreate()
        }
    }

    private var materialColorIconId: Int = -1
    private var isOperationCancelled = false

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

        // Ensure theme is set BEFORE super.onCreate
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        
        super.onCreate(savedInstanceState)
        
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = try { splashScreenView.iconView } catch (e: Exception) { null }
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStickerList()
        setupNavigation()
        setupInfoSection()

        val startTab = if (savedInstanceState == null) R.id.nav_home else prefs.getInt("last_tab", R.id.nav_home)
        binding.bottomNavigation.selectedItemId = startTab
        updateLayoutVisibility(startTab)

        savedInstanceState?.getString("current_temp_video_path")?.let { path ->
            val f = File(path)
            if (f.exists()) currentTempVideoFile = f
        }
        handleEdgeToEdge()
        updateStatusBar()

        lifecycleScope.launch {
            if (materialColorEnabled) {
                monet.awaitMonetReady()
                binding.root.applyMonetRecursively()
                binding.bottomNavigation.applyMonet()
                
                val monetInstance = MonetCompat.getInstance()
                val primary = monetInstance.getAccentColor(this@MainActivity)
                val surface = monetInstance.getBackgroundColor(this@MainActivity)
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val headColor = primary
                val cardColor = if (isDark) Color.BLACK else Color.parseColor("#F5F5F5") 

                binding.root.setBackgroundColor(surface)
                binding.appBarLayout.setBackgroundColor(surface)
                binding.toolbar.setBackgroundColor(surface)
                binding.bottomNavigation.setBackgroundColor(Color.TRANSPARENT)
                val floatingPillColor = ColorUtils.blendARGB(surface, primary, if (isDark) 0.16f else 0.10f)
                binding.floatingNavCard.setCardBackgroundColor(floatingPillColor)
                binding.floatingNavCard.strokeColor = ColorUtils.setAlphaComponent(primary, if (isDark) 64 else 40)
                
                binding.sectionSettingsHeader.setTextColor(headColor)
                binding.sectionGeneralHeader.setTextColor(headColor)
                binding.cardSettings.setCardBackgroundColor(cardColor)
                binding.cardGeneral.setCardBackgroundColor(cardColor)
                
                // Match FAB to content/bottom nav theme in dark mode
                val fabBg = primary
                val fabIcon = if (isDark) Color.BLACK else monetInstance.getBackgroundColor(this@MainActivity)
                
                binding.addButton.backgroundTintList = android.content.res.ColorStateList.valueOf(fabBg)
                binding.addButton.imageTintList = android.content.res.ColorStateList.valueOf(fabIcon)

                // Tint all icons in options pane
                listOf(
                    binding.imgTheme, binding.imgMaterialColor, binding.imgLanguage,
                    binding.imgFiles, binding.imgOnlineRemoveBg,
                    binding.imgVersion, binding.imgRepo, binding.imgLicense, binding.imgOpenSource
                ).forEach { icon ->
                    icon.setColorFilter(primary)
                }

                // Explicitly set Removal items to red
                val red = Color.parseColor("#FF3b30")
                val redAlpha = ColorUtils.setAlphaComponent(red, 30) // Adjusted to 30 as per previous polish
                
                binding.imgRemoveRecent.setColorFilter(red)
                binding.imgRemoveRecent.backgroundTintList = android.content.res.ColorStateList.valueOf(redAlpha)
                binding.txtRemoveRecent.setTextColor(red)
                
                binding.imgRemoveAll.setColorFilter(red)
                binding.imgRemoveAll.backgroundTintList = android.content.res.ColorStateList.valueOf(redAlpha)
                binding.txtRemoveAll.setTextColor(red)
                
                // Fix Switch tinting
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val thumbColors = intArrayOf(primary, Color.WHITE)
                val trackColors = intArrayOf(ColorUtils.setAlphaComponent(primary, 128), ColorUtils.setAlphaComponent(Color.LTGRAY, 128))
                
                binding.switchMaterialColor.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
                binding.switchMaterialColor.trackTintList = android.content.res.ColorStateList(states, trackColors)
                binding.switchMaterialColor.thumbIconTintList = android.content.res.ColorStateList.valueOf(
                    if (isDark) monetInstance.getBackgroundColor(this@MainActivity) else Color.WHITE
                )

                binding.switchOnlineRemoveBg.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
                binding.switchOnlineRemoveBg.trackTintList = android.content.res.ColorStateList(states, trackColors)
                binding.switchOnlineRemoveBg.thumbIconTintList = android.content.res.ColorStateList.valueOf(
                    if (isDark) monetInstance.getBackgroundColor(this@MainActivity) else Color.WHITE
                )

                binding.loadingIndicator.setIndicatorColor(primary)

                // Refresh empty state with correct localized colors and data
                val currentTab = binding.bottomNavigation.selectedItemId
                if (currentTab == R.id.nav_home) {
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                } else if (currentTab == R.id.nav_recents) {
                    updateEmptyState(adapterRecents.itemCount == 0, getString(R.string.no_history_found))
                }
            } else {
                // Even if Material Color is off, the removal buttons should be red
                val red = Color.parseColor("#FF3b30")
                val redAlpha = ColorUtils.setAlphaComponent(red, 30)
                
                binding.imgRemoveRecent.setColorFilter(red)
                binding.imgRemoveRecent.backgroundTintList = android.content.res.ColorStateList.valueOf(redAlpha)
                binding.txtRemoveRecent.setTextColor(red)
                
                binding.imgRemoveAll.setColorFilter(red)
                binding.imgRemoveAll.backgroundTintList = android.content.res.ColorStateList.valueOf(redAlpha)
                binding.txtRemoveAll.setTextColor(red)
                
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (!isDark) {
                    val lightOrange = getColor(R.color.light_orange_background)
                    binding.root.setBackgroundColor(lightOrange)
                    binding.appBarLayout.setBackgroundColor(lightOrange)
                    binding.toolbar.setBackgroundColor(lightOrange)
                    
                    // Cards should be white against the light orange background for M3 look
                    binding.cardSettings.setCardBackgroundColor(Color.WHITE)
                    binding.cardGeneral.setCardBackgroundColor(Color.WHITE)
                }
            }
        }



        binding.addButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    private fun updateStatusBar() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightMode = nightMode == Configuration.UI_MODE_NIGHT_NO
        
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = isLightMode
        windowInsetsController.isAppearanceLightNavigationBars = isLightMode
    }

    override fun onResume() {
        super.onResume()
        checkPendingShareIntent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentTempVideoFile?.absolutePath?.let {
            outState.putString("current_temp_video_path", it)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            checkPendingShareIntent()
        }
    }

    private fun checkPendingShareIntent() {
        val currentIntent = intent ?: return
        if (isFinishing || isDestroyed) return
        if (currentIntent.getBooleanExtra("zsticker_share_handled", false)) return
        val action = currentIntent.action
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE || action == Intent.ACTION_VIEW) {
            currentIntent.putExtra("zsticker_share_handled", true)
            handleIncomingShare(currentIntent)
        }
    }

    override fun onDestroy() {
        mediaPipeBackgroundRemover.close()
        super.onDestroy()
    }

    private fun setupInfoSection() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

        updateThemeText(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        binding.itemTheme.setOnClickListener {
            val themes = listOf(
                OptionItem(R.drawable.ic_theme_light, getString(R.string.theme_light)),
                OptionItem(R.drawable.ic_theme_dark, getString(R.string.theme_dark)),
                OptionItem(R.drawable.ic_settings_system, getString(R.string.theme_system))
            )
            
            val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val selectedIndex = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            showPaneDialog(
                getString(R.string.info_theme_title),
                themes,
                selectedIndex
            ) { which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                applyAndSaveTheme(prefs, newMode)
            }
        }

        val currentLang = prefs.getString("lang", "system") ?: "system"
        binding.txtCurrentLanguage.text = when (currentLang) {
            "en" -> getString(R.string.lang_en)
            "vi" -> getString(R.string.lang_vi)
            "ru" -> getString(R.string.lang_ru)
            "zh" -> getString(R.string.lang_zh)
            else -> getString(R.string.lang_system)
        }

        binding.itemLanguage.setOnClickListener {
            val langs = listOf(
                OptionItem(R.drawable.ic_flag_en, getString(R.string.lang_en)),
                OptionItem(R.drawable.ic_flag_vi, getString(R.string.lang_vi)),
                OptionItem(R.drawable.ic_flag_ru, getString(R.string.lang_ru)),
                OptionItem(R.drawable.ic_flag_zh, getString(R.string.lang_zh)),
                OptionItem(R.drawable.ic_settings_system, getString(R.string.lang_system))
            )

            val langSelection = prefs.getString("lang", "system") ?: "system"
            val selectedIndex = when (langSelection) {
                "en" -> 0
                "vi" -> 1
                "ru" -> 2
                "zh" -> 3
                else -> 4
            }
            
            showPaneDialog(
                getString(R.string.info_language_title),
                langs,
                selectedIndex
            ) { which ->
                val langCode = when (which) {
                    0 -> "en"
                    1 -> "vi"
                    2 -> "ru"
                    3 -> "zh"
                    else -> "system"
                }
                if (currentLang != langCode) {
                    prefs.edit().putString("lang", langCode).apply()
                    recreate()
                }
            }
        }

        binding.switchMaterialColor.isChecked = materialColorEnabled
        binding.switchMaterialColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("material_color_enabled", isChecked).apply()
            recreate()
        }

        binding.switchOnlineRemoveBg.isChecked = prefs.getBoolean("removebg_online_mode", false)
        binding.switchOnlineRemoveBg.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("removebg_online_mode", isChecked).apply()
        }

        setupSecondaryInfo()
    }

    private fun applyAndSaveTheme(prefs: SharedPreferences, mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        updateThemeText(mode)
        recreate()
    }

    private fun updateThemeText(mode: Int) {
        binding.txtCurrentTheme.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    private fun setupSecondaryInfo() {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = pInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") pInfo.versionCode.toLong()
            }
            binding.txtVersion.text = "$versionName ($versionCode)"
        } catch (e: Exception) {
            binding.txtVersion.text = "1.0.0 (1)"
        }

        var versionClicks = 0
        binding.itemVersion.setOnClickListener {
            versionClicks++
            if (versionClicks >= 10) {
                versionClicks = 0
                startActivity(Intent(this, EasterEggActivity::class.java))
            }
        }



        binding.itemRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker")))
        }
        binding.itemLicense.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
        }
        binding.itemFiles.setOnClickListener {
            startActivity(Intent(this, FileActivity::class.java))
        }
        binding.itemRemoveRecent.setOnClickListener { confirmRemoveRecent() }
        binding.itemRemoveAll.setOnClickListener { confirmDeleteAll() }
        binding.itemOpenSource.setOnClickListener { showOpenSourceLicenses() }
    }
    
    private fun showOpenSourceLicenses() {
        startActivity(Intent(this, LicenseActivity::class.java))
    }

    private fun confirmRemoveRecent() {
        MaterialAlertDialogBuilder(this)
            .setTitle(boldTitle(getString(R.string.info_remove_recent_confirm_title)))
            .setMessage(getString(R.string.info_remove_recent_confirm_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> removeRecentUsage() }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().showDestructiveDialog(this)
    }

    private fun removeRecentUsage() {
        val prefs = getSharedPreferences("recents", MODE_PRIVATE)
        val history = prefs.getString("list", "") ?: ""
        if (history.isEmpty()) {
            ToastUtils.showToast(this, getString(R.string.no_history_found))
            return
        }
        prefs.edit().remove("list").apply()
        adapterRecents.refreshData(this)
        ToastUtils.showToast(this, getString(R.string.success))
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(boldTitle(getString(R.string.info_remove_all_confirm_title)))
            .setMessage(getString(R.string.info_remove_all_confirm_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteAllStickers() }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().showDestructiveDialog(this)
    }

    private fun deleteAllStickers() {
        val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") } ?: emptyArray()
        if (stickerFiles.isEmpty()) {
            ToastUtils.showToast(this, getString(R.string.no_stickers_found))
            return
        }
        var successCount = 0
        stickerFiles.forEach { if (it.delete()) successCount++ }
        
        adapter.refreshData(this)
        syncRecentsAfterDeletion()
        updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
        ToastUtils.showToast(this, if (successCount > 0) getString(R.string.success) else getString(R.string.failed))
    }

    private fun syncRecentsAfterDeletion() {
        val prefs = getSharedPreferences("recents", MODE_PRIVATE)
        val list = prefs.getString("list", "")?.split(",")?.toMutableList() ?: mutableListOf()
        val filtered = list.filter { item ->
            val fileName = item.substringBefore(":")
            File(filesDir, fileName).exists()
        }.joinToString(",")
        prefs.edit().putString("list", filtered).apply()
        adapterRecents.refreshData(this)
    }

    private fun isVideoUri(uri: Uri, hintMimeType: String? = null): Boolean {
        val mimeType = try { contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        if (hintMimeType != null && hintMimeType.startsWith("video/", ignoreCase = true)) return true
        val uriStr = Uri.decode(uri.toString()).lowercase()
        val path = (uri.path ?: uriStr).lowercase()
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
            path.endsWith(".3gp") || path.endsWith(".mov") || path.endsWith(".avi") || path.endsWith(".m4v") ||
            uriStr.contains("/video/") || uriStr.contains("video:")) return true

        // Check container magic bytes (MP4/MOV/3GP ftyp, WebM/MKV EBML, AVI RIFF)
        try {
            openUriInputStream(uri)?.use { stream ->
                val header = ByteArray(16)
                val read = stream.read(header)
                if (read >= 12) {
                    val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
                    if (boxType == "ftyp" || boxType == "moov" || boxType == "wide" || boxType == "mdat") {
                        return true
                    }
                    if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
                    ) {
                        return true
                    }
                    val riff = String(header, 0, 4, Charsets.ISO_8859_1)
                    val avi = String(header, 8, 4, Charsets.ISO_8859_1)
                    if (riff == "RIFF" && avi == "AVI ") {
                        return true
                    }
                }
            }
        } catch (e: Exception) {}

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                retriever.setDataSource(uri.path ?: uri.toString())
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
            } else {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use {
                        retriever.setDataSource(it.fileDescriptor)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
                    }
                } else {
                    retriever.setDataSource(this, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
                }
            }
        } catch (e: Exception) {
            false
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun openUriInputStream(srcUri: Uri): java.io.InputStream? {
        try {
            if (srcUri.scheme == "file" || srcUri.scheme == null) {
                val file = File(srcUri.path ?: srcUri.toString())
                if (file.exists()) return file.inputStream()
            }
        } catch (e: Exception) {}
        try {
            contentResolver.openInputStream(srcUri)?.let { return it }
        } catch (e: Exception) {}
        try {
            contentResolver.openAssetFileDescriptor(srcUri, "r")?.createInputStream()?.let { return it }
        } catch (e: Exception) {}
        return null
    }

    /**
     * Prepares a local video file in cacheDir so that the video trimmer and converter
     * have fast, direct, guaranteed file access without codec or remuxing issues.
     */
    private fun prepareVideoFile(srcUri: Uri): File? {
        return try {
            val validVideoExts = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
            val rawExt = try {
                val mime = contentResolver.getType(srcUri)
                if (mime != null) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase() else null
            } catch (e: Exception) { null }
            val ext = if (rawExt != null && rawExt in validVideoExts) rawExt else "mp4"

            val tempFile = File(cacheDir, "trim_input_${System.currentTimeMillis()}.$ext")
            openUriInputStream(srcUri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private var currentTempVideoFile: File? = null

    private fun handleMediaUris(uris: List<Uri>, intentMimeType: String? = null) {
        if (uris.isEmpty()) return

        thread {
            val videoMask = uris.map { uri ->
                isVideoUri(uri, if (uris.size == 1) intentMimeType else null)
            }
            val videoCount = videoMask.count { it }

            // Enforce: Only 1 video can be imported at a time (in picker and via intent)
            if (videoCount > 0 && uris.size > 1) {
                runOnUiThread {
                    ToastUtils.showToast(this, getString(R.string.only_one_video_allowed))
                }
                return@thread
            }

            if (videoCount == 1) {
                val videoUri = uris.first()
                val progressDialog = ProgressDialog.newInstance(getString(R.string.converting_video))
                runOnUiThread {
                    progressDialog.showSafe(supportFragmentManager, "prepare_video")
                }
                val tempVideoFile = prepareVideoFile(videoUri)
                runOnUiThread {
                    progressDialog.dismissSafe()
                    if (tempVideoFile != null) {
                        launchVideoTrimmer(tempVideoFile)
                    } else {
                        ToastUtils.showToast(this, getString(R.string.failed))
                    }
                }
                return@thread
            }

            val progressDialog = ProgressDialog.newInstance(getString(R.string.importing_sticker))
            runOnUiThread {
                progressDialog.showSafe(supportFragmentManager, "import_media")
            }

            var hasSuccess = false
            var hasUnsupported = false
            var hasFailed = false

            for ((index, uri) in uris.withIndex()) {
                val mimeType = try { contentResolver.getType(uri) ?: intentMimeType ?: "" } catch (e: Exception) { "" }
                val animType = AnimatedImageConverter.detectAnimationType(this, uri)
                if (animType != AnimatedImageConverter.AnimationType.NONE) {
                    runOnUiThread { progressDialog.updateMessage(getString(R.string.converting_animated_sticker)) }
                    val destFile = File(filesDir, "zsticker_${System.currentTimeMillis()}.gif")
                    val success = AnimatedImageConverter.convertToZaloGif(this, uri, destFile) { p ->
                        runOnUiThread { progressDialog.updateProgress((index + p) / uris.size) }
                    }
                    if (success) hasSuccess = true else hasFailed = true
                } else if (mimeType.startsWith("image/") || mimeType.isEmpty() || mimeType == "*/*" || mimeType == "application/octet-stream") {
                    runOnUiThread { progressDialog.updateMessage(getString(R.string.importing_sticker)) }
                    try {
                        openUriInputStream(uri)?.use { input ->
                            val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out ->
                                if (input.copyTo(out) > 0) hasSuccess = true else {
                                    file.delete()
                                    hasFailed = true
                                }
                            }
                        } ?: run { hasFailed = true }
                    } catch (e: Exception) {
                        hasFailed = true
                    }
                } else {
                    hasUnsupported = true
                }
                runOnUiThread { progressDialog.updateProgress((index + 1).toFloat() / uris.size) }
            }

            runOnUiThread {
                progressDialog.dismissSafe()
                if (hasSuccess) {
                    adapter.refreshData(this)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    ToastUtils.showToast(this, getString(R.string.success))
                }
                if (hasUnsupported) ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
                else if (hasFailed) ToastUtils.showToast(this, getString(R.string.failed))
            }
        }
    }

    private fun launchVideoTrimmer(videoFile: File) {
        try {
            currentTempVideoFile = videoFile
            val lang = getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "system") ?: "system"
            val builder = TrimVideo.activity(Uri.fromFile(videoFile).toString())
                .setTrimType(TrimType.FIXED_DURATION)
                .setFixedDuration(5L)
                .setTitle(getString(R.string.trim_video_title))
                .disableCompression()
                .setHideSeekBar(false)
            if (lang != "system") {
                builder.setLocal(lang)
            }
            builder.start(this, videoTrimLauncher)
        } catch (e: Exception) {
            e.printStackTrace()
            videoFile.delete()
            currentTempVideoFile = null
            ToastUtils.showToast(this, getString(R.string.failed))
        }
    }

    private fun processVideoFile(
        videoUri: Uri,
        startMs: Long = 0L,
        maxDurationMs: Long = VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS,
        isTempFile: Boolean = false,
        extraCleanupFile: File? = null
    ) {
        val progressDialog = ProgressDialog.newInstance(getString(R.string.converting_video))
        progressDialog.showSafe(supportFragmentManager, "convert_video")

        thread {
            val destFile = File(filesDir, "zsticker_${System.currentTimeMillis()}.gif")
            val success = VideoToGifConverter.convertVideoToGif(
                this,
                videoUri,
                destFile,
                startMs = startMs,
                maxDurationMs = maxDurationMs,
                fps = 10
            ) { progress ->
                runOnUiThread { progressDialog.updateProgress(progress) }
            }
            if (isTempFile) {
                try {
                    videoUri.path?.let { File(it).delete() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                extraCleanupFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runOnUiThread {
                progressDialog.dismissSafe()
                if (success) {
                    adapter.refreshData(this)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    ToastUtils.showToast(this, getString(R.string.success))
                } else {
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }


    private fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                if (uris.isEmpty()) {
                    intent.data?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
                if (uris.isEmpty()) {
                    intent.getStringExtra(Intent.EXTRA_STREAM)?.let { str ->
                        if (str.isNotBlank()) uris.add(Uri.parse(str))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
                if (uris.isEmpty()) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
            }
        }

        if (uris.isNotEmpty()) {
            handleMediaUris(uris.toList(), intent.type)
        }
    }

    private fun removeBackground(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
            return
        }

        // UI Setup for loading state
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, 153)) // 60% alpha

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        binding.loadingIndicator.setIndicatorColor(primary)

        binding.progressBar.visibility = View.VISIBLE

        thread {
            var isSuccess = false
            var resultUri: Uri? = null
            try {
                // Decode, orient, and downsample to <= 512px safely via Glide
                val resizedBitmap = ImageUtils.loadSampledBitmapWithGlide(this, uri, 512, 512)
                    ?: throw Exception("Glide failed to decode source URI")
                val segmentedBitmap = mediaPipeBackgroundRemover.removeBackground(resizedBitmap)
                resizedBitmap.recycle()

                if (segmentedBitmap != null) {
                    val croppedBitmap = ImageUtils.cropTransparent(segmentedBitmap)
                    val file = File(filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (croppedBitmap != segmentedBitmap) segmentedBitmap.recycle()
                    croppedBitmap.recycle()
                    resultUri = Uri.fromFile(file)
                    isSuccess = true
                }
            } catch (e: Exception) {
                isSuccess = false
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    adapter.refreshData(this@MainActivity)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_completed))
                } else {
                    // Displays failed message for all errors (including connection issues)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_failed))
                }
            }
        }
    }

    private fun checkAndShowBackgroundRemovalWarning(uri: Uri) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("dont_show_rb_warning", false)) { removeBackgroundOnline(uri); return }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_warning, null)
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon_warning)
        val checkBox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cb_dont_show_again)
        titleView.text = boldTitle(getString(R.string.rb_warning_title))
        messageView.text = getString(R.string.rb_warning_message)
        iconView.setImageResource(R.drawable.ic_remove_bg)
        val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        iconView.setColorFilter(primary); messageView.setLinkTextColor(primary)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnContinue = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue)
        btnCancel.setTextColor(primary); checkBox.buttonTintList = android.content.res.ColorStateList.valueOf(primary)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        btnContinue.setTextColor(if (isDark) Color.BLACK else Color.WHITE)
        btnContinue.backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnContinue.setOnClickListener { if (checkBox.isChecked) prefs.edit().putBoolean("dont_show_rb_warning", true).apply()
            dialog.dismiss(); removeBackgroundOnline(uri) }
        dialog.showMonetDialog(this)
    }

    private fun removeBackgroundOnline(uri: Uri) {
        // UI Setup for loading state
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, 153)) // 60% alpha

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        binding.loadingIndicator.setIndicatorColor(primary)

        binding.progressBar.visibility = View.VISIBLE

        thread {
            var isSuccess = false
            var resultUri: Uri? = null
            val tempResponseFile = File(cacheDir, "rb_net_${System.currentTimeMillis()}.png")
            try {
                // Decode, orient, and downsample to <= 512px via Glide to save RAM & bandwidth
                val resizedBitmap = ImageUtils.loadSampledBitmapWithGlide(this, uri, 512, 512)
                    ?: throw Exception("Glide failed to decode source URI")

                // Uses the stable BRIA RMBG 1.4 proxy endpoint
                val url = URL("https://bria14proxy.vercel.app/api/nobg")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                // Stream the resized image data to the proxy
                connection.outputStream.use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                resizedBitmap.recycle()

                // Handle successful response (Engine 1.4) using Glide instead of raw stream decoding
                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempResponseFile).use { tempOut ->
                            input.copyTo(tempOut)
                        }
                    }
                    if (tempResponseFile.exists() && tempResponseFile.length() > 0) {
                        val downloadedBitmap = ImageUtils.loadSampledBitmapWithGlide(this, tempResponseFile, 512, 512)
                        if (downloadedBitmap != null) {
                            val file = File(filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                            val croppedBitmap = ImageUtils.cropTransparent(downloadedBitmap)
                            FileOutputStream(file).use { out ->
                                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            if (croppedBitmap != downloadedBitmap) downloadedBitmap.recycle()
                            croppedBitmap.recycle()
                            resultUri = Uri.fromFile(file)
                            isSuccess = true
                        }
                    }
                }
            } catch (e: Exception) {
                // If no internet or API error occurs, isSuccess remains false
                isSuccess = false
            } finally {
                try {
                    if (tempResponseFile.exists()) tempResponseFile.delete()
                } catch (_: Exception) {
                }
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    adapter.refreshData(this@MainActivity)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_completed))
                } else {
                    // Displays failed message for all errors (including connection issues)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_failed))
                }
            }
        }
    }


    private fun handleEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = topInsets.left, top = topInsets.top, right = topInsets.right)
            insets
        }
        // Prevent MDC NavigationBarView from adding internal bottom system-bar padding inside our 64dp floating pill
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, _ ->
            view.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.floatingBottomContainer) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            val density = resources.displayMetrics.density
            val sideMarginPx = (16 * density).toInt()
            val bottomMarginPx = (16 * density).toInt()
            val scrollBottomPaddingPx = (104 * density).toInt() + navInsets.bottom

            view.updatePadding(
                left = sideMarginPx + navInsets.left,
                right = sideMarginPx + navInsets.right,
                bottom = bottomMarginPx + navInsets.bottom
            )

            binding.recycler.updatePadding(bottom = scrollBottomPaddingPx)
            binding.recyclerRecents.updatePadding(bottom = scrollBottomPaddingPx)
            binding.infoLayout.updatePadding(bottom = scrollBottomPaddingPx)
            insets
        }
    }

    private fun updateLayoutVisibility(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> {
                binding.toolbar.title = boldTitle(getString(R.string.nav_home))
                binding.recycler.visibility = View.VISIBLE
                binding.recyclerRecents.visibility = View.GONE
                binding.infoLayout.visibility = View.GONE
                binding.addButton.show()
                updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
            }
            R.id.nav_recents -> {
                binding.toolbar.title = boldTitle(getString(R.string.nav_recents))
                binding.recycler.visibility = View.GONE
                binding.recyclerRecents.visibility = View.VISIBLE
                binding.infoLayout.visibility = View.GONE
                binding.addButton.hide()
                adapterRecents.refreshData(this)
                updateEmptyState(adapterRecents.itemCount == 0, getString(R.string.no_history_found))
            }
            R.id.nav_options -> {
                binding.toolbar.title = boldTitle(getString(R.string.nav_options))
                binding.recycler.visibility = View.GONE
                binding.recyclerRecents.visibility = View.GONE
                binding.infoLayout.visibility = View.VISIBLE
                binding.addButton.hide()
                binding.emptyLayout.visibility = View.GONE
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, message: String) {
        binding.emptyLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.txtEmpty.text = message
        
        if (isEmpty) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            val cookieBg = if (materialColorEnabled) {
                MonetCompat.getInstance().getAccentColor(this)
            } else {
                getColor(R.color.orange_primary)
            }
            
            val iconTint = if (materialColorEnabled) {
                if (isDark) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }
            
            // 9-sided cookie background follows material/fallback color exactly like FAB
            binding.imgEmpty.background = PolygonDrawable(Cookie9Sided, cookieBg)
            binding.imgEmpty.setColorFilter(iconTint)

            if (message == getString(R.string.no_history_found)) {
                binding.imgEmpty.setImageResource(R.drawable.ic_empty_history)
            } else {
                binding.imgEmpty.setImageResource(R.drawable.ic_empty_state)
            }

            val padding = (17 * resources.displayMetrics.density).toInt()
            binding.imgEmpty.setPadding(padding, padding, padding, padding)
        }
    }

    private fun setupStickerList() {
        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        
        val displayMetrics = resources.displayMetrics
        val columns = (displayMetrics.widthPixels / displayMetrics.density / 120).toInt().coerceAtLeast(3)

        adapter = StickerAdapter(StickerAdapter.loadOrdered(this), this, materialColorEnabled = materialColorEnabled)
        binding.recycler.layoutManager = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(pos: Int): Int = if (adapter.getItemViewType(pos) == 0) columns else 1
            }
        }
        binding.recycler.adapter = adapter

        adapterRecents = StickerAdapter(StickerAdapter.loadRecents(this), this, isRecents = true, materialColorEnabled = materialColorEnabled)
        binding.recyclerRecents.layoutManager = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(pos: Int): Int = if (adapterRecents.getItemViewType(pos) == 0) columns else 1
            }
        }
        binding.recyclerRecents.adapter = adapterRecents

        if (materialColorEnabled) {
            binding.recycler.enableStretchOverscroll()
            binding.recyclerRecents.enableStretchOverscroll()
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("last_tab", item.itemId).apply()
            updateLayoutVisibility(item.itemId)
            true
        }
        val menu = binding.bottomNavigation.menu
        for (i in 0 until menu.size()) {
            val view = binding.bottomNavigation.findViewById<View>(menu.getItem(i).itemId)
            view?.setOnLongClickListener { true }
            view?.let { androidx.appcompat.widget.TooltipCompat.setTooltipText(it, null) }
        }
    }

    override fun onStickerClick(uri: Uri) {
        try {
            val file = File(filesDir, uri.lastPathSegment ?: "")
            if (!file.exists()) return

            val isAnimated = file.name.endsWith(".gif", ignoreCase = true)

            // Resize for Zalo (max width 512px)
            val shareFile = if (isAnimated) {
                ImageUtils.getZaloAnimatedSticker(this, file)
            } else {
                ImageUtils.getZaloSticker(this, file)
            }
            
            val contentUri = FileProvider.getUriForFile(this, "$packageName.provider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isAnimated) "image/gif" else "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("is_sticker", true)
                putExtra("type", 3)
                setClassName("com.zing.zalo", "com.zing.zalo.ui.TempShareViaActivity")
            }
            startActivity(intent)
            addToRecents(file.name)
        } catch (e: Exception) { 
            e.printStackTrace()
            ToastUtils.showToast(this, getString(R.string.zalo_share_failed)) 
        }
    }

    private fun addToRecents(fileName: String) {
        val prefs = getSharedPreferences("recents", MODE_PRIVATE)
        val list = prefs.getString("list", "")?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
        list.add(0, "$fileName:${System.currentTimeMillis()}")
        if (list.size > 100) list.removeAt(100)
        prefs.edit().putString("list", list.joinToString(",")).apply()
        if (binding.bottomNavigation.selectedItemId == R.id.nav_recents) adapterRecents.refreshData(this)
    }

    override fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String?) {
        val fileName = uri.lastPathSegment ?: ""
        val isAnimated = fileName.endsWith(".gif", ignoreCase = true) || isVideoUri(uri)
        val title = getString(if (isRecent) R.string.recent_options_title else R.string.sticker_options_title)
        val options = mutableListOf<OptionItem>()
        // Regular single export/share
        options.add(OptionItem(R.drawable.ic_export_gallery, getString(R.string.export)))
        
        if (!isRecent && !isAnimated) {
            options.add(OptionItem(R.drawable.ic_remove_bg, getString(R.string.remove_bg)))
            options.add(OptionItem(R.drawable.ic_border_sticker, getString(R.string.border_sticker)))
            options.add(OptionItem(R.drawable.ic_crop, getString(R.string.crop)))
        }
        
        options.add(OptionItem(R.drawable.ic_view_full, getString(R.string.view_full_sticker)))
        
        if (!isRecent) {
            options.add(OptionItem(R.drawable.ic_delete, getString(R.string.delete)))
        } else {
            options.add(OptionItem(R.drawable.ic_delete, getString(R.string.delete_history)))
        }

        showPaneDialog(title, options) { which ->
            when (options[which].text) {
                getString(R.string.export) -> exportSingleSticker(uri)
                getString(R.string.remove_bg) -> {
                    val onlineModeEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("removebg_online_mode", false)
                    if (onlineModeEnabled) checkAndShowBackgroundRemovalWarning(uri) else removeBackground(uri)
                }
                getString(R.string.border_sticker) -> stickify(uri)
                getString(R.string.crop) -> startCrop(uri)
                getString(R.string.view_full_sticker) -> viewFullSticker(uri)
                getString(R.string.delete) -> deleteSticker(uri)
                getString(R.string.delete_history) -> entry?.let { removeFromRecents(it) }
            }
        }

    }

    private fun showPaneDialog(title: String, items: List<OptionItem>, selectedIndex: Int = -1, onItemClick: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_list_pane, null)
        val container = dialogView.findViewById<ViewGroup>(R.id.dialog_item_container)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(boldTitle(title)).setView(dialogView).setNegativeButton(getString(R.string.cancel), null).create()

        items.forEachIndexed { index, item ->
            val itemView = if (selectedIndex != -1) ThemeAdapter(this, items, selectedIndex).getView(index, null, container) else OptionAdapter(this, items).getView(index, null, container)
            val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
            val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
            itemView.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30)), itemView.background, null)
            itemView.setOnClickListener { onItemClick(index); dialog.dismiss() }
            container.addView(itemView)
            if (index < items.size - 1) container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, android.util.TypedValue(), true)
                setBackgroundColor(android.util.TypedValue().data)
                alpha = 0.1f
            })
        }
        dialog.showMonetDialog(this)
    }

    private fun exportSingleSticker(uri: Uri) {
        val file = File(filesDir, uri.lastPathSegment ?: "")
        if (!file.exists()) return
        
        val progressDialog = ProgressDialog.newInstance(getString(R.string.exporting_to_gallery))
        isOperationCancelled = false
        progressDialog.onCancel = { isOperationCancelled = true }
        progressDialog.show(supportFragmentManager, "export_single")

        thread {
            val success = BackupHelper.exportSingleStickerToGallery(this, file)
            runOnUiThread {
                progressDialog.dismiss()
                if (isOperationCancelled) {
                    ToastUtils.showToast(this, getString(R.string.failed)) // already finished on disk if single, but show failed
                } else {
                    ToastUtils.showToast(this, if (success) getString(R.string.success) else getString(R.string.failed))
                }
            }
        }
    }

    private fun stickify(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
            return
        }

        val file = File(filesDir, uri.lastPathSegment ?: "")
        if (!file.exists()) return

        // UI Setup for loading state
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, 153)) // 60% alpha

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        binding.loadingIndicator.setIndicatorColor(primary)

        binding.progressBar.visibility = View.VISIBLE
        
        thread {
            val resultFile = ImageUtils.addStickerBorder(this@MainActivity, file)
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (resultFile != null) {
                    adapter.refreshData(this@MainActivity)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.success))
                } else {
                    ToastUtils.showToast(this@MainActivity, getString(R.string.failed))
                }
            }
        }
    }



    private fun deleteSticker(uri: Uri) {
        val file = File(filesDir, uri.lastPathSegment ?: "")
        if (file.exists() && file.delete()) { 
            adapter.refreshData(this)
            syncRecentsAfterDeletion()
            updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
            ToastUtils.showToast(this, getString(R.string.success)) 
        }
    }

    private fun removeFromRecents(entry: String) {
        val prefs = getSharedPreferences("recents", MODE_PRIVATE)
        val list = prefs.getString("list", "")?.split(",")?.toMutableList() ?: mutableListOf()
        if (list.remove(entry)) { prefs.edit().putString("list", list.joinToString(",")).apply(); adapterRecents.refreshData(this); ToastUtils.showToast(this, getString(R.string.success)) }
    }

    private fun startCrop(uri: Uri) {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        if (fileName.endsWith(".gif") || isVideoUri(uri)) {
            ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
            return
        }

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        
        val destinationUri = Uri.fromFile(File(cacheDir, "crop_temp_${System.currentTimeMillis()}.png"))
        
        val options = UCrop.Options().apply {
            val surface = getThemeColor(com.google.android.material.R.attr.colorSurface)
            setToolbarColor(surface)
            setToolbarWidgetColor(primary)
            setActiveControlsWidgetColor(primary)
            setRootViewBackgroundColor(surface)
            setLogoColor(primary)
            setToolbarTitle(getString(R.string.crop))
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setFreeStyleCropEnabled(true)
        }

        val intent = UCrop.of(uri, destinationUri)
            .withOptions(options)
            .getIntent(this)
        
        cropImage.launch(intent)
    }

    private fun viewFullSticker(uri: Uri) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_full, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.img_full_sticker)
        Glide.with(this).load(uri).into(imageView)
        
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok), null)
            .create()
            .showMonetDialog(this)
    }

    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                try {
                    val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                    contentResolver.openInputStream(resultUri)?.use { input ->
                        FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                    adapter.refreshData(this)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    ToastUtils.showToast(this, getString(R.string.success))
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showToast(this, getString(R.string.failed))
                }
            }
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            handleMediaUris(uris)
        }
    }

    private val videoTrimLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val inputTemp = currentTempVideoFile
        currentTempVideoFile = null
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val trimmedPath = TrimVideo.getTrimmedVideoPath(data)
            val startMs = TrimVideo.getTrimStartMs(data).coerceAtLeast(0L)
            val endMs = TrimVideo.getTrimEndMs(data)
            val clipDurationMs = if (endMs > startMs) {
                (endMs - startMs).coerceIn(100L, VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS)
            } else {
                VideoToGifConverter.MAX_VIDEO_STICKER_DURATION_MS
            }

            if (!trimmedPath.isNullOrEmpty()) {
                val trimmedFile = File(trimmedPath)
                val trimmedUri = if (trimmedFile.exists()) {
                    Uri.fromFile(trimmedFile)
                } else if (inputTemp != null && inputTemp.exists()) {
                    Uri.fromFile(inputTemp)
                } else if (trimmedPath.startsWith("/")) {
                    Uri.fromFile(File(trimmedPath))
                } else {
                    Uri.parse(trimmedPath)
                }
                processVideoFile(
                    videoUri = trimmedUri,
                    startMs = startMs,
                    maxDurationMs = clipDurationMs,
                    isTempFile = true,
                    extraCleanupFile = inputTemp
                )
            } else if (inputTemp != null && inputTemp.exists()) {
                processVideoFile(
                    videoUri = Uri.fromFile(inputTemp),
                    startMs = startMs,
                    maxDurationMs = clipDurationMs,
                    isTempFile = true
                )
            } else {
                inputTemp?.delete()
                ToastUtils.showToast(this, getString(R.string.failed))
            }
        } else {
            inputTemp?.delete()
        }
    }


}

class OptionAdapter(context: Context, objects: List<OptionItem>) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_icon_text, parent, false)
        val item = getItem(position)!!
        val textView = view.findViewById<TextView>(R.id.item_text)
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        textView.text = item.text; iconView.setImageResource(item.iconRes)
        val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(context) else androidx.core.content.ContextCompat.getColor(context, R.color.orange_primary)

        if (item.text == context.getString(R.string.delete) || item.text == context.getString(R.string.delete_history)) {
            val red = android.graphics.Color.parseColor("#FF3b30")
            textView.setTextColor(red); iconView.setColorFilter(red)
            iconView.backgroundTintList = android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(red, 30))
        } else {
            // Use the attribute color directly from the layout for best compatibility
            if (item.iconRes != R.drawable.ic_flag_en && item.iconRes != R.drawable.ic_flag_vi && item.iconRes != R.drawable.ic_flag_ru && item.iconRes != R.drawable.ic_flag_zh) iconView.setColorFilter(primary) else iconView.clearColorFilter()
            iconView.background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_circle_icon)
            iconView.backgroundTintList = android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
        }
        return view
    }
}

class ThemeAdapter(context: Context, objects: List<OptionItem>, private val selectedIndex: Int) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_radio_icon_text, parent, false)
        val item = getItem(position)!!
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        val radioButton = view.findViewById<RadioButton>(R.id.item_radio)
        val textView = view.findViewById<TextView>(R.id.item_text)
        iconView.setImageResource(item.iconRes); textView.text = item.text; radioButton.isChecked = (position == selectedIndex)
        val materialColorEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(context) else androidx.core.content.ContextCompat.getColor(context, R.color.orange_primary)
        radioButton.buttonTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)), intArrayOf(primary, android.graphics.Color.GRAY))
        if (item.iconRes != R.drawable.ic_flag_en && item.iconRes != R.drawable.ic_flag_vi && item.iconRes != R.drawable.ic_flag_ru && item.iconRes != R.drawable.ic_flag_zh) iconView.setColorFilter(primary) else iconView.clearColorFilter()
        iconView.background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_circle_icon)
        iconView.backgroundTintList = android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
        return view
    }
}

private fun androidx.appcompat.app.AlertDialog.showMonetDialog(context: android.content.Context) {
    val materialColorEnabled = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)
    if (materialColorEnabled) window?.decorView?.applyMonetRecursively()
    show()
    val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(context) else androidx.core.content.ContextCompat.getColor(context, R.color.orange_primary)
    
    val rippleColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 30))
    listOf(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE, androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE, androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).forEach { which ->
        val btn = getButton(which)
        btn?.setTextColor(primary)
        if (btn is com.google.android.material.button.MaterialButton) {
            btn.rippleColor = rippleColor
        } else if (btn?.background is android.graphics.drawable.RippleDrawable) {
            (btn.background as android.graphics.drawable.RippleDrawable).setColor(rippleColor)
        }
    }
    window?.setDimAmount(0.35f)
}

private fun androidx.appcompat.app.AlertDialog.showDestructiveDialog(context: android.content.Context) {
    val materialColorEnabled = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)
    if (materialColorEnabled) window?.decorView?.applyMonetRecursively()
    show()
    val red = android.graphics.Color.parseColor("#FF3b30")
    val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(context) else androidx.core.content.ContextCompat.getColor(context, R.color.orange_primary)
    
    val ripplePrimary = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 30))
    val rippleRed = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(red, 30))

    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let { btn ->
        btn.setTextColor(red)
        if (btn is com.google.android.material.button.MaterialButton) {
            btn.rippleColor = rippleRed
        } else if (btn.background is android.graphics.drawable.RippleDrawable) {
            (btn.background as android.graphics.drawable.RippleDrawable).setColor(rippleRed)
        }
    }
    
    getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.let { btn ->
        btn.setTextColor(primary)
        if (btn is com.google.android.material.button.MaterialButton) {
            btn.rippleColor = ripplePrimary
        } else if (btn.background is android.graphics.drawable.RippleDrawable) {
            (btn.background as android.graphics.drawable.RippleDrawable).setColor(ripplePrimary)
        }
    }
    
    window?.setDimAmount(0.35f)
}
