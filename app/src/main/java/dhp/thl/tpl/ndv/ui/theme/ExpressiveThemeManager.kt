package dhp.thl.tpl.ndv.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.kieronquinn.monetcompat.core.MonetCompat
import dhp.thl.tpl.ndv.ZStickerApplication
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates and applies Material Design 3 Expressive tonal schemes across the entire app.
 *
 * Key design rule:
 * - When Dynamic Color (`material_color_enabled`) is ON: uses the OS / Monet wallpaper palette.
 * - When Dynamic Color is OFF: feeds stock Orange (`#F57C00`) through the EXACT same perceptual
 *   OKLab + CIE-L* Material 3 tonal palette algorithm as if `#F57C00` were extracted from the
 *   user's wallpaper.
 */
object ExpressiveThemeManager {

    enum class GroupPosition {
        TOP,
        MIDDLE,
        BOTTOM,
        SINGLE
    }

    @Volatile
    private var cachedOrangeLight: ExpressivePalette? = null

    @Volatile
    private var cachedOrangeDark: ExpressivePalette? = null

    fun isDarkTheme(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("material_color_enabled", false)
    }

    fun resolvePalette(
        context: Context,
        dynamicEnabled: Boolean = isDynamicColorEnabled(context),
        isDark: Boolean = isDarkTheme(context)
    ): ExpressivePalette {
        if (!dynamicEnabled) {
            return if (isDark) {
                cachedOrangeDark ?: generateFromSeed(ZStickerApplication.STOCK_ORANGE_SEED, true)
                    .also { cachedOrangeDark = it }
            } else {
                cachedOrangeLight ?: generateFromSeed(ZStickerApplication.STOCK_ORANGE_SEED, false)
                    .also { cachedOrangeLight = it }
            }
        }

        // Android 12+ (API 31+): read exact OS Material You tonal tables directly with 0ms latency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return resolveSystemDynamicPaletteApi31(context, isDark)
        }

