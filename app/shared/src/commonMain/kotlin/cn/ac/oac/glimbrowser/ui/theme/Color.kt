package cn.ac.oac.glimbrowser.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 设计令牌 · 色彩（唯一真源：docs/原型图.html §01 设计风格系统 / TOKENS）。
 * 六形态（手机/Pad/macOS/Windows）共用同一套昼/夜主题（原型页脚说明）。
 * 8pt 栅格 + 强调色克制原则：强调色仅用于 ✨按钮、护盾数字、要点左线、主按钮、开态开关（原型注释 06）。
 */

// ===== 昼（light，原型 :root） =====
/** 纸面 / 主背景 light #F2EFE7 */
val PaperLight = Color(0xFFF2EFE7)
/** 浮层 / 卡片面 light #FAF8F3 */
val SurfaceLight = Color(0xFFFAF8F3)
/** 次级面（原型 --paper-3）light #E9E5DA */
val SurfaceVariantLight = Color(0xFFEAE6DB)
/** 墨色 / 主文字 light #15181B */
val InkLight = Color(0xFF14171A)
/** 次文字 light #3E444A（--ink-2） */
val InkSecondaryLight = Color(0xFF3C4247)
/** 弱文字 light #7D828A（--ink-3） */
val InkTertiaryLight = Color(0xFF7A7F86)
/** 玉青 / 强调 light #1E6B58（AI·护盾·主按钮） */
val JadeLight = Color(0xFF1A6452)
/** 玉青-2 light #2C8F76（悬停/次强调） */
val JadeBrightLight = Color(0xFF238068)
/** 琥珀 / 警示 light #A96B1B（误杀提示·降级告知） */
val AmberLight = Color(0xFFA06518)
/** 陶红 / 危险 light #9E3B2D（错误·删除·拦截失败） */
val DangerLight = Color(0xFF96382A)

// ===== 夜（dark，原型 [data-theme="dark"]） =====
/** 纸面 / 主背景 dark #0D1110 */
val PaperDark = Color(0xFF0B0E0D)
/** 浮层 / 卡片面 dark #161B19 */
val SurfaceDark = Color(0xFF151A18)
/** 次级面 dark #1E2422（--paper-3） */
val SurfaceVariantDark = Color(0xFF1C2220)
/** 墨色 / 主文字 dark #EBE7DC */
val InkDark = Color(0xFFEAE6DB)
/** 次文字 dark #B6B4AA（--ink-2） */
val InkSecondaryDark = Color(0xFFB2B0A6)
/** 弱文字 dark #787D79（--ink-3） */
val InkTertiaryDark = Color(0xFF74787E)
/** 玉青 / 强调 dark #56B397 */
val JadeDark = Color(0xFF4CAF94)
/** 玉青-2 dark #7ACBB1 */
val JadeBrightDark = Color(0xFF6BC4A8)
/** 琥珀 / 警示 dark #D9A25B */
val AmberDark = Color(0xFFD4995A)
/** 陶红 / 危险 dark #D4796A */
val DangerDark = Color(0xFFCF7365)

// ===== 护眼（EYE_CARE，PRD V1.3 §3.5.3）：黄绿纸感低蓝光色板（108 实装，去占位）=====
val PaperEyeCare = Color(0xFFF0EAD2)
val SurfaceEyeCare = Color(0xFFF7F2E2)
val SurfaceVariantEyeCare = Color(0xFFE3DCC2)
val InkEyeCare = Color(0xFF23281F)
val InkSecondaryEyeCare = Color(0xFF4A5140)
val InkTertiaryEyeCare = Color(0xFF7C8370)
val JadeEyeCare = Color(0xFF4A6B34)
val JadeBrightEyeCare = Color(0xFF5E8542)
val AmberEyeCare = Color(0xFF8A6A1E)
val DangerEyeCare = Color(0xFF8C3B2B)

// ===== 毛玻璃背景（组件库 --glass / --glass-line；带主题透明度）=====
val GlassLight = Color(0xFFFAF8F3).copy(alpha = 0.72f)
val GlassLightSoft = Color(0xFFFAF8F3).copy(alpha = 0.55f)
val GlassDark = Color(0xFF151A18).copy(alpha = 0.72f)
val GlassDarkSoft = Color(0xFF151A18).copy(alpha = 0.55f)
val GlassLineLight = Color(0xFF14171A).copy(alpha = 0.08f)
val GlassLineDark = Color(0xFFEAE6DB).copy(alpha = 0.10f)
