package dhp.thl.tpl.ndv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.kieronquinn.monetcompat.extensions.views.enableStretchOverscroll
import dhp.thl.tpl.ndv.ui.theme.ExpressiveMotion
import dhp.thl.tpl.ndv.ui.theme.ExpressivePalette
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager.GroupPosition

class LicenseActivity : BaseActivity() {

    data class Library(val name: String, val licenseName: String, val licenseText: String)

    private val heroPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        innerRadius = 0.84f,
        rounding = CornerRounding(0.20f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(R.layout.activity_license)

        val palette = ExpressiveThemeManager.resolvePalette(this)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !palette.isDark
        insetsController.isAppearanceLightNavigationBars = !palette.isDark

        val root = findViewById<View>(R.id.licenseRoot)
        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val headerRow = findViewById<View>(R.id.headerRow)
        val btnBack = findViewById<MaterialCardView>(R.id.btnBack)
        val imgBack = findViewById<ImageView>(R.id.imgBack)
        val txtHeaderTitle = findViewById<TextView>(R.id.txtHeaderTitle)
        val scrollView = findViewById<NestedScrollView>(R.id.licenseScroll)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val headerIcon = findViewById<ImageView>(R.id.imgHeaderIcon)
        val txtContentTitle = findViewById<TextView>(R.id.txtContentTitle)
        val txtContentNote = findViewById<TextView>(R.id.txtContentNote)

        scrollView.enableStretchOverscroll()
        ExpressiveMotion.attachPressSpring(btnBack, 0.90f)
        btnBack.setOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val topInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = topInsets.left, top = topInsets.top, right = topInsets.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = navInsets.left, right = navInsets.right, bottom = navInsets.bottom)
            insets
        }

        // Apply M3 Expressive palette
        root.setBackgroundColor(palette.surface)
        appBarLayout.setBackgroundColor(palette.surface)
        headerRow.setBackgroundColor(palette.surface)
        txtHeaderTitle.setTextColor(palette.onSurface)

        btnBack.setCardBackgroundColor(palette.secondaryContainer)
        btnBack.strokeWidth = 0
        imgBack.setColorFilter(palette.onSecondaryContainer)

        headerIcon.background = PolygonDrawable(heroPolygon, palette.secondaryContainer)
        headerIcon.setColorFilter(palette.onSecondaryContainer)
        ExpressiveMotion.springPopIn(headerIcon, 0.82f)

        txtContentTitle.setTextColor(palette.onSurface)
        txtContentNote.setTextColor(palette.onSurfaceVariant)

        val libraries = listOf(
            Library("uCrop", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Activity", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX AppCompat", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX ConstraintLayout", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Core", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX DynamicAnimation", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX ExifInterface", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Fragment", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Graphics Shapes", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Lifecycle", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("AndroidX Splash Screen", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Google MediaPipe Tasks Vision", "Apache 2.0", "Licensed under the Apache License, Version 2.0. Source: https://github.com/google-ai-edge/mediapipe"),
            Library("Glide", "BSD/MIT/Apache", "License information for Glide can be found at: https://github.com/bumptech/glide/blob/master/LICENSE"),
            Library("Kotlin Coroutines", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Kotlin Standard Library", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Material Components", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Material 3 Expressive", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Jetpack Compose", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Activity Compose", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Lifecycle Runtime Compose", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("MonetCompat", "MIT License", "Copyright (c) 2021 Kieron Quinn\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software."),
            Library("UI Graphics", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("UI Tooling Preview", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Zip4j", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("Android Video Trimmer", "MIT", "Copyright (c) 2020 a914-gowtham. Licensed under the MIT License. Source: https://github.com/a914-gowtham/android-video-trimmer"),
            Library("AndroidX Media3", "Apache 2.0", "Licensed under the Apache License, Version 2.0"),
            Library("ExoPlayer", "Apache 2.0", "Licensed under the Apache License, Version 2.0")
        ).sortedBy { it.name.lowercase() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = LicenseAdapter(libraries, palette)
    }

    private class LicenseAdapter(
        private val libraries: List<Library>,
        private val palette: ExpressivePalette
    ) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardLicenseItem)
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
            val density = holder.itemView.resources.displayMetrics.density

            val groupPos = when {
                libraries.size == 1 -> GroupPosition.SINGLE
                position == 0 -> GroupPosition.TOP
                position == libraries.lastIndex -> GroupPosition.BOTTOM
                else -> GroupPosition.MIDDLE
            }
            ExpressiveThemeManager.styleSegmentedCard(holder.card, groupPos, palette)

            holder.txtName.text = library.name
            holder.txtName.setTextColor(palette.onSurface)

            holder.txtLicenseName.text = library.licenseName
            holder.txtLicenseName.setTextColor(palette.onTertiaryContainer)
            holder.txtLicenseName.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f * density
                setColor(palette.tertiaryContainer)
            }

            holder.txtLicenseText.text = library.licenseText
            holder.txtLicenseText.setTextColor(palette.onSurfaceVariant)
        }

        override fun getItemCount(): Int = libraries.size
    }
}
