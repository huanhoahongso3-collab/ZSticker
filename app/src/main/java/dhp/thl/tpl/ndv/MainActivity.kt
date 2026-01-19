package dhp.thl.tpl.ndv

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
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
import androidx.core.content.ContextCompat
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
        // Apply Material 3 Dynamic Colors (Wallpaper-based)
        DynamicColors.applyToActivityIfAvailable(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupStickerList()
        setupInfoSection()
        handleShareIntent(intent)

        // Request permissions for older Android versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestLegacyPermissions()
        }

        // Floating Action Button behavior
        binding.addButton.setOnClickListener { openSystemImagePicker() }
    }

    private fun setupStickerList() {
        adapter = StickerAdapter(StickerAdapter.loadOrdered(this), this)
        binding.recycler.layoutManager = GridLayoutManager(this, 3)
        binding.recycler.adapter = adapter
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.toolbar.title = "ZSticker"
                    binding.recycler.visibility = View.VISIBLE
                    binding.infoLayout.visibility = View.GONE
                    binding.addButton.show() // Float like M3
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
        // 1. Get App Version dynamically from system
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            // Displays under the "Version" title
            binding.txtVersion.text = pInfo.versionName
        } catch (e: Exception) {
            binding.txtVersion.text = "1.0.0"
        }

        // 2. GitHub Repository Item
        binding.itemRepo.setOnClickListener {
            val url = "https://github.com/huanhoahongso3-collab/ZSticker"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // 3. Choose Theme Mode Item
        binding.itemTheme.setOnClickListener {
            val options = arrayOf(
                getString(R.string.theme_system), 
                getString(R.string.theme_light), 
                getString(R.string.theme_dark)
            )
            
            AlertDialog.Builder(this)
                .setTitle(R.string.info_theme_title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    // Update the subtitle text to show current selection
                    binding.txtCurrentTheme.text = options[which]
                }.show()
        }
    }

    private fun requestLegacyPermissions() {
        val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 999)
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
                for (i in 0 until clipData.itemCount) {
                    importToAppOrExternal(clipData.getItemAt(i).uri)
                }
            } else if (uri != null) {
                importToAppOrExternal(uri)
            }
        }
    }

    private fun importToAppOrExternal(src: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentResolver.openInputStream(src)?.use { input ->
                    val file = File(filesDir, "zaticker_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out -> input.copyTo(out) }
                    adapter.addStickerAtTop(this, Uri.fromFile(file))
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        } else {
            importToExternalLegacy(src)
        }
    }

    private fun importToExternalLegacy(src: Uri) {
        try {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Zaticker")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, "zaticker_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(src)?.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            adapter.addStickerAtTop(this, Uri.fromFile(file))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.legacy_import_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStickerClick(uri: Uri) {
        try {
            val contentUri = FileProvider.getUriForFile(this, "$packageName.provider", File(uri.path!!))
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
            .setTitle(R.string.sticker_options_title)
            .setItems(arrayOf(getString(R.string.export), getString(R.string.delete))) { _, which ->
                if (which == 0) exportSticker(uri) else deleteSticker(uri)
            }.show()
    }

    private fun exportSticker(uri: Uri) {
        try {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Zaticker")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, "zaticker_export_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            Toast.makeText(this, getString(R.string.sticker_exported, file.absolutePath), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSticker(uri: Uri) {
        val file = File(uri.path ?: "")
        if (file.exists()) file.delete()
        adapter.removeSticker(this, uri)
        Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) importToAppOrExternal(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { importToAppOrExternal(it) }
            }
        }
    }
}
