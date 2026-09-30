package com.ctos.client.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The brand scale (hue 268), computed from the same oklch values as the website's --color-brand-*
 * (see resources/css/app.css). It does not change between light and dark mode, only how it is
 * used does (see [CtosColors]).
 */
private object Brand {
    val S50 = Color(0xFFF0F5FF)
    val S100 = Color(0xFFE1EBFF)
    val S200 = Color(0xFFC4D7FF)
    val S300 = Color(0xFF99B7FF)
    val S500 = Color(0xFF4C71FB)
    val S600 = Color(0xFF3656EE)
    val S700 = Color(0xFF2943CA)
    val S800 = Color(0xFF223899)
    val S950 = Color(0xFF101C4C)
}

/** One full set of [CtosColors]' values: what light mode uses, and what dark mode uses. */
private class Palette(
    val primary: Color, val primaryHover: Color, val primary50: Color, val primary100: Color, val onPrimary: Color,
    val canvas: Color, val surface: Color, val surfaceAlt: Color, val border: Color, val borderStrong: Color,
    val text: Color, val textMuted: Color, val textSubtle: Color,
    val success: Color, val successSoft: Color, val warning: Color, val warningSoft: Color,
    val danger: Color, val dangerSoft: Color, val scrim: Color,
)

// The website's own light palette (resources/views, plain slate/emerald/amber/red Tailwind classes
// with no dark: prefix): see CtosColors' doc comment for where each value comes from.
private val LightPalette = Palette(
    primary = Brand.S600, primaryHover = Brand.S700, primary50 = Brand.S50, primary100 = Brand.S100, onPrimary = Color.White,
    canvas = Color(0xFFF8FAFC), surface = Color.White, surfaceAlt = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0), borderStrong = Color(0xFFCBD5E1),
    text = Color(0xFF0F172A), textMuted = Color(0xFF475569), textSubtle = Color(0xFF94A3B8),
    success = Color(0xFF047857), successSoft = Color(0xFFD1FAE5),
    warning = Color(0xFFB45309), warningSoft = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626), dangerSoft = Color(0xFFFEE2E2),
    scrim = Color(0x990F172A),
)

// The website's dark: classes on the same elements: bg-slate-950 canvas, bg-slate-900 surfaces,
// bg-slate-800 borders/alt-rows, text-slate-100/400, brand text lightened to brand-300 (a filled
// button stays the same brand-600: it already reads fine on a dark background), and each status
// color as a light shade of itself over a translucent version of its own mid tone.
private val DarkPalette = Palette(
    primary = Brand.S600, primaryHover = Brand.S300, primary50 = Brand.S500.copy(alpha = 0.14f), primary100 = Brand.S500.copy(alpha = 0.24f), onPrimary = Color.White,
    canvas = Color(0xFF020617), surface = Color(0xFF0F172A), surfaceAlt = Color(0xFF1E293B),
    border = Color(0xFF1E293B), borderStrong = Color(0xFF334155),
    text = Color(0xFFF1F5F9), textMuted = Color(0xFF94A3B8), textSubtle = Color(0xFF64748B),
    success = Color(0xFFA7F3D0), successSoft = Color(0xFF10B981).copy(alpha = 0.16f),
    warning = Color(0xFFFDE68A), warningSoft = Color(0xFFF59E0B).copy(alpha = 0.16f),
    danger = Color(0xFFFECACA), dangerSoft = Color(0xFFEF4444).copy(alpha = 0.16f),
    scrim = Color(0xCC000000),
)

/**
 * The design tokens of the app: one palette, one type scale, one set of shapes. Screens and
 * widgets take every color, size and radius from here instead of writing their own numbers, so
 * the app looks like one product, and like the website — including its dark mode: [AppTheme]
 * switches every value here to match the system, the same way the website's `dark:` classes do.
 *
 * Every property is backed by [mutableStateOf], so a screen that reads e.g. `CtosColors.Canvas`
 * during composition is automatically recomposed when the mode changes, without needing a
 * CompositionLocal or to thread colors through every function's parameters.
 */
object CtosColors {
    var Primary by mutableStateOf(LightPalette.primary); private set
    var PrimaryHover by mutableStateOf(LightPalette.primaryHover); private set
    var Primary50 by mutableStateOf(LightPalette.primary50); private set
    var Primary100 by mutableStateOf(LightPalette.primary100); private set
    var OnPrimary by mutableStateOf(LightPalette.onPrimary); private set

    // Fixed regardless of light/dark mode: the always-dark brand hero panel on the login screen
    // is deliberately styled that way itself, not tied to the app's overall theme.
    val Primary200 = Brand.S200
    val Primary800 = Brand.S800
    val Primary950 = Brand.S950

    var Canvas by mutableStateOf(LightPalette.canvas); private set
    var Surface by mutableStateOf(LightPalette.surface); private set
    var SurfaceAlt by mutableStateOf(LightPalette.surfaceAlt); private set
    var Border by mutableStateOf(LightPalette.border); private set
    var BorderStrong by mutableStateOf(LightPalette.borderStrong); private set

