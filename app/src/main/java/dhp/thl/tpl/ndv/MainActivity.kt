package dhp.thl.tpl.ndv

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.DynamicColors
import dhp.thl.tpl.ndv.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), StickerAdapter.StickerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StickerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Theme persistence with Loop Prevention (Fixes the "Blinking" issue)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        // Only set mode if it's actually different from the current one
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
        }

        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupStickerList()
        setupInfoSection()
        handleShareIntent(intent)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestLegacyPermissions()
        }

        binding.addButton.setOnClickListener { openSystemImagePicker() }
    }

    private fun setupStickerList() {
        val items = StickerAdapter.loadOrdered(this)
        adapter = StickerAdapter(items, this)
        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = 
                if (adapter.getItemViewType(position) == 0) 3 else 1
        }
        binding.recycler.layoutManager = layoutManager
        binding.recycler.adapter = adapter
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.toolbar.title = "ZSticker"
                    binding.recycler.visibility = View.VISIBLE
                    binding.infoLayout.visibility = View.GONE
                    binding.addButton.show()
                    true
                }
                R.id.nav_info -> {
                    binding.toolbar.title = getString(R.string.nav_info)
                    binding.recycler.visibility = View.GONE
                    binding.infoLayout.visibility = View.VISIBLE
                    binding.addButton.hide()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupInfoSection() {
        // Version info
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

        // Theme Switcher with Persistence
        binding.itemTheme.setOnClickListener {
            val options = arrayOf(
                getString(R.string.theme_system), 
                getString(R.string.theme_light), 
                getString(R.string.theme_dark)
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.info_theme_title)
                .setItems(options) { _, which ->
                    val mode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        else -> AppCompatDelegate.MODE_NIGHT_YES
                    }
                    // Save preference
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putInt("theme_mode", mode).apply()
                    
                    // Apply theme (this will trigger a clean activity recreation)
                    AppCompatDelegate.setDefaultNightMode(mode)
                }.show()
        }

        // Export All Feature
        binding.itemExportAll.setOnClickListener { exportAllStickers() }

        binding.itemRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker")))
        }
    }

    private fun exportAllStickers() {
        val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zaticker_") }
        if (stickerFiles.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.no_stickers), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!outDir.exists()) outDir.mkdirs()

            stickerFiles.forEach { src ->
                val dest = File(outDir, src.name)
                src.inputStream().use { input -> 
                    dest.outputStream().use { output -> input.copyTo(output) } 
                }
            }
            Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickImages.launch(intent)
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val clipData = result.data?.clipData
            val uri = result.data?.data
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) importToApp(clipData.getItemAt(i).uri)
            } else if (uri != null) {
                importToApp(uri)
            }
        }
    }

    private fun importToApp(src: Uri) {
        try {
            contentResolver.openInputStream(src)?.use { input ->
                val file = File(filesDir, "zaticker_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out -> input.copyTo(out) }
                // Ensure refreshData exists in StickerAdapter
                adapter.refreshData(this)
                Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { 
            Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_SHORT).show() 
        }
    }

    override fun onStickerClick(uri: Uri) {
        try {
            val file = File(uri.path!!)
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sticker_options_title))
            .setItems(arrayOf(getString(R.string.delete))) { _, _ ->
                File(uri.path!!).delete()
                adapter.refreshData(this)
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { importToApp(it) }
        }
    }

    private fun requestLegacyPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
    }
}
