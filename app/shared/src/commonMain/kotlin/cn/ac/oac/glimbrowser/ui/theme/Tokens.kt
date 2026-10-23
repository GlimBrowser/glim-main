package cn.ac.oac.glimbrowser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 设计令牌 · 形状/间距/层级（唯一真源：docs/原型图.html :root）。
 * 8pt 栅格基线（原型 SCALE 注释：4/8/14/22/36/60/96 — 8pt 基线；间距 token 与之一致）。
 */
object GlimTokens {

    /** 圆角（原型 --r-sm/md/lg/xl）。 */
    val RadiusSm = RoundedCornerShape(8.dp)    // --r-sm
    val RadiusMd = RoundedCornerShape(14.dp)   // --r-md（卡片）
    val RadiusLg = RoundedCornerShape(22.dp)   // --r-lg（面板/抽屉）
    val RadiusXl = RoundedCornerShape(32.dp)   // --r-xl
    /** 胶囊/地址栏/按钮（原型 .searchbar/.addr/.gbtn/.chip 用 999px）。 */
    val RadiusPill = RoundedCornerShape(percent = 50)

    /** 间距阶梯（原型 --s1..--s7：4/8/14/22/36/60/96）。 */
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 14.dp
    val Space4 = 22.dp
    val Space5 = 36.dp
    val Space6 = 60.dp
    val Space7 = 96.dp

    /** 阴影（原型 --shadow / --shadow-sm，Compose 侧用 elevation 近似，此处仅记录数值供各端对齐）。 */
    const val ElevationSm = 4    // --shadow-sm: 0 8px 24px
    const val ElevationLg = 24   // --shadow: 0 30px 70px（抽屉/浮层用）

    /** AI 抽屉占屏比例（PRD V1.1 §3.2.4「侧边 AI 抽屉占屏 55%」，原型 .drawer height:56%）。 */
    const val AiSheetHeightFraction = 0.55f

    // ===== V2.0 Safari-inspired 组件库令牌（毛玻璃 / 圆角阶梯 / 阴影）=====
    /** 圆角阶梯（组件库 --r-xs/sm/md/lg/xl）。 */
    val RadiusXs = RoundedCornerShape(8.dp)      // 原型 --r-xs
    val Radius12 = RoundedCornerShape(12.dp)     // 原型 --r-sm
    val Radius6 = RoundedCornerShape(6.dp)
    val RadiusCard = RoundedCornerShape(16.dp)   // 原型 --r-md
    val RadiusPanel = RoundedCornerShape(22.dp)  // 原型 --r-lg
    val RadiusXl28 = RoundedCornerShape(28.dp)   // 原型 --r-xl（底部抽屉）

    /** 毛玻璃透明度（昼间：surface 72%/55%/40%）。 */
    const val GlassAlphaStrong = 0.72f
    const val GlassAlphaMedium = 0.55f
    const val GlassAlphaLight = 0.40f

    /** 浮层/工具栏高度（组件库 toolbar 约 52dp、tab strip 34dp）。 */
    val ToolbarHeight = 52.dp
    val TabStripHeight = 36.dp
    val IconButtonSize = 32.dp

    /** 中面板栏宽度区间（PRD V1.3 §3.5.1，与 shared:ui-adaptive PanelColumnPolicy 保持一致，供 Compose 布局直接引用）。 */
    const val PanelColumnMinWidthDp = 280
    const val PanelColumnMaxWidthDp = 480
}
