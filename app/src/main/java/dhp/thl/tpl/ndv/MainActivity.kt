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
import kotlinx.coroutines.launch

class MainActivity : MonetCompatActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter
    private lateinit var adapterRecents: StickerAdapter

            private var versionClickCount = 0
            private var lastClickTime: Long = 0

            private fun getThemeColor(attr: Int): Int {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(attr, typedValue, true)
                return typedValue.data
            }

                override fun attachBaseContext(newBase: Context) {
                    val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
                    val langCode = prefs.getString("lang", "system") ?: "system"

                    val config = Configuration(newBase.resources.configuration)

                    // Apply Locale
                    if (langCode != "system") {
                        val locale = Locale(langCode)
                        Locale.setDefault(locale)
                        config.setLocale(locale)
                    } else {
                        // Revert to device system locale
                        val systemLocale = Configuration(newBase.resources.configuration).locales[0]
                        config.setLocale(systemLocale)
                    }

                    // Removed manual config.uiMode override here as it conflicts with DynamicColors 
                    // initialization in onCreate. AppCompatDelegate handles the switch.
                    
                    val context = newBase.createConfigurationContext(config)
                    super.attachBaseContext(context)
                }

                override fun onCreate(savedInstanceState: Bundle?) {
                    val splashScreen = installSplashScreen()
                    
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

                    // Ensure theme is set BEFORE super.onCreate
                    AppCompatDelegate.setDefaultNightMode(savedTheme)
                    
                    // Applied via MonetCompatActivity automatically if enabled
                    // Removed old DynamicColors implementation


                    super.onCreate(savedInstanceState)
                    
                    splashScreen.setOnExitAnimationListener { splashScreenView ->
                        val zoomX = ObjectAnimator.ofFloat(
                            splashScreenView.iconView,
                            View.SCALE_X,
                            1.0f,
                            5.0f
                        )
                        val zoomY = ObjectAnimator.ofFloat(
                            splashScreenView.iconView,
                            View.SCALE_Y,
                            1.0f,
                            5.0f
                        )
                        val alpha = ObjectAnimator.ofFloat(
                            splashScreenView.view,
                            View.ALPHA,
                            1.0f,
                            0.0f
                        )

                        zoomX.duration = 500L
                        zoomY.duration = 500L
                        alpha.duration = 500L
                        
                        zoomX.interpolator = AnticipateInterpolator()
                        zoomY.interpolator = AnticipateInterpolator()
                        
                        zoomX.doOnEnd { splashScreenView.remove() }
                        
                        zoomX.start()
                        zoomY.start()
                        alpha.start()
                    }

                    binding = ActivityMainBinding.inflate(layoutInflater)
                    setContentView(binding.root)

                    setupStickerList()
                    setupNavigation()
                    setupInfoSection()

                    val startTab = if (savedInstanceState == null) R.id.nav_home else prefs.getInt("last_tab", R.id.nav_home)
                    binding.bottomNavigation.selectedItemId = startTab
                    updateLayoutVisibility(startTab)

                    handleIncomingShare(intent)
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
                            val surface = if (isDark) monetInstance.getBackgroundColor(this@MainActivity, true) else Color.WHITE
                            
                            // Colors for light/dark mode
                            val headColor = if (isDark) primary else primary
                            val cardColor = if (isDark) surface else Color.parseColor("#F5F5F5") 
                            
                            binding.sectionSettingsHeader.setTextColor(headColor)
                            binding.sectionGeneralHeader.setTextColor(headColor)
                            binding.cardSettings.setCardBackgroundColor(cardColor)
                            binding.cardGeneral.setCardBackgroundColor(cardColor)
                            
                            // Force apply to FAB
                            binding.addButton.backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
                            binding.addButton.imageTintList = android.content.res.ColorStateList.valueOf(monetInstance.getBackgroundColor(this@MainActivity))

                            // Tint all icons in options pane
                            listOf(
                                binding.imgTheme, binding.imgMaterialColor, binding.imgLanguage,
                                binding.imgExportAll, binding.imgRemoveRecent, binding.imgRemoveAll,
                                binding.imgVersion, binding.imgRepo, binding.imgLicense, binding.imgOpenSource
                            ).forEach { icon ->
                                icon.setColorFilter(primary)
                            }
                            
                            // Fix Switch tinting
                            val states = arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf(-android.R.attr.state_checked)
                            )
                            val thumbColors = intArrayOf(primary, Color.WHITE)
                            val trackColors = intArrayOf(primary, Color.LTGRAY)
                            
                            binding.switchMaterialColor.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
                            binding.switchMaterialColor.trackTintList = android.content.res.ColorStateList(states, trackColors).withAlpha(128)
                            binding.switchMaterialColor.thumbIconTint = android.content.res.ColorStateList.valueOf(
                                if (isDark) monetInstance.getBackgroundColor(this@MainActivity) else Color.WHITE
                            )
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
                    setIntent(intent) // Update the intent for future access
                    handleIncomingShare(intent)
                }

                private fun setupInfoSection() {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val materialColorEnabled = prefs.getBoolean("material_color_enabled", false)

                    // --- THEME SELECTOR ---
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
                            handleThemeSelection(prefs, newMode)
                        }
                    }

                    // --- LANGUAGE SELECTOR ---
                    val currentLang = prefs.getString("lang", "system") ?: "system"
                    binding.txtCurrentLanguage.text = when (currentLang) {
                        "en" -> getString(R.string.lang_en)
                        "vi" -> getString(R.string.lang_vi)
                        else -> getString(R.string.lang_system)
                    }

                    binding.itemLanguage.setOnClickListener {
                        val langs = listOf(
                            OptionItem(R.drawable.ic_flag_en, getString(R.string.lang_en)),
                            OptionItem(R.drawable.ic_flag_vi, getString(R.string.lang_vi)),
                            OptionItem(R.drawable.ic_settings_system, getString(R.string.lang_system))
                        )

                        val langSelection = prefs.getString("lang", "system") ?: "system"
                        val selectedIndex = when (langSelection) {
                            "en" -> 0
                            "vi" -> 1
                            else -> 2
                        }
                        
                        showPaneDialog(
                            getString(R.string.info_language_title),
                            langs,
                            selectedIndex
                        ) { which ->
                            val langCode = when (which) {
                                0 -> "en"
                                1 -> "vi"
                                else -> "system"
                            }
                            if (currentLang != langCode) {
                                prefs.edit().putString("lang", langCode).apply()
                                recreate()
                            }
                        }
                    }

                    // --- MATERIAL COLOR TOGGLE ---
                    val materialColorIcon = binding.imgMaterialColor
                    // No programmatic tinting here - let XML/Theme handle it like other icons

                    binding.switchMaterialColor.isChecked = materialColorEnabled
                    binding.switchMaterialColor.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) {
                            prefs.edit().putBoolean("material_color_enabled", true).apply()
                            recreate()
                        } else {
                            if (prefs.getBoolean("material_color_enabled", false)) {
                                prefs.edit().putBoolean("material_color_enabled", false).apply()
                                recreate()
                            }
                        }
                    }

                    setupSecondaryInfo()
                }

    private fun handleThemeSelection(prefs: SharedPreferences, newMode: Int) {
        if (prefs.getBoolean("dont_show_theme_warning", false)) {
            applyAndSaveTheme(prefs, newMode)
        } else {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_warning, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()

            val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
            val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
            val iconView = dialogView.findViewById<ImageView>(R.id.icon_warning)
            val checkBox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cb_dont_show_again)
            val btnContinue = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue)
            val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)

            btnCancel.visibility = View.GONE
            
            titleView.text = getString(R.string.theme_change_warning_title)
            messageView.text = getString(R.string.theme_change_warning_message)
            btnContinue.text = getString(R.string.ok)
            
            iconView.setImageResource(R.drawable.ic_palette)
            val isMaterial = prefs.getBoolean("material_color_enabled", false)
            if (!isMaterial) {
                iconView.setColorFilter(getColor(R.color.orange_primary))
            } else {
                val primary = MonetCompat.getInstance().getAccentColor(this)
                iconView.setColorFilter(primary)
                btnContinue.setTextColor(primary)
                (btnCancel as? com.google.android.material.button.MaterialButton)?.setTextColor(primary)
            }

            btnContinue.setOnClickListener {
                if (checkBox.isChecked) {
                    prefs.edit().putBoolean("dont_show_theme_warning", true).apply()
                }
                applyAndSaveTheme(prefs, newMode)
                dialog.dismiss()
            }

            dialog.showMonetDialog(this)
        }
    }

                private fun applyAndSaveTheme(prefs: SharedPreferences, mode: Int) {
                    prefs.edit().putInt("theme_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                    updateThemeText(mode)
                    recreate() // Force recreation to apply the new theme
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
                        binding.txtVersion.text = pInfo.versionName
                    } catch (e: Exception) {
                        binding.txtVersion.text = "1.0.0"
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
                    binding.itemExportAll.setOnClickListener { exportAllStickers() }
                    binding.itemRemoveRecent.setOnClickListener { confirmRemoveRecent() }
                    binding.itemRemoveAll.setOnClickListener { confirmDeleteAll() }
                    binding.itemOpenSource.setOnClickListener { showOpenSourceLicenses() }
                }
                private fun showOpenSourceLicenses() {
                    startActivity(Intent(this, LicenseActivity::class.java))
                }

                private fun confirmRemoveRecent() {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.info_remove_recent_confirm_title))
                        .setMessage(getString(R.string.info_remove_recent_confirm_message))
                        .setPositiveButton(getString(R.string.delete)) { _, _ -> removeRecentUsage() }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create().showMonetDialog(this)
                }

                private fun removeRecentUsage() {
                    val prefs = getSharedPreferences("recents", MODE_PRIVATE)
                    val history = prefs.getString("list", "")
                    if (history.isNullOrEmpty()) {
                        ToastUtils.showToast(this, getString(R.string.no_history_found))
                        return
                    }
                    prefs.edit().remove("list").apply()
                    adapterRecents.refreshData(this)
                    ToastUtils.showToast(this, getString(R.string.success))
                }

                private fun confirmDeleteAll() {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.info_remove_all_confirm_title))
                        .setMessage(getString(R.string.info_remove_all_confirm_message))
                        .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteAllStickers() }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create().showMonetDialog(this)
                }

                private fun deleteAllStickers() {
                    val files = filesDir.listFiles { f -> f.name.startsWith("zsticker_") }
                    if (files.isNullOrEmpty()) {
                        ToastUtils.showToast(this, getString(R.string.no_stickers_found))
                        return
                    }
                    var successCount = 0
                    files.forEach { if (it.delete()) successCount++ }
                    
                    adapter.refreshData(this)
                    syncRecentsAfterDeletion()
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

                // --- STICKER OPERATIONS ---

                private fun exportAllStickers() {
                    val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") }
                    if (stickerFiles.isNullOrEmpty()) {
                        ToastUtils.showToast(this, getString(R.string.no_stickers_found))
                        return
                    }
                    try {
                        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
                        if (!outDir.exists()) outDir.mkdirs()
                            var successCount = 0
                            stickerFiles.forEach { src ->
                                val destFile = File(outDir, src.name)
                                src.inputStream().use { input ->
                                    destFile.outputStream().use { output -> if (input.copyTo(output) > 0) successCount++ }
                                }
                            }
                            Toast.makeText(this, if (successCount > 0) getString(R.string.success) else getString(R.string.failed), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show()
                    }
                }

                /**
                 * Imports a single URI as a sticker.
                 * Returns: 0 for success, 1 for unsupported type (including GIF), 2 for other failures.
                 */
                private fun importToApp(src: Uri): Int {
                    try {
                        val mimeType = contentResolver.getType(src) ?: "application/octet-stream"
                        if (!mimeType.startsWith("image/") || mimeType == "image/gif") {
                            return 1
                        }

                        contentResolver.openInputStream(src)?.use { input ->
                            val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out ->
                                if (input.copyTo(out) > 0) {
                                    return 0
                                } else {
                                    return 2
                                }
                            }
                        }
                        return 2
                    } catch (e: Exception) {
                        return 2
                    }
                }

                private fun handleIncomingShare(intent: Intent?) {
                    if (intent == null) return
                    val uris = mutableListOf<Uri>()
                    when (intent.action) {
                        Intent.ACTION_SEND -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
                        }
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

                        if (hasSuccess) adapter.refreshData(this)
                        
                        if (hasUnsupported) {
                            ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
                        } else if (hasFailed) {
                            ToastUtils.showToast(this, getString(R.string.failed))
                        }
                    }
                }

                private fun isNetworkAvailable(): Boolean {
                    return try {
                        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val activeNetwork = connectivityManager.activeNetwork ?: return false
                        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
                        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    } catch (e: Exception) {
                        false
                    }
                }

                private fun removeBackground(uri: Uri) {
                    if (!isNetworkAvailable()) {
                        ToastUtils.showToast(this@MainActivity, getString(R.string.no_internet_access))
                        return
                    }
                    binding.progressBar.visibility = View.VISIBLE
                    thread {
                        var isSuccess = false
                        var resultUri: Uri? = null
                        try {
                            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception()
                            val url = URL("https://briarmbg20.vercel.app/api/rmbg")
                            val connection = (url.openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                doOutput = true
                                setRequestProperty("Content-Type", "application/octet-stream")
                                connectTimeout = 30000
                                readTimeout = 30000
                            }

                            connection.outputStream.use { out -> inputStream.copyTo(out) }

                            if (connection.responseCode == 200) {
                                val file = File(filesDir, "zsticker_rb_${System.currentTimeMillis()}.png")
                                connection.inputStream.use { input ->
                                    FileOutputStream(file).use { out -> input.copyTo(out) }
                                }
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
                                binding.recycler.scrollToPosition(0)
                                ToastUtils.showToast(this@MainActivity, getString(R.string.rb_completed))
                            } else {
                                ToastUtils.showToast(this@MainActivity, getString(R.string.rb_failed))
                            }
                        }
                    }
                }

                private fun handleEdgeToEdge() {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
                    ViewCompat.setOnApplyWindowInsetsListener(binding.addButton) { view, insets ->
                        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            val baseMargin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 112 else 104
                            bottomMargin = (baseMargin * resources.displayMetrics.density).toInt() + navInsets.bottom
                        }
                        insets
                    }
                }

                private fun updateLayoutVisibility(itemId: Int) {
                    when (itemId) {
                        R.id.nav_home -> {
                            binding.toolbar.title = SpannableString(getString(R.string.nav_home)).apply {
                                setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
                            }
                            binding.recycler.visibility = View.VISIBLE
                            binding.recyclerRecents.visibility = View.GONE
                            binding.infoLayout.visibility = View.GONE
                            binding.addButton.show()
                        }
                        R.id.nav_recents -> {
                            binding.toolbar.title = SpannableString(getString(R.string.nav_recents)).apply {
                                setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
                            }
                            binding.recycler.visibility = View.GONE
                            binding.recyclerRecents.visibility = View.VISIBLE
                            binding.infoLayout.visibility = View.GONE
                            binding.addButton.hide()
                            adapterRecents.refreshData(this)
                        }
                        R.id.nav_options -> {
                            binding.toolbar.title = SpannableString(getString(R.string.nav_options)).apply {
                                setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
                            }
                            binding.recycler.visibility = View.GONE
                            binding.recyclerRecents.visibility = View.GONE
                            binding.infoLayout.visibility = View.VISIBLE
                            binding.addButton.hide()
                        }
                    }
                }

                private fun setupStickerList() {
                    // Home adapter
                    val items = StickerAdapter.loadOrdered(this)
                    adapter = StickerAdapter(items, this)
                    val layoutManager = GridLayoutManager(this, 3)
                    layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(pos: Int): Int = if (adapter.getItemViewType(pos) == 0) 3 else 1
                    }
                    binding.recycler.layoutManager = layoutManager
                    binding.recycler.adapter = adapter

                    // Recents adapter
                    val recentItems = StickerAdapter.loadRecents(this)
                    adapterRecents = StickerAdapter(recentItems, this, isRecents = true)
                    val layoutManagerRecents = GridLayoutManager(this, 3)
                    layoutManagerRecents.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(pos: Int): Int = if (adapterRecents.getItemViewType(pos) == 0) 3 else 1
                    }
                    binding.recyclerRecents.layoutManager = layoutManagerRecents
                    binding.recyclerRecents.adapter = adapterRecents

                    // Enable stretch overscroll if material color is enabled
                    val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)
                    if (materialColorEnabled) {
                        binding.recycler.enableStretchOverscroll()
                        binding.recyclerRecents.enableStretchOverscroll()
                        // info_layout is a ScrollView, enableStretchOverscroll supports NestedScrollView/RecyclerView
                        // binding.infoLayout.enableStretchOverscroll() 
                    }
                }

                private fun setupNavigation() {
                    binding.bottomNavigation.setOnItemSelectedListener { item ->
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("last_tab", item.itemId).apply()
                        updateLayoutVisibility(item.itemId)
                        true
                    }
                }

                override fun onStickerClick(uri: Uri) {
                    try {
                        val file = File(filesDir, uri.lastPathSegment ?: "")
                        if (!file.exists()) return

                        val contentUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra("is_sticker", true)
                            putExtra("type", 3)
                            setClassName("com.zing.zalo", "com.zing.zalo.ui.TempShareViaActivity")
                        }
                        startActivity(intent)
                        
                        // Only add to recents if sharing didn't fail (no exception occurred)
                        addToRecents(file.name)
                    } catch (e: Exception) {
                        ToastUtils.showToast(this, getString(R.string.zalo_share_failed))
                    }
                }

                private fun addToRecents(fileName: String) {
                    val prefs = getSharedPreferences("recents", MODE_PRIVATE)
                    val list = prefs.getString("list", "")?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
                    val timestamp = System.currentTimeMillis()
                    
                    list.add(0, "$fileName:$timestamp")
                    if (list.size > 100) list.removeAt(100) // History limit
                    prefs.edit().putString("list", list.joinToString(",")).apply()
                    
                    // Refresh recents adapter if it's currently showing
                    if (binding.bottomNavigation.selectedItemId == R.id.nav_recents) {
                        adapterRecents.refreshData(this)
                    }
                }

    override fun onStickerLongClick(uri: Uri, isRecent: Boolean, entry: String?) {
        val titleRes = if (isRecent) R.string.recent_options_title else R.string.sticker_options_title
        val title = getString(titleRes)

        val options = mutableListOf<OptionItem>()
        options.add(OptionItem(R.drawable.ic_export, getString(R.string.export)))
        
        if (!isRecent) {
            options.add(OptionItem(R.drawable.ic_remove_bg, getString(R.string.remove_bg)))
            options.add(OptionItem(R.drawable.ic_delete, getString(R.string.delete)))
        } else {
            options.add(OptionItem(R.drawable.ic_delete, getString(R.string.delete_history)))
        }

        showPaneDialog(title, options) { which ->
            val selectedOption = options[which].text
            when {
                selectedOption == getString(R.string.export) -> exportSingleSticker(uri)
                selectedOption == getString(R.string.remove_bg) -> checkAndShowBackgroundRemovalWarning(uri)
                selectedOption == getString(R.string.delete) -> deleteSticker(uri)
                selectedOption == getString(R.string.delete_history) -> entry?.let { removeFromRecents(it) }
            }
        }
    }

    private fun showPaneDialog(
        title: String,
        items: List<OptionItem>,
        selectedIndex: Int = -1,
        onItemClick: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_list_pane, null)
        val container = dialogView.findViewById<ViewGroup>(R.id.dialog_item_container)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        items.forEachIndexed { index, item ->
            val itemView = if (selectedIndex != -1) {
                val adapter = ThemeAdapter(this, items, selectedIndex)
                adapter.getView(index, null, container)
            } else {
                val adapter = OptionAdapter(this, items)
                adapter.getView(index, null, container)
            }

            itemView.setOnClickListener {
                onItemClick(index)
                dialog.dismiss()
            }
            container.addView(itemView)

            if (index < items.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                    )
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                    setBackgroundColor(typedValue.data)
                    alpha = 0.1f
                }
                container.addView(divider)
            }
        }

        dialog.showMonetDialog(this)
    }

    private fun checkAndShowBackgroundRemovalWarning(uri: Uri) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("dont_show_rb_warning", false)) {
            removeBackground(uri)
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_warning, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon_warning)
        val checkBox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cb_dont_show_again)

        titleView.text = getString(R.string.rb_warning_title)
        messageView.text = getString(R.string.rb_warning_message)
        iconView.setImageResource(R.drawable.ic_remove_bg)
        
        val isMaterial = prefs.getBoolean("material_color_enabled", false)
        if (!isMaterial) {
            iconView.setColorFilter(getColor(R.color.orange_primary))
        } else {
            val primary = MonetCompat.getInstance().getAccentColor(this)
            iconView.setColorFilter(primary)
            (dialogView.findViewById<View>(R.id.btn_continue) as? com.google.android.material.button.MaterialButton)?.setTextColor(primary)
            (dialogView.findViewById<View>(R.id.btn_cancel) as? com.google.android.material.button.MaterialButton)?.setTextColor(primary)
        }

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_continue).setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("dont_show_rb_warning", true).apply()
            }
            dialog.dismiss()
            removeBackground(uri)
        }

        dialog.showMonetDialog(this)
    }

                private fun exportSingleSticker(uri: Uri) {
                    try {
                        val file = File(filesDir, uri.lastPathSegment ?: "")
                        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
                        if (!outDir.exists()) outDir.mkdirs()
                            File(outDir, file.name).outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
                            ToastUtils.showToast(this, getString(R.string.success))
                    } catch (e: Exception) {
                        ToastUtils.showToast(this, getString(R.string.failed))
                    }
                }

                private fun deleteSticker(uri: Uri) {
                    val file = File(filesDir, uri.lastPathSegment ?: "")
                    if (file.exists() && file.delete()) {
                        adapter.refreshData(this)
                        syncRecentsAfterDeletion()
                        ToastUtils.showToast(this, getString(R.string.success))
                    }
                }

                private fun removeFromRecents(entry: String) {
                    val prefs = getSharedPreferences("recents", MODE_PRIVATE)
                    val list = prefs.getString("list", "")?.split(",")?.toMutableList() ?: mutableListOf()
                    if (list.remove(entry)) {
                        prefs.edit().putString("list", list.joinToString(",")).apply()
                        adapterRecents.refreshData(this)
                        ToastUtils.showToast(this, getString(R.string.success))
                    }
                }

                private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
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
                        if (hasSuccess) adapter.refreshData(this)
                        if (hasUnsupported) {
                            ToastUtils.showToast(this, getString(R.string.unsupported_file_type))
                        } else if (hasFailed) {
                            ToastUtils.showToast(this, getString(R.string.failed))
                        }
                    }
                }

                private fun requestLegacyPermissions() {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
                    }
                }
}

