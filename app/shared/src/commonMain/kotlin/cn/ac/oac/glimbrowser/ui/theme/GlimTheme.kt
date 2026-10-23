package cn.ac.oac.glimbrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * GlimBrowser 昼/夜主题（PRD V1.3 §3.5.3：昼/夜/护眼/跟随系统全平台一致）。
 * 色值唯一真源：docs/原型图.html（Color.kt 逐 token 摘录）。
 * Phase 1 交付：昼/夜切换 + 跟随系统；护眼主题后续阶段补（见 000 §4 Phase 1 范围边界）。
 */
private val LightColors: ColorScheme = lightColorScheme(
    primary = JadeLight,
    onPrimary = SurfaceLight,
    primaryContainer = JadeLight,
    onPrimaryContainer = PaperLight,
    secondary = JadeBrightLight,
    onSecondary = PaperLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkSecondaryLight,
    outline = InkTertiaryLight,
    error = DangerLight,
    onError = PaperLight,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = JadeDark,
    onPrimary = PaperDark,
    primaryContainer = JadeDark,
    onPrimaryContainer = PaperDark,
    secondary = JadeBrightDark,
    onSecondary = PaperDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkSecondaryDark,
    outline = InkTertiaryDark,
    error = DangerDark,
    onError = PaperDark,
)

/**
 * 主题模式（PRD V1.3 §3.5.3：昼/夜/护眼/跟随系统）。
 * Phase 1：LIGHT/DARK/SYSTEM 三态；EYE_CARE（护眼）占位，后续阶段实现。
 */
enum class GlimThemeMode {
    LIGHT,
    DARK,
    /** 跟随系统（isSystemInDarkTheme）。 */
    SYSTEM,
    /** 护眼（PRD V1.3 §3.5.3，Phase 1 暂按 LIGHT 呈现，后续补黄绿纸感色板）。 */
    EYE_CARE,
}

/**
 * GlimBrowser 主题挂点（全平台一致，六形态共用）。
 *
 * @param themeMode 昼/夜/系统/护眼；默认 SYSTEM。
 * @param content 根内容。
 */
@Composable
fun GlimTheme(
    themeMode: GlimThemeMode = GlimThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        GlimThemeMode.LIGHT -> false
        GlimThemeMode.DARK -> true
        GlimThemeMode.SYSTEM -> isSystemInDarkTheme()
        GlimThemeMode.EYE_CARE -> false   // 护眼=低蓝光浅色纸感（非夜间）
    }
    // 记录当前深浅（供 WebView 等非 Composable 场景读取）
    ThemeState.isDark = dark
    val scheme = when (themeMode) {
        GlimThemeMode.EYE_CARE -> EyeCareColors
        else -> if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}

/** 护眼纸感色板（低蓝光；PRD V1.3 §3.5.3）。 */
private val EyeCareColors: ColorScheme = LightColors.copy(
    background = cn.ac.oac.glimbrowser.ui.theme.PaperEyeCare,
    surface = cn.ac.oac.glimbrowser.ui.theme.SurfaceEyeCare,
    surfaceVariant = cn.ac.oac.glimbrowser.ui.theme.SurfaceVariantEyeCare,
    onBackground = cn.ac.oac.glimbrowser.ui.theme.InkEyeCare,
    onSurface = cn.ac.oac.glimbrowser.ui.theme.InkEyeCare,
    onSurfaceVariant = cn.ac.oac.glimbrowser.ui.theme.InkSecondaryEyeCare,
    primary = cn.ac.oac.glimbrowser.ui.theme.JadeEyeCare,
    onPrimary = cn.ac.oac.glimbrowser.ui.theme.SurfaceEyeCare,
    outline = cn.ac.oac.glimbrowser.ui.theme.InkTertiaryEyeCare,
    error = cn.ac.oac.glimbrowser.ui.theme.DangerEyeCare,
)
