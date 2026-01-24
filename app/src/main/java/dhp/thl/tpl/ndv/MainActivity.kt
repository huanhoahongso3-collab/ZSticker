package dhp.thl.tpl.ndv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter

    private var versionClickCount = 0
    private var lastClickTime: Long = 0

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
        val langCode = prefs.getString("lang", "en") ?: "en"

        val config = Configuration(newBase.resources.configuration)
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        config.setLocale(locale)

        // Only handle Locale here. 
        // DO NOT manually set config.uiMode, otherwise Follow System will break.
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Apply theme and Dynamic Colors BEFORE super.onCreate
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lastTab = prefs.getInt("last_tab", R.id.nav_home)
        binding.bottomNavigation.selectedItemId = lastTab
        updateLayoutVisibility(lastTab)

        setupNavigation()
        setupStickerList()
        setupInfoSection()
        handleIncomingShare(intent)
        handleEdgeToEdge()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestLegacyPermissions()
        }

        binding.addButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun setupInfoSection() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        
        // --- THEME SECTION ---
        updateThemeText(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

        binding.itemTheme.setOnClickListener {
            val themes = arrayOf(
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)
            )
            val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val checkedItem = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.info_theme_title))
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val newMode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }

                    // We apply even if newMode == currentMode to force-reset 'Follow System' logic
                    val systemNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val isDifferentFromSystem = (newMode == AppCompatDelegate.MODE_NIGHT_YES && systemNightMode != Configuration.UI_MODE_NIGHT_YES) || 
                                                (newMode == AppCompatDelegate.MODE_NIGHT_NO && systemNightMode != Configuration.UI_MODE_NIGHT_NO)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDifferentFromSystem && newMode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dynamic_color_warning_title))
                            .setMessage(getString(R.string.dynamic_color_warning_message))
                            .setPositiveButton(getString(R.string.ok)) { _, _ -> 
                                applyAndSaveTheme(prefs, newMode) 
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show()
                    } else {
                        applyAndSaveTheme(prefs, newMode)
                    }
                    dialog.dismiss()
                }.show()
        }

        // --- LANGUAGE SECTION ---
        val currentLang = prefs.getString("lang", "en") ?: "en"
        binding.txtCurrentLanguage.text = if (currentLang == "vi") "Tiếng Việt" else "English"

        binding.itemLanguage.setOnClickListener {
            val langs = arrayOf("English", "Tiếng Việt")
            val checkedLang = if (currentLang == "vi") 1 else 0

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.info_language_title))
                .setSingleChoiceItems(langs, checkedLang) { dialog, which ->
                    val langCode = if (which == 0) "en" else "vi"
                    if (currentLang != langCode) {
                        prefs.edit().putString("lang", langCode).apply()
                        recreate()
                    }
                    dialog.dismiss()
                }.show()
        }

        // --- VERSION & EASTER EGG ---
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
            if (versionClickCount >= 5) {
                versionClickCount = 0
                startActivity(Intent(this, EasterEggActivity::class.java))
            }
        }

        binding.itemRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker")))
        }
        binding.itemLicense.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
        }
        binding.itemExportAll.setOnClickListener { exportAllStickers() }
    }

    private fun applyAndSaveTheme(prefs: SharedPreferences, mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        updateThemeText(mode)
    }

    private fun updateThemeText(mode: Int) {
        binding.txtCurrentTheme.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    // --- SYSTEM & STICKER UTILS ---

    private fun exportAllStickers() {
        val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") }
        if (stickerFiles.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show()
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

    private fun importToApp(src: Uri) {
        try {
            contentResolver.openInputStream(src)?.use { input ->
                val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    if (input.copyTo(out) > 0) adapter.refreshData(this)
                    else Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { importToApp(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { list ->
                        list.forEach { importToApp(it) }
                    }
                }
            }
        }
    }

    private fun handleEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navInsets.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.addButton) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (96 * resources.displayMetrics.density).toInt() + navInsets.bottom
            }
            insets
        }
    }

    private fun updateLayoutVisibility(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> {
                binding.toolbar.title = getString(R.string.app_name)
                binding.recycler.visibility = View.VISIBLE
                binding.infoLayout.visibility = View.GONE
                binding.addButton.show()
            }
            R.id.nav_info -> {
                binding.toolbar.title = getString(R.string.nav_info)
                binding.recycler.visibility = View.GONE
                binding.infoLayout.visibility = View.VISIBLE
                binding.addButton.hide()
            }
        }
    }

    private fun setupStickerList() {
        val items = StickerAdapter.loadOrdered(this)
        adapter = StickerAdapter(items, this)
        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(pos: Int): Int = if (adapter.getItemViewType(pos) == 0) 3 else 1
        }
        binding.recycler.layoutManager = layoutManager
        binding.recycler.adapter = adapter
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
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.zalo_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStickerLongClick(uri: Uri) {
        val title = SpannableString(getString(R.string.sticker_options_title)).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
        }
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setItems(arrayOf(getString(R.string.export), getString(R.string.delete))) { _, which ->
                if (which == 0) exportSingleSticker(uri) else deleteSticker(uri)
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun exportSingleSticker(uri: Uri) {
        try {
            val file = File(filesDir, uri.lastPathSegment ?: "")
            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!outDir.exists()) outDir.mkdirs()
            File(outDir, file.name).outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
            Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSticker(uri: Uri) {
        val file = File(filesDir, uri.lastPathSegment ?: "")
        if (file.exists() && file.delete()) {
            adapter.refreshData(this)
            Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) uris.forEach { importToApp(it) }
    }

    private fun requestLegacyPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
        }
    }
}