        // Android 10–11 fallback via MonetCompat wallpaper extraction -> same perceptual tonal engine
        val seed = try {
            MonetCompat.getInstance().getAccentColor(context)
        } catch (_: Exception) {
            ZStickerApplication.STOCK_ORANGE_SEED
        }
        return generateFromSeed(seed, isDark)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun resolveSystemDynamicPaletteApi31(context: Context, isDark: Boolean): ExpressivePalette {
        fun sysColor(resId: Int): Int = ContextCompat.getColor(context, resId)

        val n1_0 = sysColor(android.R.color.system_neutral1_0)
        val n1_10 = sysColor(android.R.color.system_neutral1_10)
        val n1_50 = sysColor(android.R.color.system_neutral1_50)
        val n1_100 = sysColor(android.R.color.system_neutral1_100)
        val n1_800 = sysColor(android.R.color.system_neutral1_800)
        val n1_900 = sysColor(android.R.color.system_neutral1_900)
        val n1_1000 = sysColor(android.R.color.system_neutral1_1000)

        return if (isDark) {
            // Interpolate intermediate M3 surface container tones (T6, T10, T12, T17, T22) from OS neutral1 stops
            val surface = ColorUtils.blendARGB(n1_900, n1_1000, 0.40f) // ~T6
            val surfaceLowest = ColorUtils.blendARGB(n1_900, n1_1000, 0.60f) // ~T4
            val surfaceLow = n1_900 // T10
            val surfaceContainer = ColorUtils.blendARGB(n1_900, n1_800, 0.20f) // ~T12
            val surfaceHigh = ColorUtils.blendARGB(n1_900, n1_800, 0.70f) // ~T17
            val surfaceHighest = ColorUtils.blendARGB(n1_800, sysColor(android.R.color.system_neutral1_700), 0.20f) // ~T22

            ExpressivePalette(
                isDark = true,
                primary = sysColor(android.R.color.system_accent1_200),
                onPrimary = sysColor(android.R.color.system_accent1_800),
                primaryContainer = sysColor(android.R.color.system_accent1_700),
                onPrimaryContainer = sysColor(android.R.color.system_accent1_100),
                secondary = sysColor(android.R.color.system_accent2_200),
                onSecondary = sysColor(android.R.color.system_accent2_800),
                secondaryContainer = sysColor(android.R.color.system_accent2_700),
                onSecondaryContainer = sysColor(android.R.color.system_accent2_100),
                tertiary = sysColor(android.R.color.system_accent3_200),
                onTertiary = sysColor(android.R.color.system_accent3_800),
                tertiaryContainer = sysColor(android.R.color.system_accent3_700),
                onTertiaryContainer = sysColor(android.R.color.system_accent3_100),
                surface = surface,
                surfaceContainerLowest = surfaceLowest,
                surfaceContainerLow = surfaceLow,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceHigh,
                surfaceContainerHighest = surfaceHighest,
                onSurface = n1_100,
                onSurfaceVariant = sysColor(android.R.color.system_neutral2_200),
                outline = sysColor(android.R.color.system_neutral2_400),
                outlineVariant = sysColor(android.R.color.system_neutral2_700),
                error = Color.parseColor("#FFB4AB"),
                onError = Color.parseColor("#690005"),
                errorContainer = Color.parseColor("#93000A"),
                onErrorContainer = Color.parseColor("#FFDAD6")
            )
        } else {
            // Interpolate intermediate M3 light surface container tones (T98, T96, T94, T92, T90)
            val surface = ColorUtils.blendARGB(n1_10, n1_50, 0.25f) // ~T98
            val surfaceLowest = n1_0 // T100
            val surfaceLow = ColorUtils.blendARGB(n1_10, n1_50, 0.75f) // ~T96
            val surfaceContainer = ColorUtils.blendARGB(n1_50, n1_100, 0.20f) // ~T94
            val surfaceHigh = ColorUtils.blendARGB(n1_50, n1_100, 0.60f) // ~T92
            val surfaceHighest = n1_100 // T90

            ExpressivePalette(
                isDark = false,
                primary = sysColor(android.R.color.system_accent1_600),
                onPrimary = n1_0,
                primaryContainer = sysColor(android.R.color.system_accent1_100),
                onPrimaryContainer = sysColor(android.R.color.system_accent1_900),
                secondary = sysColor(android.R.color.system_accent2_600),
                onSecondary = n1_0,
                secondaryContainer = sysColor(android.R.color.system_accent2_100),
                onSecondaryContainer = sysColor(android.R.color.system_accent2_900),
                tertiary = sysColor(android.R.color.system_accent3_600),
                onTertiary = n1_0,
                tertiaryContainer = sysColor(android.R.color.system_accent3_100),
                onTertiaryContainer = sysColor(android.R.color.system_accent3_900),
                surface = surface,
                surfaceContainerLowest = surfaceLowest,
                surfaceContainerLow = surfaceLow,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceHigh,
                surfaceContainerHighest = surfaceHighest,
                onSurface = n1_900,
                onSurfaceVariant = sysColor(android.R.color.system_neutral2_700),
                outline = sysColor(android.R.color.system_neutral2_500),
                outlineVariant = sysColor(android.R.color.system_neutral2_200),
                error = Color.parseColor("#BA1A1A"),
                onError = Color.WHITE,
                errorContainer = Color.parseColor("#FFDAD6"),
                onErrorContainer = Color.parseColor("#410002")
            )
        }
    }

