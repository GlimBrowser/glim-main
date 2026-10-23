package cn.ac.oac.glimbrowser.model

/**
 * 浏览核心领域模型（Phase 2）。纯 Kotlin，无平台依赖（PRD V1.2 §2.3 关键设计原则 1：逻辑全部下沉 shared）。
 * 持久化在 Phase 6（shared:data / SQLDelight）接入；本阶段先以内存态驱动 UI。
 */

/**
 * 标签页（PRD V1.0 §3.1.3 标签页管理）。
 *
 * @property id 稳定标识（会话内唯一，非 URL）
 * @property title 页面标题（加载中为占位，加载完成由容器回填）
 * @property url 当前地址
 * @property faviconDomain 用于生成占位字母图标的域名（原型 .tabcard/.dtab 展示域名）
 * @property isLoading 是否加载中（顶部细线进度条，PRD §3.1.2）
 * @property isPrivate 是否无痕标签（PRD §3.1.3 隐私标签：不入历史、不留缓存）
 * @property progress 加载进度 0..1（容器回调）
 */
data class BrowserTab(
    val id: Long,
    val title: String,
    val url: String,
    val faviconDomain: String = url.toDisplayDomain(),
    val isLoading: Boolean = false,
    val isPrivate: Boolean = false,
    val progress: Float = 0f,
    /** 页面级错误（"网页访问报错"提示卡片，108 修复）：非 null = 展示错误卡。 */
    val lastError: String? = null,
    /** 原生内核历史可后退/可前进（驱动地址栏后退/前进按钮可用态，PRD §3.1.2 手势/导航）。 */
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** 标签分组名（null=未分组；分组由用户在标签页设置/移动）。 */
    val group: String? = null,
)

/**
 * 菜单/设置入口项（原型「菜单」+ PRD V1.0 §6.1 全局信息架构设置子项）。
 */
data class MenuItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: MenuItemIcon,
)

/**
 * 菜单项图标（Phase 2 先用枚举占位，Phase 3 接容器后替换为真实矢量图标资源）。
 */
enum class MenuItemIcon {
    BOOKMARK,
    HISTORY,
    DOWNLOAD,
    PLUGIN,
    SETTINGS,
    NEW_TAB,
    INCOGNITO,
    DESKTOP_MODE,
}

/**
 * 快捷站点（原型首页 .qsites 8 宫格；PRD V1.0 §3.5.3 首页信息架构「快捷站点 1..8 可自定义」）。
 *
 * @property id 稳定标识（用于 Phase 6 持久化"可自定义"）
 * @property label 展示名（原型：维基/GitHub/知乎/…）
 * @property url 目标地址
 * @property letter 占位字母图标（原型 .qsite .tile 单字母）
 */
data class QuickSite(
    val id: String,
    val label: String,
    val url: String,
    val letter: String,
)

/**
 * 首页可关卡片（PRD V1.0 §3.5.3 + 原型注释 01：「两张可关卡片（隐私/AI）……卡片可在设置一键全关」）。
 */
enum class HomeCard(val id: String, val title: String, val defaultEnabled: Boolean) {
    /** 隐私统计卡片（原型 .mini-card PRIVACY — 今日净化）。 */
    PRIVACY("privacy", "今日净化", true),

    /** AI 快速入口卡片（原型 .mini-card AI — 快速入口）。 */
    AI("ai", "AI 快速入口", true),
}

/**
 * 从 URL 提取展示域名（去掉 scheme、path、query；裸主机名原样返回）。
 * 用于地址栏/标签卡片的域名展示（原型 .dom / .tabcard .d / .dtab）。
 */
fun String.toDisplayDomain(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    val withoutScheme = trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
    val end = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    return if (end >= 0) withoutScheme.substring(0, end) else withoutScheme
}

/**
 * 地址栏输入判别（PRD V1.0 §3.1.1 智能地址栏）：
 * 判定输入应作为 URL 直接导航，还是交给搜索引擎。
 *
 * 规则（Phase 2 简化版，P1 搜索建议/拼音匹配后续）：
 *  - 已带 scheme（http/https/file/about）→ 直接导航
 *  - localhost / IP 字面量 / 含 '.' 且形如主机:端口 → 直接导航
 *  - 其余 → 交给搜索引擎（PRD §3.1.1 多源搜索）
 */
fun String.looksLikeUrl(): Boolean {
    val s = trim()
    if (s.isEmpty()) return false
    if (s.startsWith("http://", true) || s.startsWith("https://", true) ||
        s.startsWith("file://", true) || s.startsWith("about:", true)
    ) return true
    if (s.equals("localhost", true)) return true
    // 简单 IPv4 字面量判定（如 192.168.1.1、10.0.0.1:8080）
    val host = s.substringBefore('/')
    if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?$"""))) return true
    // 形如 host.tld 或 host.tld:port / host.tld/path（至少一个点 + 非空顶级段）
    val first = host.substringBefore(':')
    return first.contains('.') &&
        !first.startsWith('.') &&
        !first.endsWith('.') &&
        first.substringAfterLast('.').length >= 2 &&
        !first.substringAfterLast('.').any { it.isWhitespace() }
}
