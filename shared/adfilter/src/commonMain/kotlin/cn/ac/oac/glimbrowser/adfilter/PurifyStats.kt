package cn.ac.oac.glimbrowser.adfilter

/**
 * 净化统计（PRD V1.0 §3.3.3 净化报告 + V1.3 §3.6.2「报告按端标注 拦截/净化」）。
 *
 * 纯 Kotlin 计数器（内存态；Phase 6 SQLDelight 持久化 + 按天分组）。
 * 埋点红线（PRD §7）：仅计数与规则 ID/域名，**不含页面正文、URL 全路径可选脱敏**——
 * 域名统计按 PRD §7.2 需哈希化后上报（本类仅内存聚合，上报层处理）。
 *
 * @param platformLabel 平台标注（"Android 拦截" / "iOS 净化" / …，PRD §3.6.2 如实标注）
 */
class PurifyStats(
    val platformLabel: String,
) {
    /** 分类计数（PRD §3.3.1 网页内部广告类型）。 */
    private val byCategory = HashMap<String, Int>()

    /** 按规则清单计数（报告分组）。 */
    private val byList = HashMap<String, Int>()

    /** 域名 → 拦截数（TOP SITES，原型净化报告；上报前调用方需哈希，PRD §7.2）。 */
    private val byHost = HashMap<String, Int>()

    /** 单页拦截数（地址栏护盾计数，原型 .badge-shield）。 */
    var currentPageCount: Int = 0
        private set

    /** 今日累计（跨页）。 */
    var todayCount: Int = 0
        private set

    /**
     * 记录一次拦截/净化命中。
     * @param category 广告类别（PRD §3.3.1：展示广告/弹窗浮层/信息流/追踪器/…）
     * @param rule 命中规则（供 byList 统计）
     * @param host 请求 host（供 TOP SITES）
     */
    fun record(category: String, rule: AdRule?, host: String?) {
        byCategory[category] = (byCategory[category] ?: 0) + 1
        if (rule != null) byList[rule.list] = (byList[rule.list] ?: 0) + 1
        if (host != null) byHost[host] = (byHost[host] ?: 0) + 1
        currentPageCount++
        todayCount++
    }

    /** 切换页面（护盾计数清零）。 */
    fun newPage() { currentPageCount = 0 }

    fun categoryCounts(): Map<String, Int> = byCategory.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    fun listCounts(): Map<String, Int> = byList.toMap()
    fun topHosts(limit: Int = 10): Map<String, Int> =
        byHost.entries.sortedByDescending { it.value }.take(limit).associate { it.key to it.value }

    /** 分类占比（0..1，原型 .cat .tr 进度条）。 */
    fun categoryShare(category: String): Float {
        val total = byCategory.values.sum()
        return if (total == 0) 0f else (byCategory[category] ?: 0) / total.toFloat()
    }

    fun reset() {
        byCategory.clear(); byList.clear(); byHost.clear()
        currentPageCount = 0; todayCount = 0
    }
}
