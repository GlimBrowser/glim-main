package cn.ac.oac.glimbrowser

import cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 1 主题模式契约单测（PRD V1.3 §3.5.3：昼/夜/护眼/跟随系统）。
 * 色值本身在 Color.kt 逐 token 对照原型，无纯函数可测，这里锁住枚举契约防回归。
 */
class GlimThemeModeTest {

    @Test
    fun allFourModesPresent() {
        val modes = GlimThemeMode.entries.toSet()
        assertEquals(
            setOf(GlimThemeMode.LIGHT, GlimThemeMode.DARK, GlimThemeMode.SYSTEM, GlimThemeMode.EYE_CARE),
            modes,
        )
    }

    @Test
    fun systemModeIsDistinctFromHardcodedLightDark() {
        // SYSTEM 必须是独立状态（不能与 LIGHT/DARK 合并），否则"跟随系统"无法作为默认值表达。
        assertTrue(GlimThemeMode.SYSTEM != GlimThemeMode.LIGHT)
        assertTrue(GlimThemeMode.SYSTEM != GlimThemeMode.DARK)
    }
}
