package cn.ac.oac.glimbrowser.adfilter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 匹配器单测（PRD V1.2 §2.4「匹配」+ §3.3.1 网页内部广告类型）。
 */
class AdRuleMatcherTest {

    private fun matcher(vararg rules: String): AdRuleMatcher =
        AdRuleMatcher(AdRuleParser.parse(rules.joinToString("\n")).rules)

    // ===== 网络匹配 =====

    @Test
    fun exactDomainBlock() {
        val m = matcher("||ads.example.com^")
        assertNotNull(m.matchNetwork("https://ads.example.com/banner.js"))
        assertNull(m.matchNetwork("https://good.example.org/ok.js"))  // 域不符
    }

    @Test
    fun separatorAnchorCaret() {
        // ^ 匹配分隔符：ads.example.com/path 里 ^ 在 .com 后匹配 '/'
        val m = matcher("||ads.example.com^")
        assertNotNull(m.matchNetwork("https://ads.example.com/track.gif"))
        // 'ads.exampleXcom' — X 是字母 → ^ 不匹配字母位置（域名连字符位置 ok，字母替换位置不命中）
        assertNull(m.matchNetwork("https://ads.exampleXcom/track.gif"))
    }

    @Test
    fun wildcardStarMatchesAnything() {
        val m = matcher("||example.com/*/ad.js")
        assertNotNull(m.matchNetwork("https://example.com/2026/ad.js"))
        assertNotNull(m.matchNetwork("https://example.com/ad.js".replace("/ad.js", "/x/y/ad.js")))
        assertNull(m.matchNetwork("https://example.com/other.js"))
    }

    @Test
    fun thirdPartyOptionRespected() {
        val m = matcher("||tracker.io^\$third-party")
        // 第三方 → 命中
        assertNotNull(m.matchNetwork("https://tracker.io/p.gif", requestHost = "news.site", isThirdParty = true))
        // 同域（非第三方）→ 跳过（thirdPartyOnly 规则仅三方）
        assertNull(m.matchNetwork("https://tracker.io/p.gif", requestHost = "tracker.io", isThirdParty = false))
        // isThirdParty 未知(null) → 不启用过滤，仍按 pattern 命中
        assertNotNull(m.matchNetwork("https://tracker.io/p.gif", isThirdParty = null))
    }

    @Test
    fun domainOptionWhitelist() {
        val m = matcher("||ads.net^\$domain=news.site|blog.site")
        assertNotNull(m.matchNetwork("https://ads.net/a.js", requestHost = "news.site"))
        assertNotNull(m.matchNetwork("https://ads.net/a.js", requestHost = "blog.site"))
        assertNull(m.matchNetwork("https://ads.net/a.js", requestHost = "other.site"))
        // requestHost 未知 → 跳过 domain 过滤（保守放行选项判定，pattern 仍命中）
        assertNotNull(m.matchNetwork("https://ads.net/a.js", requestHost = null))
    }

    @Test
    fun pathSubstringPattern() {
        val m = matcher("/adframe/")
        assertNotNull(m.matchNetwork("https://any.site/adframe/index.html"))
        assertNull(m.matchNetwork("https://any.site/content/index.html"))
    }

    // ===== 化妆匹配 =====

    @Test
    fun cosmeticHideAndException() {
        val m = matcher("##.ad-banner", "#@#.ad-banner", "##.sponsored")
        // 例外覆盖同 selector
        assertNull(m.matchCosmetic(".ad-banner"))
        assertTrue(m.isCosmeticException(".ad-banner"))
        // 无例外的正常隐藏
        val hit = m.matchCosmetic(".sponsored")
        assertNotNull(hit)
        assertEquals(AdRule.Kind.COSMETIC, hit.kind)
        // 未知 selector
        assertNull(m.matchCosmetic(".unknown"))
    }

    @Test
    fun countersExposeRuleCounts() {
        val m = matcher("||a.com^", "||b.com^", "##.x")
        assertEquals(2, m.networkRuleCount)
        assertEquals(1, m.cosmeticRuleCount)
    }

    // ===== 规则 ID 与来源 =====
    @Test
    fun matchedRuleCarriesSourceForReport() {
        // PRD §3.3.3 误杀上报：域名 + 规则 ID（不含页面内容）
        val m = matcher("||ads.example.com^")
        val hit = m.matchNetwork("https://ads.example.com/x.js")!!
        assertEquals("||ads.example.com^", hit.source)
        assertTrue(hit.id >= 0)
    }
}