    var Text by mutableStateOf(LightPalette.text); private set
    var TextMuted by mutableStateOf(LightPalette.textMuted); private set
    var TextSubtle by mutableStateOf(LightPalette.textSubtle); private set

    var Success by mutableStateOf(LightPalette.success); private set
    var SuccessSoft by mutableStateOf(LightPalette.successSoft); private set
    var Warning by mutableStateOf(LightPalette.warning); private set
    var WarningSoft by mutableStateOf(LightPalette.warningSoft); private set
    var Danger by mutableStateOf(LightPalette.danger); private set
    var DangerSoft by mutableStateOf(LightPalette.dangerSoft); private set

    var Scrim by mutableStateOf(LightPalette.scrim); private set

    /** Switches every color above to [dark]'s palette. Called once per frame by [AppTheme]; cheap
     * and idempotent, so it never needs its own "did this already happen" bookkeeping. */
    fun useDark(dark: Boolean) {
        val p = if (dark) DarkPalette else LightPalette
        Primary = p.primary; PrimaryHover = p.primaryHover
        Primary50 = p.primary50; Primary100 = p.primary100; OnPrimary = p.onPrimary
        Canvas = p.canvas; Surface = p.surface; SurfaceAlt = p.surfaceAlt
        Border = p.border; BorderStrong = p.borderStrong
        Text = p.text; TextMuted = p.textMuted; TextSubtle = p.textSubtle
        Success = p.success; SuccessSoft = p.successSoft
        Warning = p.warning; WarningSoft = p.warningSoft
        Danger = p.danger; DangerSoft = p.dangerSoft
        Scrim = p.scrim
    }
}

/** Corner radii. Cards are the largest; controls are a step smaller. */
object CtosShapes {
    val Card = RoundedCornerShape(16.dp)
    val Control = RoundedCornerShape(12.dp)
    val Small = RoundedCornerShape(8.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * The type scale. Sizes are deliberately much larger than a typical desktop app's: the app is
 * read at arm's length as often as at a desk, so everything here should be comfortable to read
 * without leaning in. Every screen and widget takes its text size from here, so raising it here
 * raises it everywhere at once.
 *
 * The family is deliberately not the website's own web font (Instrument Sans): the app uses each
 * platform's native system font instead, which is already installed, renders instantly with no
 * download or bundled font file, and looks at home next to every other app on that device. A
 * website and a native app are different mediums, so "styled like the website" (the color palette
 * above, the same brand and layout language) stops at typeface — matching fonts pixel-for-pixel
 * would need shipping Instrument Sans as a font resource for comparatively little benefit.
 */
/**
 * A compact, uniform scale anchored on the sidebar's own text size (Body, the same size the
 * sidebar's navigation items use): nothing towers over it, so headings read as "the same text,
 * a bit bolder" rather than a completely different size.
 */
object CtosText {
    private val family = FontFamily.Default

    val Display = TextStyle(fontFamily = family, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val PageTitle = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val SectionTitle = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Small = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val SmallStrong = Small.copy(fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val Button = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}

// Material's own typography slots are mapped onto the same scale, so any stock Material
// component that is used still matches the rest of the UI.
private val CtosTypography = Typography(
    displayLarge = CtosText.Display,
    headlineLarge = CtosText.PageTitle,
    headlineMedium = CtosText.PageTitle,
    headlineSmall = CtosText.SectionTitle,
    titleLarge = CtosText.PageTitle,
    titleMedium = CtosText.SectionTitle,
    titleSmall = CtosText.BodyStrong,
    bodyLarge = CtosText.Body,
    bodyMedium = CtosText.Body,
    bodySmall = CtosText.Small,
    labelLarge = CtosText.Button,
    labelMedium = CtosText.SmallStrong,
    labelSmall = CtosText.Caption,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Always dark, matching the website's own dark: palette, the same way it would look with the
    // OS set to dark mode — not tied to system detection: Compose Desktop's isSystemInDarkTheme()
    // does not reliably read the actual OS setting on every platform (notably Linux desktops),
    // so a build that depended on it could silently stay light no matter what the system said.
    CtosColors.useDark(true)

    val colorScheme = darkColorScheme(
        primary = CtosColors.Primary, onPrimary = CtosColors.OnPrimary,
        primaryContainer = CtosColors.Primary50, onPrimaryContainer = CtosColors.PrimaryHover,
        secondary = CtosColors.Primary, onSecondary = CtosColors.OnPrimary,
        background = CtosColors.Canvas, onBackground = CtosColors.Text,
        surface = CtosColors.Surface, onSurface = CtosColors.Text,
        surfaceVariant = CtosColors.SurfaceAlt, onSurfaceVariant = CtosColors.TextMuted,
        outline = CtosColors.Border, error = CtosColors.Danger, onError = CtosColors.OnPrimary,
        scrim = CtosColors.Scrim,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CtosTypography,
        content = content,
    )
}
