package cn.ac.oac.glimbrowser.uiadaptive

/**
 * 自适应布局推导结果（SizeClass → 布局决策，PRD V1.3 §3.5.1）。
 *
 * 本类型只描述"该显示什么"，不涉及具体 Compose 组件，保证纯逻辑可单测
 * （PRD V1.3 §2.3 架构要点 2：形态判定、分栏策略、导航模式切换为纯逻辑，可单测）。
 */
data class AdaptiveLayout(
    /** 窗口宽度分级。 */
    val widthClass: WindowWidthClass,
    /** 由分级推导的导航模式。 */
    val navigationMode: NavigationMode,
    /** 是否启用 Expanded 三栏的中面板栏（标签卡片列表 / AI 抽屉 / 净化报告）。 */
    val showPanelColumn: Boolean,
    /** 是否启用左导航图标轨（可折叠，折叠为图标轨，PRD §3.5.1 Expanded 三栏）。 */
    val showNavigationRail: Boolean,
    /** AI 面板呈现方式：半屏停靠（Medium）/ 右侧常驻停靠（Expanded）/ 不适用（Compact 走抽屉）。 */
    val aiPanelPresentation: AiPanelPresentation,
    /** 内容信息密度：桌面/大屏默认紧凑，手机舒适（PRD V1.3 §3.5.3 主题与密度）。 */
    val density: UiDensity,
)

/**
 * AI 面板呈现方式（PRD V1.1 §3.2.4 轻/重入口 + PRD V1.3 §3.5.1 Medium「可停靠半屏」）。
 */
enum class AiPanelPresentation {
    /** Compact：侧边抽屉（占屏约 55%，PRD V1.1 §3.2.4 重入口）。 */
    BOTTOM_SHEET,

    /** Medium：可停靠半屏（不遮挡正文）。 */
    DOCKED_HALF,

    /** Expanded：右侧常驻停靠栏（对齐原型「AI DOCK」）。 */
    DOCKED_SIDE,
}

/**
 * 界面密度（PRD V1.3 §3.5.3：桌面支持"界面密度"选项（舒适/紧凑），紧凑模式减小行高与侧栏宽度）。
 */
enum class UiDensity {
    COMFORTABLE,
    COMPACT,
}

/**
 * 由窗口宽度（dp）推导完整布局决策（纯函数，可单测；Compose 侧只需读取结果渲染）。
 */
fun deriveAdaptiveLayout(widthDp: Int): AdaptiveLayout = deriveAdaptiveLayout(WindowWidthClass.fromWidthDp(widthDp))

fun deriveAdaptiveLayout(widthClass: WindowWidthClass): AdaptiveLayout = AdaptiveLayout(
    widthClass = widthClass,
    // Pad/桌面：MEDIUM 起即使用三栏 + 侧栏（用户要求"Pad 端布局同桌面端"）
    navigationMode = when (widthClass) {
        WindowWidthClass.COMPACT -> NavigationMode.BOTTOM_BAR
        WindowWidthClass.MEDIUM, WindowWidthClass.EXPANDED -> NavigationMode.THREE_PANE
    },
    showPanelColumn = widthClass != WindowWidthClass.COMPACT,
    showNavigationRail = widthClass != WindowWidthClass.COMPACT,
    aiPanelPresentation = when (widthClass) {
        WindowWidthClass.COMPACT -> AiPanelPresentation.BOTTOM_SHEET
        WindowWidthClass.MEDIUM -> AiPanelPresentation.DOCKED_HALF
        WindowWidthClass.EXPANDED -> AiPanelPresentation.DOCKED_SIDE
    },
    density = when (widthClass) {
        WindowWidthClass.COMPACT -> UiDensity.COMFORTABLE
        WindowWidthClass.MEDIUM, WindowWidthClass.EXPANDED -> UiDensity.COMPACT
    },
)