    /**
     * Generates a complete Material 3 TonalSpot / Expressive scheme from any ARGB seed color
     * using perceptual OKLab + CIE-L* gamut mapping.
     */
    fun generateFromSeed(@ColorInt seedColor: Int, isDark: Boolean): ExpressivePalette {
        val r = Color.red(seedColor)
        val g = Color.green(seedColor)
        val b = Color.blue(seedColor)

        val (_, okA, okB) = rgbToOklab(r, g, b)
        val seedHueRad = atan2(okB, okA)
        val seedChroma = hypot(okA, okB)

        val pChroma = max(0.145, seedChroma)
        val sChroma = 0.048
        val tChroma = 0.078
        val tHueRad = seedHueRad + Math.toRadians(55.0)
        val nChroma = 0.012
        val nvChroma = 0.022

        fun p(tone: Double) = toneToArgb(seedHueRad, pChroma, tone)
        fun s(tone: Double) = toneToArgb(seedHueRad, sChroma, tone)
        fun t(tone: Double) = toneToArgb(tHueRad, tChroma, tone)
        fun n(tone: Double) = toneToArgb(seedHueRad, nChroma, tone)
        fun nv(tone: Double) = toneToArgb(seedHueRad, nvChroma, tone)

        return if (isDark) {
            ExpressivePalette(
                isDark = true,
                primary = p(80.0),
                onPrimary = p(20.0),
                primaryContainer = p(30.0),
                onPrimaryContainer = p(90.0),
                secondary = s(80.0),
                onSecondary = s(20.0),
                secondaryContainer = s(30.0),
                onSecondaryContainer = s(90.0),
                tertiary = t(80.0),
                onTertiary = t(20.0),
                tertiaryContainer = t(30.0),
                onTertiaryContainer = t(90.0),
                surface = n(6.0),
                surfaceContainerLowest = n(4.0),
                surfaceContainerLow = n(10.0),
                surfaceContainer = n(12.0),
                surfaceContainerHigh = n(17.0),
                surfaceContainerHighest = n(22.0),
                onSurface = n(90.0),
                onSurfaceVariant = nv(80.0),
                outline = nv(60.0),
                outlineVariant = nv(30.0),
                error = Color.parseColor("#FFB4AB"),
                onError = Color.parseColor("#690005"),
                errorContainer = Color.parseColor("#93000A"),
                onErrorContainer = Color.parseColor("#FFDAD6")
            )
        } else {
            ExpressivePalette(
                isDark = false,
                primary = p(40.0),
                onPrimary = Color.WHITE,
                primaryContainer = p(90.0),
                onPrimaryContainer = p(10.0),
                secondary = s(40.0),
                onSecondary = Color.WHITE,
                secondaryContainer = s(90.0),
                onSecondaryContainer = s(10.0),
                tertiary = t(40.0),
                onTertiary = Color.WHITE,
                tertiaryContainer = t(90.0),
                onTertiaryContainer = t(10.0),
                surface = n(98.0),
                surfaceContainerLowest = Color.WHITE,
                surfaceContainerLow = n(96.0),
                surfaceContainer = n(94.0),
                surfaceContainerHigh = n(92.0),
                surfaceContainerHighest = n(90.0),
                onSurface = n(10.0),
                onSurfaceVariant = nv(30.0),
                outline = nv(50.0),
                outlineVariant = nv(80.0),
                error = Color.parseColor("#BA1A1A"),
                onError = Color.WHITE,
                errorContainer = Color.parseColor("#FFDAD6"),
                onErrorContainer = Color.parseColor("#410002")
            )
        }
    }

