package cn.ac.oac.glimbrowser.plugin

import kotlinx.serialization.Serializable

/**
 * 用户脚本元数据（PRD V1.0 §3.4 用户脚本 Userscript 形态；对齐 Greasemonkey 风格头部子集）。
 *
 * 政策约束（PRD §2.2.2 硬约束 / §3.4）：
 *  - iOS/macOS 仅"用户脚本 + 内置模块"，不承诺完整扩展生态
 *  - 权限逐项授权（PRD §3.4 权限清单），安装时展示、可随时关闭
 *  - 沙箱执行：脚本只能调 [Permission] 白名单内 API（Phase 6 平台桥接层执行；元数据在此校验）
 *
 * @property fingerprint SHA-256（远程吊销黑名单用，PRD §3.4/§10 风险表；不含脚本内容上传）
 */
@Serializable
data class UserscriptMeta(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    /** 脚本来源（CDN 订阅 URL 或本地导入）。 */
    val sourceUrl: String = "",
    /** 脚本指纹（远程吊销比对；本地计算）。 */
    val fingerprint: String = "",
    /** 运行域作用域（@match 简化：域名通配，`*` 全站）。 */
    val matches: List<String> = emptyList(),
    /** 请求的权限（逐项授权，PRD §3.4）。 */
    val permissions: Set<Permission> = emptySet(),
    /** 启用开关（用户可关每个脚本，PRD §3.4）。 */
    val enabled: Boolean = false,
    /** 内置模块标记（vs 用户导入脚本）。 */
    val builtin: Boolean = false,
    /** 订阅清单来源（https://…/list.json，CDN 热更，PRD §3.3.3 同源）。 */
    val subscriptionId: String? = null,
    /** 吊销标记（远程黑名单命中，PRD §3.4 吊销机制）。 */
    val revoked: Boolean = false,
    /** 安装时间 epoch ms（本地）。 */
    val installedAt: Long = 0,
)

/**
 * 脚本权限（PRD §3.4 权限清单；授予后可关闭 = Permission 可撤回）。
 * 红线：每个权限必须有用户可见用途说明（[purpose]），安装页逐项展示。
 */
@Serializable
enum class Permission(val purpose: String, val sensitive: Boolean = false) {
    /** 读当前页文本（摘要/划词依赖）。 */
    READ_PAGE_TEXT("读取当前页面文字内容", sensitive = true),

    /** 注入/修改 DOM 样式（广告隐藏类）。 */
    MODIFY_STYLE("修改页面样式与元素显示"),

    /** 发起网络请求（fetch，限定域名白名单）。 */
    NETWORK("向已授权域名发起网络请求", sensitive = true),

    /** 读写本地剪贴板。 */
    CLIPBOARD("读取或写入剪贴板", sensitive = true),

    /** 读写站点级存储（localStorage 域内）。 */
    STORAGE("在当前网站存储少量数据"),

    /** 触发通知（系统级，需二次确认）。 */
    NOTIFY("发送本地通知", sensitive = true),

    /** 调用浏览器内置模块（摘要/翻译等 AI 能力，走 BYOK 链路）。 */
    AI_TOOLS("使用浏览器 AI 能力（BYOK，费用计入你的 API）", sensitive = true),
}

/**
 * 脚本作用域匹配（@match 简化语义）：
 *  - `*` 匹配所有
 *  - `*.example.com` 匹配根域与子域
 *  - `example.com` 精确或子域前缀
 */
object ScopeMatcher {
    fun matches(pattern: String, host: String): Boolean {
        if (pattern.isBlank() || host.isBlank()) return false
        if (pattern == "*") return true
        val p = pattern.removePrefix("*.").removePrefix("www.")
        val h = host.removePrefix("www.")
        return h == p || h.endsWith(".$p")
    }

    fun matchesAny(patterns: List<String>, host: String): Boolean = patterns.any { matches(it, host) }
}
