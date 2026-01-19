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
        // 1. Theme Persistence
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)
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
        
        // BUG FIX: Sync UI visibility immediately to prevent overlapping views
        syncUIState()
        
        // Handle incoming shares (Single or Multiple)
        handleIncomingShare(intent)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestLegacyPermissions()
        }

        binding.addButton.setOnClickListener { openSystemImagePicker() }
    }

    override fun onResume() {
        super.onResume()
        syncUIState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
    }

    private fun syncUIState() {
        updateLayoutVisibility(binding.bottomNavigation.selectedItemId)
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            updateLayoutVisibility(item.itemId)
            true
        }
    }

    private fun updateLayoutVisibility(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> {
                binding.toolbar.title = "ZSticker"
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
            override fun getSpanSize(position: Int): Int = 
                if (adapter.getItemViewType(position) == 0) 3 else 1
        }
        binding.recycler.layoutManager = layoutManager
        binding.recycler.adapter = adapter
    }

    private fun setupInfoSection() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        
        // 1. Theme
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)
        binding.txtCurrentTheme.text = if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) 
            getString(R.string.theme_dark) else getString(R.string.theme_light)

        binding.itemTheme.setOnClickListener {
            val options = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark))
            AlertDialog.Builder(this)
                .setTitle(R.string.info_theme_title)
                .setItems(options) { _, which ->
                    val mode = if (which == 0) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
                    prefs.edit().putInt("theme_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                }.show()
        }

        // 2. Version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.txtVersion.text = pInfo.versionName
        } catch (e: Exception) { binding.txtVersion.text = "1.0.0" }

        // 3. Source Code
        binding.itemRepo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huanhoahongso3-collab/ZSticker")))
        }

        // 4. License
        binding.itemLicense.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
        }

        // 5. Export All
        binding.itemExportAll.setOnClickListener { exportAllStickers() }
    }

    /** RESTORED: Zalo Sticker Intent */
    override fun onStickerClick(uri: Uri) {
        try {
            // Ensure we are sending a FileProvider URI
            val file = File(filesDir, uri.lastPathSegment ?: "")
            val contentUri = FileProvider.getUriForFile(this, "$packageName.provider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Specific Zalo Sticker Extras
                putExtra("is_sticker", true)
                putExtra("type", 3)
                setClassName("com.zing.zalo", "com.zing.zalo.ui.TempShareViaActivity")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Zalo not found or share failed", Toast.LENGTH_SHORT).show()
        }
    }

    /** RESTORED: 3-Option Long Click Dialog */
    override fun onStickerLongClick(uri: Uri) {
        val options = arrayOf("Export to Gallery", "Delete Sticker")
        AlertDialog.Builder(this)
            .setTitle("Sticker Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportSingleSticker(uri)
                    1 -> deleteSticker(uri)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportSingleSticker(uri: Uri) {
        try {
            val fileName = uri.lastPathSegment ?: "sticker_${System.currentTimeMillis()}.png"
            val file = File(filesDir, fileName)
            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!outDir.exists()) outDir.mkdirs()
            val dest = File(outDir, file.name)
            file.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            Toast.makeText(this, "Saved to Pictures/ZSticker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun deleteSticker(uri: Uri) {
        val fileName = uri.lastPathSegment ?: ""
        val file = File(filesDir, fileName)
        if (file.exists()) file.delete()
        adapter.refreshData(this)
    }

    private fun exportAllStickers() {
        val stickerFiles = filesDir.listFiles { f -> f.name.startsWith("zsticker_") }
        if (stickerFiles.isNullOrEmpty()) {
            Toast.makeText(this, "No stickers to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZSticker")
            if (!outDir.exists()) outDir.mkdirs()
            stickerFiles.forEach { src ->
                val dest = File(outDir, src.name)
                src.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            }
            Toast.makeText(this, "All exported to Pictures/ZSticker", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** RESTORED: Single and Multiple Photo Import */
    private fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let { importToApp(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    uris?.forEach { importToApp(it) }
                }
            }
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
                val file = File(filesDir, "zsticker_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out -> input.copyTo(out) }
                adapter.refreshData(this)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun requestLegacyPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
        }
    }
}