    /**
     * Styles a MaterialCardView as an Android 16 Material 3 Expressive Segmented Grouped List row
     * (28dp outer corners, 6dp inner corners, tonal surface fill, ripple, and zero border).
     */
    fun styleSegmentedCard(
        card: MaterialCardView,
        position: GroupPosition,
        palette: ExpressivePalette,
        @ColorInt containerColor: Int = palette.surfaceContainerLow,
        @ColorInt rippleBaseColor: Int = palette.primary
    ) {
        val density = card.resources.displayMetrics.density
        val outerRadius = 28f * density
        val innerRadius = 6f * density

        val (topLeft, topRight, bottomRight, bottomLeft) = when (position) {
            GroupPosition.TOP -> floatArrayOf(outerRadius, outerRadius, innerRadius, innerRadius)
            GroupPosition.MIDDLE -> floatArrayOf(innerRadius, innerRadius, innerRadius, innerRadius)
            GroupPosition.BOTTOM -> floatArrayOf(innerRadius, innerRadius, outerRadius, outerRadius)
            GroupPosition.SINGLE -> floatArrayOf(outerRadius, outerRadius, outerRadius, outerRadius)
        }

        card.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, topLeft)
            .setTopRightCorner(CornerFamily.ROUNDED, topRight)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottomRight)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottomLeft)
            .build()

        card.setCardBackgroundColor(containerColor)
        card.strokeWidth = 0
        card.cardElevation = 0f
        card.rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(rippleBaseColor, if (palette.isDark) 52 else 36)
        )
    }

    /**
     * Styles a leading icon inside an M3 Expressive 14dp tonal squircle badge.
     */
    fun styleSquircleIcon(
        imageView: ImageView,
        @ColorInt badgeBgColor: Int,
        @ColorInt iconTint: Int,
        tintIcon: Boolean = true
    ) {
        val density = imageView.resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(badgeBgColor)
        }
        imageView.background = bg
        if (tintIcon) {
            imageView.setColorFilter(iconTint)
        } else {
            imageView.clearColorFilter()
        }
    }

    /**
     * Styles a MaterialSwitch consistently using the exact Material 3 tonal tokens.
     */
    fun styleSwitch(switchView: MaterialSwitch, palette: ExpressivePalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(palette.onPrimary, palette.outline)
        val trackColors = intArrayOf(palette.primary, palette.surfaceContainerHighest)
        val trackStrokeColors = intArrayOf(Color.TRANSPARENT, palette.outline)
        val iconColors = intArrayOf(palette.primary, palette.surfaceContainerHighest)

        switchView.thumbTintList = ColorStateList(states, thumbColors)
        switchView.trackTintList = ColorStateList(states, trackColors)
        switchView.trackDecorationTintList = ColorStateList(states, trackStrokeColors)
        switchView.thumbIconTintList = ColorStateList(states, iconColors)
    }

    fun createPillRipple(
        @ColorInt fillColor: Int,
        @ColorInt rippleColor: Int,
        cornerRadiusPx: Float
    ): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(fillColor)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    // ---- Perceptual OKLab + CIE-L* Solver ----

    private fun srgbToLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(c: Double): Int {
        val clamped = min(1.0, max(0.0, c))
        val v = if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
        return (v * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun rgbToOklab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)

        val l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
        val m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
        val s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb

        val lC = cbrt(l)
        val mC = cbrt(m)
        val sC = cbrt(s)

        val okL = 0.2104542553 * lC + 0.7936177850 * mC - 0.0040720468 * sC
        val okA = 1.9779984951 * lC - 2.4285922050 * mC + 0.4505937099 * sC
        val okB = 0.0259040371 * lC + 0.7827717662 * mC - 0.8086757660 * sC
        return Triple(okL, okA, okB)
    }

    private fun oklabToLinearRgb(okL: Double, okA: Double, okB: Double): Triple<Double, Double, Double> {
        val lC = okL + 0.3963377774 * okA + 0.2158037573 * okB
        val mC = okL - 0.1055613458 * okA - 0.0638541728 * okB
        val sC = okL - 0.0894841775 * okA - 1.2914855480 * okB

        val l = lC * lC * lC
        val m = mC * mC * mC
        val s = sC * sC * sC

        val lr = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Triple(lr, lg, lb)
    }

    private fun cieLstarToOklabL(tone: Double): Double {
        val y = if (tone > 8.0) {
            ((tone + 16.0) / 116.0).pow(3.0)
        } else {
            tone / 903.2962962
        }
        return cbrt(y)
    }

    @ColorInt
    private fun toneToArgb(hueRad: Double, targetChroma: Double, tone: Double): Int {
        if (tone <= 0.0) return Color.BLACK
        if (tone >= 100.0) return Color.WHITE

        val okL = cieLstarToOklabL(tone)
        val cosH = cos(hueRad)
        val sinH = sin(hueRad)

        var lo = 0.0
        var hi = targetChroma
        repeat(16) {
            val mid = (lo + hi) * 0.5
            val (lr, lg, lb) = oklabToLinearRgb(okL, mid * cosH, mid * sinH)
            if (lr in -0.0005..1.0005 && lg in -0.0005..1.0005 && lb in -0.0005..1.0005) {
                lo = mid
            } else {
                hi = mid
            }
        }
        val (lr, lg, lb) = oklabToLinearRgb(okL, lo * cosH, lo * sinH)
        return Color.rgb(linearToSrgb(lr), linearToSrgb(lg), linearToSrgb(lb))
    }
}
