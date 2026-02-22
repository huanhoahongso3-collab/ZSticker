package dhp.thl.tpl.ndv

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.animation.ObjectAnimator
import android.animation.AnimatorInflater
import android.view.animation.AnticipateInterpolator
import androidx.core.animation.doOnEnd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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
import kotlinx.coroutines.launch
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.star

class MainActivity : BaseActivity(), StickerAdapter.StickerListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter
    private lateinit var adapterRecents: StickerAdapter
    private var versionClickCount = 0
    private var lastClickTime: Long = 0
    
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

        if (savedInstanceState == null) {
            handleIncomingShare(intent)
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
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                
                // Colors for light/dark mode
                val headColor = primary
                val cardColor = if (isDark) surface else Color.parseColor("#F5F5F5") 
                
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
                    binding.imgFiles,
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
                
                // Allow FAB to use standard XML theme colors (colorSecondaryContainer) in dark/light mode
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestLegacyPermissions()
        }

        binding.addButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun updateStatusBar() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightMode = nightMode == Configuration.UI_MODE_NIGHT_NO
        
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = isLightMode
        windowInsetsController.isAppearanceLightNavigationBars = isLightMode
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) 
        handleIncomingShare(intent)
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

        binding.itemVersion.setOnClickListener {
            val now = System.currentTimeMillis()
            versionClickCount = if (now - lastClickTime < 500) versionClickCount + 1 else 1
            lastClickTime = now
            if (versionClickCount == 10) {
                startActivity(Intent(this, MoreOptionActivity::class.java))
            } else if (versionClickCount >= 20) {
                versionClickCount = 0
                startActivity(Intent(this, AdvancedSettingsActivity::class.java))
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

    private fun importToApp(src: Uri): Int {
        try {
            val mimeType = contentResolver.getType(src) ?: "application/octet-stream"
            if (!mimeType.startsWith("image/") || mimeType == "image/gif") return 1

            contentResolver.openInputStream(src)?.use { input ->
                val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    if (input.copyTo(out) > 0) return 0 else return 2
                }
            }
            return 2
        } catch (e: Exception) { return 2 }
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                    if (uris.isEmpty()) intent.data?.let { uris.add(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
                if (uris.isEmpty()) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
                }
            }
            Intent.ACTION_VIEW -> { intent.data?.let { uris.add(it) } }
        }

        if (uris.isNotEmpty()) {
            var hasUnsupported = false
            var hasFailed = false
            var hasSuccess = false

            uris.forEach { uri ->
                when (importToApp(uri)) {
                    0 -> hasSuccess = true
                    1 -> hasUnsupported = true
                    2 -> hasFailed = true
                }
            }

            if (hasSuccess) {
                adapter.refreshData(this)
                updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
            }
            
            if (hasUnsupported) ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
            else if (hasFailed) ToastUtils.showToast(this, getString(R.string.failed))
        }
    }

    private val backgroundRemover by lazy { MediaPipeBackgroundRemover(this) }

    private fun removeBackground(uri: Uri) {
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.progressBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, 153))

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
        val primary = if (materialColorEnabled) MonetCompat.getInstance().getAccentColor(this) else getColor(R.color.orange_primary)
        binding.loadingIndicator.setIndicatorColor(primary)

        binding.progressBar.visibility = View.VISIBLE

        thread {
            var isSuccess = false
            var resultUri: Uri? = null
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                
                // Copy bitmap to ARGB_8888 if it's not
                var argbBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val maxDim = 1024
                if (argbBitmap.width > maxDim || argbBitmap.height > maxDim) {
                    val ratio = maxDim.toFloat() / kotlin.math.max(argbBitmap.width, argbBitmap.height)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(argbBitmap, (argbBitmap.width * ratio).toInt(), (argbBitmap.height * ratio).toInt(), true)
                    if (scaled != argbBitmap) {
                        argbBitmap.recycle()
                        argbBitmap = scaled
                    }
                }
                val outBitmap = backgroundRemover.removeBackground(argbBitmap)
                
                if (outBitmap != null) {
                    val file = File(filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        outBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    resultUri = Uri.fromFile(file)
                    isSuccess = true
                }
            } catch (t: Throwable) { isSuccess = false }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (isSuccess && resultUri != null) {
                    adapter.refreshData(this@MainActivity)
                    updateEmptyState(adapter.itemCount == 0, getString(R.string.no_stickers_found))
                    binding.recycler.scrollToPosition(0)
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_completed))
                } else {
                    ToastUtils.showToast(this@MainActivity, getString(R.string.rb_failed))
                }
            }
        }
    }

    private fun handleEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navInsets.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.addButton) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.addButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val baseMargin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 112 else 104
                bottomMargin = (baseMargin * resources.displayMetrics.density).toInt() + navInsets.bottom
            }
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
            
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
