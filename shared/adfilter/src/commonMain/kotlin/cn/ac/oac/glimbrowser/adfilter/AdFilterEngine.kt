package cn.ac.oac.glimbrowser.adfilter

/**
 * 去广告总入口（PRD V1.2 §2.4 adfilter 职责聚合：解析 + 匹配 + 统计 + 开关；纯 Kotlin KMP 共享）。
 *
 * 平台执行层用法（PRD §3.3.5 执行平台化，接入点已在 Phase 3 容器实现类预留）：
 *  - Android `shouldInterceptRequest` → [shouldIntercept]（true=阻断，命中 rule 用于统计/注入 CSS 走 [cosmeticHideSelectors]）
 *  - iOS/macOS `WKContentRuleList` 编译 → [compileContentRuleListJson]（同源编译三产物之一，PRD §3.3.5 CI 转换）
 *  - Windows `WebResourceRequested` → 同 Android 路径
 *
 * 启动加载（PRD §3.3.2 L1"启动时本地加载"）：调用 [loadRules]（线程约定：调用方决定，common 无协程依赖）。
 */
class AdFilterEngine(
    /** 平台标注（报告"拦截/净化"如实区分，PRD §3.6.2）。 */
    val platformLabel: String = "unknown",
) {
    private var rules: List<AdRule> = emptyList()
    private var matcher: AdRuleMatcher = AdRuleMatcher(emptyList())

    /** 当前开关状态（PRD §5.1 状态机；只读对外，经 [updateSettings]/[pauseSite] 变更）。 */
    var settings: FilterSettings = FilterSettings()
        private set

    /** 状态机更新入口（纯函数迁移，便于测试与后续持久化）。 */
    fun updateSettings(transform: (FilterSettings) -> FilterSettings) {
        settings = transform(settings)
    }

    /** 误杀闭环快捷入口（PRD §3.3.3 流程 B）。 */
    fun pauseSite(host: String) { settings = settings.pauseSite(host) }
    fun resumeSite(host: String) { settings = settings.resumeSite(host) }

    /** 净化统计（护盾计数/报告数据源）。 */
    val stats: PurifyStats = PurifyStats(platformLabel)

    /** 规则是否已加载。 */
    val isLoaded: Boolean get() = rules.isNotEmpty()

    /** 解析结果诊断（unsupportedSyntax 如实上报，不虚报拦截能力 —— PRD §3.6.2）。 */
    var lastParseResult: AdRuleParser.Result = AdRuleParser.Result(emptyList(), 0, 0)
        private set

    /**
     * 加载规则清单（可多次调用叠加，如 GlimBase + EasyList 订阅）。
     * @return 本次新增规则数
     */
    fun loadRules(text: String, listName: String = "GlimBase"): Int {
        val result = AdRuleParser.parse(text, listName = listName)
        lastParseResult = result
        rules = rules + result.rules
        matcher = AdRuleMatcher(rules)   // 重建匹配索引（启动一次性；热更走 Phase 4 热更新）
        return result.rules.size
    }

    /**
     * 网络请求拦截判定（平台请求级拦截入口）。
     *
     * @param url 请求 URL
     * @param pageHost 页面域名
     * @param isThirdParty 是否第三方请求
     * @param isTrackerRequest 是否追踪器类（站点降级 TRACKERS_ONLY 时用，PRD §5.1）
     * @return 命中规则（null=放行）；命中即由调用方/内部记账
     */
    fun shouldIntercept(
        url: String,
        pageHost: String,
        isThirdParty: Boolean = false,
        isTrackerRequest: Boolean = false,
    ): AdRule? {
        val hit = matchOnly(url, pageHost, isThirdParty, isTrackerRequest) ?: return null
        stats.record(category = guessCategory(hit, url), rule = hit, host = url.toHostOrNull())
        return hit
    }

    /**
     * **只匹配、不记账**（线程安全：只读规则与设置，不改任何可变状态）。
     * 供 Android `shouldInterceptRequest`（后台线程）使用；记账必须在主线程另行调用 [recordBlocked]。
     */
    fun matchOnly(
        url: String,
        pageHost: String,
        isThirdParty: Boolean = false,
        isTrackerRequest: Boolean = false,
    ): AdRule? {
        if (!settings.shouldBlockNetwork(pageHost, isTrackerRequest)) return null
        return matcher.matchNetwork(url, requestHost = pageHost, isThirdParty = isThirdParty)
    }

    /** 主线程记账（命中后统计 + 护盾计数）。 */
    fun recordBlocked(url: String, pageHost: String) {
        val hit = matchOnly(url, pageHost) ?: return
        stats.record(category = guessCategory(hit, url), rule = hit, host = url.toHostOrNull())
    }

    /**
     * 化妆隐藏判定（元素选择器）；例外优先（PRD §3.3.2 L1 化妆过滤）。
     */
    fun cosmeticHideSelectors(selector: String, pageHost: String): AdRule? {
        if (!settings.shouldCosmeticHide(pageHost)) return null
        val hit = matcher.matchCosmetic(selector) ?: return null
        stats.record(category = "化妆隐藏", rule = hit, host = pageHost.ifBlank { null })
        return hit
    }

    /**
     * 编译 iOS/macOS `WKContentRuleList` JSON（PRD §3.3.5：共享引擎同源编译三产物之一；
     * 此处生成 css-display-none + url-block 子集，供 `WKContentRuleListStore.compileContentRuleList` 消费）。
     *
     * 输出形如：
     * ```
     * [{"trigger":{"url-filter":".*"},"action":"block"},
     *  {"trigger":{"url-filter":"..."},"action":"css-display-none","selector":"..."}]
     * ```
     * 注意：本方法只**生成同源产物**；规则一致性抽样（≥95%）由 CI 侧脚本对三产物做（PRD §3.3.5）。
     */
    fun compileContentRuleListJson(): String {
        val sb = StringBuilder("[")
        var first = true
        rules.forEach { r ->
            when (r.kind) {
                AdRule.Kind.NETWORK -> {
                    val filter = (r.pattern ?: "").toUrlFilterRegex()
                    if (filter.isEmpty()) return@forEach
                    if (!first) sb.append(',')
                    first = false
                    sb.append("{\"trigger\":{\"url-filter\":\"").append(escapeJson(filter))
                    sb.append("\"},\"action\":\"block\"}")
                }
                AdRule.Kind.COSMETIC -> {
                    val sel = r.selector ?: return@forEach
                    if (!first) sb.append(',')
                    first = false
                    sb.append("{\"trigger\":{\"url-filter\":\".*\",\"if-domain\":[\"*\"]},")
                    sb.append("\"action\":\"css-display-none\",\"selector\":\"").append(escapeJson(sel)).append("\"}")
                }
                AdRule.Kind.COSMETIC_EXCEPTION -> Unit // ContentRuleList 用 -css-display-none 需 selector 关联，子集略（Phase 4 完善）
            }
        }
        return sb.append(']').toString()
    }

    /** 加载新清单并重建（热更入口，PRD §3.3.3 规则热更新 P0 —— 本阶段仅提供 API，下载留 Phase 4）。 */
    fun swapRules(text: String, listName: String = "GlimBase") {
        rules = emptyList()
        stats.reset()
        loadRules(text, listName)
    }

    /** 页面切换（护盾计数清零）。 */
    fun onPageChanged() = stats.newPage()

    // ===== helpers =====

    /** 粗分类（PRD §3.3.1 类别；真实分类 Phase 4 按规则元数据增强）。 */
    private fun guessCategory(rule: AdRule, url: String): String = when {
        rule.thirdPartyOnly -> "追踪器"
        url.contains("banner", true) || url.contains("ad_", true) -> "展示广告"
        url.contains("popup", true) -> "弹窗浮层"
        else -> "展示广告"
    }

    private fun String.toHostOrNull(): String? {
        val s = removePrefix("https://").removePrefix("http://")
        val end = s.indexOfFirst { it == '/' || it == '?' }
        return s.substring(0, if (end >= 0) end else s.length).ifBlank { null }
    }

    /** EasyList pattern → WKContentRuleList url-filter（近似正则：* → .*，^ → 分隔类，其余转义）。 */
    private fun String.toUrlFilterRegex(): String {
        if (isEmpty()) return ""
        val sb = StringBuilder()
        for (c in this) {
            when (c) {
                '*' -> sb.append(".*")
                '^' -> sb.append("[^a-zA-Z0-9_\\-.%/]")
                '.', '$', '+', '?', '(', ')', '{', '}', '[', ']', '|', '\\' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
