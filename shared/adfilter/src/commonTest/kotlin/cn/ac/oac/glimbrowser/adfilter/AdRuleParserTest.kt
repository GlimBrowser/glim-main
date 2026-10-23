package cn.ac.oac.glimbrowser.adfilter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EasyList 解析单测（PRD V1.2 §2.4「规则解析（EasyList 兼容）」）。
 */
class AdRuleParserTest {

    @Test
    fun commentsAndHeadersSkipped() {
        val text = """
            ! comment line
            [Adblock Plus 2.0]
            
            ||example.com^
        """.trimIndent()
        val r = AdRuleParser.parse(text)
        assertEquals(1, r.rules.size)
        assertTrue(r.skipped >= 3)  // 注释+头+空行
    }

    @Test
    fun networkRuleBasicParse() {
        val r = AdRuleParser.parse("||ads.example.com^")
        assertEquals(1, r.rules.size)
        val rule = r.rules.first()
        assertEquals(AdRule.Kind.NETWORK, rule.kind)
        assertEquals("||ads.example.com^", rule.pattern)
        assertTrue(rule.options.isEmpty())
    }

    @Test
    fun networkRuleWithOptions() {
        val r = AdRuleParser.parse("||tracker.io^\$third-party,script")
        val rule = r.rules.first()
        assertEquals("||tracker.io^", rule.pattern)
        assertTrue(rule.thirdPartyOnly)
        assertEquals(listOf("third-party", "script"), rule.options)
    }

    @Test
    fun dollarInPatternNotSplitWhenOptionsLike() {
        // options 段仅含 [a-z0-9=-,]，pattern 中 $ 出现在非选项形态时不分割
        val r = AdRuleParser.parse("||ex.com/pa\$th")  // 无合法 options → 整行是 pattern
        assertEquals(1, r.rules.size)
        assertTrue(r.rules.first().pattern!!.contains('$'))
    }

    @Test
    fun cosmeticHideParse() {
        val r = AdRuleParser.parse("##.ad-banner")
        val rule = r.rules.first()
        assertEquals(AdRule.Kind.COSMETIC, rule.kind)
        assertEquals(".ad-banner", rule.selector)
    }

    @Test
    fun cosmeticExceptionParse() {
        val r = AdRuleParser.parse("#@#.ad-banner")
        val rule = r.rules.first()
        assertEquals(AdRule.Kind.COSMETIC_EXCEPTION, rule.kind)
        assertTrue(rule.isException)
    }

    @Test
    fun unsupportedSyntaxCountedNotParsed() {
        // scriptlet #?// 与正则规则 → unsupportedSyntax 统计（如实，不虚报能力）
        val r = AdRuleParser.parse("#?//script\n/regex.*pattern/\n||ok.com^")
        assertEquals(1, r.rules.size)
        assertEquals(2, r.unsupportedSyntax)
    }

    @Test
    fun crlfLineEndingsHandled() {
        val r = AdRuleParser.parse("||a.com^\r\n##.x\r\n")
        assertEquals(2, r.rules.size)
    }

    @Test
    fun statsGrouping() {
        val r = AdRuleParser.parse("||a.com^\n##.b\n#@#.b")
        assertEquals(1, r.networkRules.size)
        assertEquals(1, r.cosmeticRules.size)
        assertEquals(1, r.cosmeticExceptions.size)
    }

    @Test
    fun idsAreStableAndUnique() {
        val r = AdRuleParser.parse("||a.com^\n||b.com^\n##.c")
        assertEquals(3, r.rules.map { it.id }.toSet().size)
        assertEquals(r.rules.first().id, AdRuleParser.parse("||a.com^\n||b.com^\n##.c").rules.first().id)  // 同输入同 ID（确定性）
    }
}
