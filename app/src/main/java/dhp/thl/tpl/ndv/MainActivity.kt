package dhp.thl.tpl.ndv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Locale

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
                        val systemLocale = Configuration(newBase.resources.configuration).locales[0]
                        config.setLocale(systemLocale)
                    }

                    val context = newBase.createConfigurationContext(config)
                    super.attachBaseContext(context)
                }

                override fun onCreate(savedInstanceState: Bundle?) {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

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

                // --- ML KIT BACKGROUND REMOVAL ---

                private fun removeBackground(uri: Uri) {
                    binding.progressBar.visibility = View.VISIBLE

                    // SINGLE_IMAGE_MODE is ideal for sticker creation from gallery
                    val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
                    val segmenter = Segmentation.getClient(options)

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@launch

                            // ML Kit expects an InputImage
                            val image = InputImage.fromBitmap(originalBitmap, 0)

                            segmenter.process(image)
                            .addOnSuccessListener { segmentationMask ->
                                // Switch to Default dispatcher for heavy pixel looping
                                lifecycleScope.launch(Dispatchers.Default) {
                                    val resultBitmap = applyMask(originalBitmap, segmentationMask.buffer)
                                    saveProcessedSticker(resultBitmap)
                                }
                            }
                            .addOnFailureListener {
                                handleError(getString(R.string.failed))
                            }
                        } catch (e: Exception) {
                            handleError(e.message ?: "Error")
                        }
                    }
                }

                private fun applyMask(source: Bitmap, buffer: ByteBuffer): Bitmap {
                    val width = source.width
                    val height = source.height
                    val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                    val pixels = IntArray(width * height)
                    source.getPixels(pixels, 0, width, 0, 0, width, height)

                    buffer.rewind()

                    for (i in pixels.indices) {
                        val confidence = buffer.float
                        // If the AI is less than 50% sure it's a person, make it transparent
                        if (confidence < 0.5f) {
                            pixels[i] = Color.TRANSPARENT
                        }
                    }

                    outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                    return outBitmap
                }

                private suspend fun saveProcessedSticker(bitmap: Bitmap) {
                    withContext(Dispatchers.IO) {
                        val fileName = "zsticker_ml_${System.currentTimeMillis()}.png"
                        val file = File(filesDir, fileName)
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            adapter.refreshData(this@MainActivity)
                            binding.recycler.scrollToPosition(0)
                            Toast.makeText(this@MainActivity, getString(R.string.success), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                private fun handleError(msg: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                // --- NAVIGATION & UI ---

                private fun setupNavigation() {
                    binding.bottomNavigation.setOnItemSelectedListener { item ->
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("last_tab", item.itemId).apply()
                        updateLayoutVisibility(item.itemId)
                        true
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

                // --- SETTINGS & INFO ---

                private fun setupInfoSection() {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    updateThemeText(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

                    binding.itemTheme.setOnClickListener {
                        val themes = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system))
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
                            handleThemeSelection(prefs, newMode)
                            dialog.dismiss()
                        }.show()
                    }

                    val currentLang = prefs.getString("lang", "system") ?: "system"
                    binding.txtCurrentLanguage.text = when (currentLang) {
                        "en" -> "English"
                        "vi" -> "Tiếng Việt"
                        else -> getString(R.string.theme_system)
                    }

                    binding.itemLanguage.setOnClickListener {
                        val langs = arrayOf("English", "Tiếng Việt", getString(R.string.theme_system))
                        val checkedLang = when (currentLang) {
                            "en" -> 0
                            "vi" -> 1
                            else -> 2
                        }

                        MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.info_language_title))
                        .setSingleChoiceItems(langs, checkedLang) { dialog, which ->
                            val langCode = when (which) {
                                0 -> "en"
                                1 -> "vi"
                                else -> "system"
                            }
                            if (currentLang != langCode) {
                                prefs.edit().putString("lang", langCode).apply()
                                recreate()
                            }
                            dialog.dismiss()
                        }.show()
                    }
                    setupSecondaryInfo()
                }

                private fun handleThemeSelection(prefs: SharedPreferences, newMode: Int) {
                    prefs.edit().putInt("theme_mode", newMode).apply()
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    updateThemeText(newMode)
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
                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        binding.txtVersion.text = pInfo.versionName
                    } catch (e: Exception) {
                        binding.txtVersion.text = "1.0.0"
                    }

                    binding.itemVersion.setOnClickListener {
                        val now = System.currentTimeMillis()
                        versionClickCount = if (now - lastClickTime < 500) versionClickCount + 1 else 1
                        lastClickTime = now
                        if (versionClickCount >= 10) {
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

                // --- STICKER CLICK & LONG CLICK ---

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
                    .setItems(arrayOf(getString(R.string.export), getString(R.string.delete), getString(R.string.remove_bg))) { _, which ->
                        when (which) {
                            0 -> exportSingleSticker(uri)
                            1 -> deleteSticker(uri)
                            2 -> removeBackground(uri)
                        }
                    }.setNegativeButton(getString(R.string.cancel), null).show()
                }

                // --- FILE OPERATIONS ---

                private fun importToApp(src: Uri) {
                    contentResolver.openInputStream(src)?.use { input ->
                        val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            if (input.copyTo(out) > 0) adapter.refreshData(this)
                        }
                    }
                }

                private fun exportAllStickers() {
                    val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") }
                    if (stickerFiles.isNullOrEmpty()) return
                        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
                        if (!outDir.exists()) outDir.mkdirs()
                            stickerFiles.forEach { src ->
                                File(outDir, src.name).outputStream().use { out -> src.inputStream().use { it.copyTo(out) } }
                            }
                            Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show()
                }

                private fun exportSingleSticker(uri: Uri) {
                    val file = File(filesDir, uri.lastPathSegment ?: "")
                    val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
                    if (!outDir.exists()) outDir.mkdirs()
                        File(outDir, file.name).outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
                        Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show()
                }

                private fun deleteSticker(uri: Uri) {
                    val file = File(filesDir, uri.lastPathSegment ?: "")
                    if (file.exists() && file.delete()) {
                        adapter.refreshData(this)
                    }
                }

                private fun handleIncomingShare(intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { importToApp(it) }
                    }
                }

                private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
                    uris.forEach { importToApp(it) }
                }

                private fun requestLegacyPermissions() {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
                    }
                }
}
