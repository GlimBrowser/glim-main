package cn.ac.oac.glimbrowser.uiadaptive

/**
 * 中面板栏宽度策略（PRD V1.3 §3.5.1 Expanded 三栏细则：中面板栏「宽度可拖拽 280–480dp」）。
 * 纯逻辑：钳制拖拽值到合法区间，便于单测与跨端一致（移动端大屏 / 桌面窗口同策略）。
 */
object PanelColumnPolicy {
    /** 最小宽度（dp），PRD V1.3 §3.5.1 下限。 */
    const val MIN_WIDTH_DP: Int = 280

    /** 最大宽度（dp），PRD V1.3 §3.5.1 上限。 */
    const val MAX_WIDTH_DP: Int = 480

    /** 默认宽度（dp）：取区间中值偏下，兼顾原型「250px 示意下限」与可读性。 */
    const val DEFAULT_WIDTH_DP: Int = 320

    /** 将用户拖拽的宽度钳制到 [MIN_WIDTH_DP, MAX_WIDTH_DP]；非正值按默认宽度处理（防御式）。 */
    fun clamp(requestedWidthDp: Int): Int = when {
        requestedWidthDp <= 0 -> DEFAULT_WIDTH_DP
        requestedWidthDp < MIN_WIDTH_DP -> MIN_WIDTH_DP
        requestedWidthDp > MAX_WIDTH_DP -> MAX_WIDTH_DP
        else -> requestedWidthDp
    }
}