data class OptionItem(val iconRes: Int, val text: String)

class OptionAdapter(context: Context, objects: List<OptionItem>) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_icon_text, parent, false)
        val item = getItem(position)
        val textView = view.findViewById<TextView>(R.id.item_text)
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        
        textView.text = item!!.text
        iconView.setImageResource(item.iconRes)
        
        if (item.text == context.getString(R.string.delete) || item.text == context.getString(R.string.delete_history)) {
            textView.setTextColor(android.graphics.Color.parseColor("#FF3b30"))
            iconView.setColorFilter(android.graphics.Color.parseColor("#FF3b30"))
        } else {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            textView.setTextColor(typedValue.data)
            iconView.setColorFilter(typedValue.data)
        }
        
        return view
    }
}

class ThemeAdapter(context: Context, objects: List<OptionItem>, private val selectedIndex: Int) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_radio_icon_text, parent, false)
        val item = getItem(position)
        val iconView = view.findViewById<ImageView>(R.id.item_icon)
        val radioButton = view.findViewById<RadioButton>(R.id.item_radio)
        
        iconView.setImageResource(item!!.iconRes)
        view.findViewById<TextView>(R.id.item_text).text = item.text
        radioButton.isChecked = (position == selectedIndex)
        
        if (context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)) {
            val primary = MonetCompat.getInstance().getAccentColor(context)
            val sl = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                intArrayOf(primary, android.graphics.Color.GRAY)
            )
            radioButton.buttonTintList = sl
            iconView.setColorFilter(primary)
        }
        
        return view
    }
}

private fun androidx.appcompat.app.AlertDialog.showMonetDialog(context: android.content.Context) {
    if (context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("material_color_enabled", false)) {
        window?.decorView?.applyMonetRecursively()
    }
    show()
    window?.setDimAmount(0.35f)
}
