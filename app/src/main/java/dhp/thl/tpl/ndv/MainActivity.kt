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

class MainActivity : AppCompatActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
        private lateinit var adapter: StickerAdapter

            private var versionClickCount = 0
            private var lastClickTime: Long = 0

                override fun attachBaseContext(newBase: Context) {
                    val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
                    val langCode = prefs.getString("lang", "system") ?: "system"

                    val config = Configuration(newBase.resources.configuration)

                    if (langCode != "system") {
                        val locale = Locale(langCode)
                        Locale.setDefault(locale)
                        config.setLocale(locale)
                    } else {
                        // Revert to device system locale
                        val systemLocale = Configuration(newBase.resources.configuration).locales[0]
                        config.setLocale(systemLocale)
                    }

                    // Force uiMode to ensure correct theme/colors on startup across all Android versions
                    val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    if (savedTheme != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                        val mode = if (savedTheme == AppCompatDelegate.MODE_NIGHT_YES) 
                            Configuration.UI_MODE_NIGHT_YES 
                        else 
                            Configuration.UI_MODE_NIGHT_NO
                        config.uiMode = mode or (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
                    }

                    // IMPORTANT: We do NOT manually set config.uiMode here anymore.
                    // This allows AppCompatDelegate to handle "Follow System" correctly.
                    val context = newBase.createConfigurationContext(config)
                    super.attachBaseContext(context)
                }

                override fun onCreate(savedInstanceState: Bundle?) {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

                    // Initialize Theme and Dynamic Colors before super.onCreate
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

                override fun onNewIntent(intent: Intent?) {
                    super.onNewIntent(intent)
                    setIntent(intent) // Update the intent for future access
                    handleIncomingShare(intent)
                }

                private fun setupInfoSection() {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)

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

                        val adapter = ThemeAdapter(this, themes, selectedIndex)

                        val dialog = MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.info_theme_title))
                        .setAdapter(adapter) { dialog, which ->
                            val newMode = when (which) {
                                0 -> AppCompatDelegate.MODE_NIGHT_NO
                                1 -> AppCompatDelegate.MODE_NIGHT_YES
                                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                            handleThemeSelection(prefs, newMode)
                            dialog.dismiss()
                        }.create()
                        dialog.window?.setDimAmount(0.35f)
                        dialog.show()
                    }

                    // --- LANGUAGE SELECTOR ---
                    val currentLang = prefs.getString("lang", "system") ?: "system"
                    binding.txtCurrentLanguage.text = when (currentLang) {
                        "en" -> "English"
                        "vi" -> "Tiếng Việt"
                        else -> getString(R.string.theme_system)
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
                        
                         val adapter = ThemeAdapter(this, langs, selectedIndex) // Reusing ThemeAdapter as it fits (OptionItem with Radio)

                         val dialog = MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.info_language_title))
                            .setAdapter(adapter) { d, which ->
                                val langCode = when (which) {
                                    0 -> "en"
                                    1 -> "vi"
                                    else -> "system"
                                }
                                if (currentLang != langCode) {
                                    prefs.edit().putString("lang", langCode).apply()
                                    recreate()
                                }
                                d.dismiss()
                            }
                            .create()
                        
                        dialog.window?.setDimAmount(0.35f)
                        dialog.show()
                    }

                    setupSecondaryInfo()
                }

                private fun handleThemeSelection(prefs: SharedPreferences, newMode: Int) {
                    val systemNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val isDifferentFromSystem = (newMode == AppCompatDelegate.MODE_NIGHT_YES && systemNightMode != Configuration.UI_MODE_NIGHT_YES) ||
                    (newMode == AppCompatDelegate.MODE_NIGHT_NO && systemNightMode != Configuration.UI_MODE_NIGHT_NO)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDifferentFromSystem && newMode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                        val dialog = MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dynamic_color_warning_title))
                        .setMessage(getString(R.string.dynamic_color_warning_message))
                        .setPositiveButton(getString(R.string.ok)) { _, _ -> applyAndSaveTheme(prefs, newMode) }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create()
                        dialog.window?.setDimAmount(0.35f)
                        dialog.show()
                    } else {
                        applyAndSaveTheme(prefs, newMode)
                    }
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

                // --- STICKER OPERATIONS ---

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

                private fun removeBackground(uri: Uri) {
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
                                Toast.makeText(this, getString(R.string.rb_completed), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, getString(R.string.rb_failed), Toast.LENGTH_LONG).show()
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
                        R.id.nav_options -> {
                            binding.toolbar.title = getString(R.string.nav_options)
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

        val options = listOf(
            OptionItem(R.drawable.ic_export, getString(R.string.export)),
            OptionItem(R.drawable.ic_remove_bg, getString(R.string.remove_bg)),
            OptionItem(R.drawable.ic_delete, getString(R.string.delete))
        )

        val adapter = OptionAdapter(this, options)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> exportSingleSticker(uri)
                    1 -> checkAndShowBackgroundRemovalWarning(uri) // Swapped order
                    2 -> deleteSticker(uri) // Swapped order
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        dialog.window?.setDimAmount(0.35f)
        dialog.show()
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

        val checkBox = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_dont_show_again)

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_continue).setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("dont_show_rb_warning", true).apply()
            }
            dialog.dismiss()
            removeBackground(uri)
        }

        dialog.window?.setDimAmount(0.35f)
        dialog.show()
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

data class OptionItem(val iconRes: Int, val text: String)

class OptionAdapter(context: Context, objects: List<OptionItem>) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_icon_text, parent, false)
        val item = getItem(position)
        view.findViewById<ImageView>(R.id.item_icon).setImageResource(item!!.iconRes)
        view.findViewById<TextView>(R.id.item_text).text = item.text
        return view
    }
}

class ThemeAdapter(context: Context, objects: List<OptionItem>, private val selectedIndex: Int) : ArrayAdapter<OptionItem>(context, 0, objects) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_radio_icon_text, parent, false)
        val item = getItem(position)
        view.findViewById<ImageView>(R.id.item_icon).setImageResource(item!!.iconRes)
        view.findViewById<TextView>(R.id.item_text).text = item.text
        view.findViewById<RadioButton>(R.id.item_radio).isChecked = (position == selectedIndex)
        return view
    }
}
