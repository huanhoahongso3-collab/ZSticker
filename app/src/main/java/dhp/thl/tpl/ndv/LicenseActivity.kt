package dhp.thl.tpl.ndv

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively
import kotlinx.coroutines.launch

class LicenseActivity : MonetCompatActivity() {

    data class Library(val name: String, val licenseName: String, val licenseText: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val materialColorEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false)

        lifecycleScope.launch {
            if (materialColorEnabled) {
                monet.awaitMonetReady()
            }

            setContentView(R.layout.activity_license)

            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
            val headerIcon = findViewById<android.widget.ImageView>(R.id.imgHeaderIcon)

            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "" // Centered title is in the scroll view
            toolbar.setNavigationOnClickListener { finish() }

            // Handle edge-to-edge
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
                val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updatePadding(top = statusBarInsets.top)
                insets
            }

            if (materialColorEnabled) {
                window.decorView.applyMonetRecursively()
                val primary = MonetCompat.getInstance().getAccentColor(this@LicenseActivity)
                headerIcon.setColorFilter(primary)
                headerIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(primary).withAlpha(40)
            }

            val libraries = listOf(
                Library("AndroidX Libraries", "Apache 2.0", "Licensed under the Apache License, Version 2.0 (the \"License\");\nyou may not use this file except in compliance with the License.\nYou may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0"),
                Library("Glide", "BSD/MIT/Apache", "License information for Glide can be found at: https://github.com/bumptech/glide/blob/master/LICENSE"),
                Library("Kotlin", "Apache 2.0", "Licensed under the Apache License, Version 2.0 (the \"License\");\nyou may not use this file except in compliance with the License.\nYou may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0"),
                Library("Material Components", "Apache 2.0", "Licensed under the Apache License, Version 2.0 (the \"License\");\nyou may not use this file except in compliance with the License.\nYou may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0"),
                Library("MonetCompat", "MIT License", "Copyright (c) 2021 Kieron Quinn\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.")
            ).sortedBy { it.name }

            recyclerView.layoutManager = LinearLayoutManager(this@LicenseActivity)
            recyclerView.adapter = LicenseAdapter(libraries)
        }
    }

    private class LicenseAdapter(
        private val libraries: List<Library>
    ) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtName)
            val txtLicenseName: TextView = view.findViewById(R.id.txtLicenseName)
            val txtLicenseText: TextView = view.findViewById(R.id.txtLicenseText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_license, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val library = libraries[position]
            holder.txtName.text = library.name
            holder.txtLicenseName.text = library.licenseName
            holder.txtLicenseText.text = library.licenseText
        }

        override fun getItemCount() = libraries.size
    }
}
