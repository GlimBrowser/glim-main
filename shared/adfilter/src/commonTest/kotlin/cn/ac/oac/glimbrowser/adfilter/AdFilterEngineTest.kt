package cn.ac.oac.glimbrowser.adfilter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 总入口单测（PRD §3.3.2 默认红线 + §3.3.3 误杀闭环 + §3.3.5 WKContentRuleList 同源编译 + §7 埋点红线）。
 */
class AdFilterEngineTest {

    private fun engineWithRules(): AdFilterEngine = AdFilterEngine(platformLabel = "iOS 净化").apply {
        loadRules(
            """
            ! GlimBase 示例清单
            ||ads.example.com^
            ||tracker.io^${'$'}third-party
            ##.ad-banner
            #@#.ad-banner
            ##.sponsored
            """.trimIndent(),
        )
    }

    @Test
    fun loadRulesReportsCountAndLoaded() {
        val e = engineWithRules()
        assertTrue(e.isLoaded)
        // 5 规则（注释不计）：2 网络 + 3 化妆（含 1 例外）
        assertEquals(5, e.lastParseResult.rules.size)
        assertEquals(0, e.lastParseResult.unsupportedSyntax)
    }

    @Test
    fun defaultRedLine_blocksNetworkAndCosmetic() {
        val e = engineWithRules()
        assertNotNull(e.shouldIntercept("https://ads.example.com/banner.js", pageHost = "news.site"))
        assertNotNull(e.cosmeticHideSelectors(".sponsored", pageHost = "news.site"))
        // 例外规则：.ad-banner 被 #@# 撤销
        assertNull(e.cosmeticHideSelectors(".ad-banner", pageHost = "news.site"))
    }

    @Test
    fun pauseSiteStopsBlocking_misjudgeLoop() {
        // PRD §3.3.3 流程 B：误杀 → 本页暂停拦截 → 页面恢复
        val e = engineWithRules()
        val host = "news.site"
        e.pauseSite(host)
        assertNull(e.shouldIntercept("https://ads.example.com/b.js", pageHost = host))
        assertNull(e.cosmeticHideSelectors(".sponsored", pageHost = host))
        // 恢复
        e.resumeSite(host)
        assertNotNull(e.shouldIntercept("https://ads.example.com/b.js", pageHost = host))
    }

    @Test
    fun statsRecordedOnIntercept_currentPageAndToday() {
        val e = engineWithRules()
        e.shouldIntercept("https://ads.example.com/b.js", pageHost = "news.site")
        e.shouldIntercept("https://ads.example.com/c.js", pageHost = "news.site")
        assertEquals(2, e.stats.currentPageCount)
        assertEquals(2, e.stats.todayCount)
        e.onPageChanged()
        assertEquals(0, e.stats.currentPageCount)  // 切页护盾清零（PRD §3.1.2 地址栏护盾计数）
        assertEquals(2, e.stats.todayCount)        // 今日累计不清
    }

    @Test
    fun matchOnlyDoesNotRecord() {
        // 线程安全契约：matchOnly 只读匹配、不改统计（Android 后台线程调用它）
        val e = engineWithRules()
        val hit = e.matchOnly("https://ads.example.com/b.js", pageHost = "news.site")
        kotlin.test.assertNotNull(hit)
        kotlin.test.assertEquals(0, e.stats.currentPageCount)
        kotlin.test.assertEquals(0, e.stats.todayCount)
        // 主线程记账才累加
        e.recordBlocked("https://ads.example.com/b.js", pageHost = "news.site")
        kotlin.test.assertEquals(1, e.stats.currentPageCount)
    }

    @Test
    fun platformLabelExposedForHonestReporting() {
        // PRD §3.6.2：报告按端标注"拦截/净化"，不虚报
        val e = AdFilterEngine(platformLabel = "iOS 净化")
        assertEquals("iOS 净化", e.platformLabel)
        assertEquals("iOS 净化", e.stats.platformLabel)
    }

    @Test
    fun contentRuleListJsonCompilesFromSameRules() {
        // PRD §3.3.5：同源编译三产物（此为 WKContentRuleList JSON 产物）
        val e = engineWithRules()
        val json = e.compileContentRuleListJson()
        assertTrue(json.startsWith("[") && json.endsWith("]"))
        assertTrue(json.contains("\"action\":\"block\""))
        assertTrue(json.contains("\"action\":\"css-display-none\""))
        assertTrue(json.contains("ads"))                 // 网络规则 → block（注意 '.' 会被转义进正则，故不断言完整域名）
        assertTrue(json.contains(".sponsored"))          // 化妆规则 → css-display-none
        // JSON 转义安全（注入防护：selector 内引号转义）
        val e2 = AdFilterEngine().apply { loadRules("##div[title=\"x\"]") }
        val j2 = e2.compileContentRuleListJson()
        assertTrue(j2.contains("\\\"x\\\""))
    }

    @Test
    fun swapRulesHotUpdateResetsStats() {
        // PRD §3.3.3 规则热更新 P0（本阶段提供 API；下载留 Phase 4）
        val e = engineWithRules()
        e.shouldIntercept("https://ads.example.com/b.js", pageHost = "n.s")
        assertTrue(e.stats.todayCount > 0)
        e.swapRules("||new.rules.example^")
        assertEquals(0, e.stats.todayCount)
        assertEquals(1, e.lastParseResult.rules.size)
        assertNotNull(e.shouldIntercept("https://new.rules.example/x", pageHost = "n.s"))
        assertNull(e.shouldIntercept("https://ads.example.com/b.js", pageHost = "n.s"))  // 旧规则已换
    }

    @Test
    fun globalOffRedLineReversed() {
        val e = engineWithRules()
        e.updateSettings { it.withGlobalEnabled(false) }
        assertNull(e.shouldIntercept("https://ads.example.com/b.js", pageHost = "n.s"))
        e.updateSettings { it.withGlobalEnabled(true) }
        assertNotNull(e.shouldIntercept("https://ads.example.com/b.js", pageHost = "n.s"))
    }

    @Test
    fun noPageContentInStats_埋点红线() {
        // PRD §7 埋点红线：统计仅含计数/域名，不含 URL 全路径或页面正文——
        // stats.topHosts 只应暴露 host（此处验证 record 不接收 body/path 参数即结构保证）
        val e = engineWithRules()
        e.shouldIntercept("https://ads.example.com/very/secret/path?token=abc", pageHost = "news.site")
        val hosts = e.stats.topHosts().keys
        assertTrue(hosts.all { !it.contains("secret") && !it.contains("token") })
        assertTrue(hosts.contains("ads.example.com"))
    }
}
