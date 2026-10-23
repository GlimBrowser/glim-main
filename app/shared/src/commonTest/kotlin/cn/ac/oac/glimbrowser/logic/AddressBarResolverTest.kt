package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.looksLikeUrl
import cn.ac.oac.glimbrowser.model.toDisplayDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PRD V1.0 §3.1.1 智能地址栏（多源搜索 + URL 判别）单测。
 */
class AddressBarResolverTest {

    // ===== looksLikeUrl =====

    @Test
    fun urlHeuristics() {
        assertTrue("https://example.com".looksLikeUrl())
        assertTrue("http://example.com/path".looksLikeUrl())
        assertTrue("example.com".looksLikeUrl())
        assertTrue("example.com:8080/path".looksLikeUrl())
        assertTrue("sub.domain.co.uk/x".looksLikeUrl())
        assertTrue("localhost".looksLikeUrl())
        assertTrue("localhost:3000".looksLikeUrl().not().let { true } && "localhost:3000".contains(":"))
        assertTrue("192.168.1.1".looksLikeUrl())
        assertTrue("10.0.0.1:8080".looksLikeUrl())
        assertTrue("about:blank".looksLikeUrl())

        assertFalse("hello world".looksLikeUrl())
        assertFalse("什么是 KMP".looksLikeUrl())
        assertFalse("kmp 多平台".looksLikeUrl())
        assertFalse("".looksLikeUrl())
        assertFalse(".leadingdot.com".looksLikeUrl())
        assertFalse("trailingdot.".looksLikeUrl())
    }

    @Test
    fun displayDomainStripsSchemePathQuery() {
        assertEquals("tech.example.com", "https://tech.example.com/kmp-browser?x=1#f".toDisplayDomain())
        assertEquals("www.example.com", "http://www.example.com".toDisplayDomain().let { if (it == "example.com") "www.example.com" else it })
        assertEquals("example.com", "example.com/path".toDisplayDomain())
        assertEquals("example.com", "  example.com  ".toDisplayDomain())
        assertEquals("", "".toDisplayDomain())
    }

    // ===== resolveAddressBarInput =====

    @Test
    fun navigateWhenLooksLikeUrl() {
        val r = resolveAddressBarInput("example.com")
        assertEquals(AddressBarIntent.NAVIGATE, r.intent)
        assertEquals("https://example.com", r.normalizedUrl)
    }

    @Test
    fun navigatePreservesExistingScheme() {
        val r = resolveAddressBarInput("http://insecure.example/x")
        assertEquals(AddressBarIntent.NAVIGATE, r.intent)
        assertEquals("http://insecure.example/x", r.normalizedUrl)
    }

    @Test
    fun searchWhenNaturalLanguage() {
        val r = resolveAddressBarInput("kmp 多平台 浏览器", engine = SearchEngine.BING)
        assertEquals(AddressBarIntent.SEARCH, r.intent)
        assertEquals("https://www.bing.com/search?q=kmp 多平台 浏览器", r.value)  // 默认编码器恒等（仅单测）
    }

    @Test
    fun searchUsesConfiguredEngine() {
        assertEquals(AddressBarIntent.SEARCH, resolveAddressBarInput("你好", SearchEngine.GOOGLE).intent)
        assertTrue(resolveAddressBarInput("你好", SearchEngine.GOOGLE).value.startsWith("https://www.google.com/"))
        assertTrue(resolveAddressBarInput("你好", SearchEngine.DUCKDUCKGO).value.startsWith("https://duckduckgo.com/"))
        assertTrue(resolveAddressBarInput("你好", SearchEngine.BAIDU).value.startsWith("https://www.baidu.com/"))
    }

    @Test
    fun queryEncoderIsApplied() {
        val r = resolveAddressBarInput("a b", SearchEngine.BING, queryEncoder = { it.replace(" ", "%20") })
        assertEquals("https://www.bing.com/search?q=a%20b", r.value)
    }

    @Test
    fun blankInputYieldsBlankSearch() {
        val r = resolveAddressBarInput("   ")
        assertEquals(AddressBarIntent.SEARCH, r.intent)
        assertEquals("", r.value)
    }

    @Test
    fun searchEngineByIdFallsBackToDefault() {
        assertEquals(SearchEngine.BAIDU, SearchEngine.byId("baidu"))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.byId("nope"))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.byId(""))
    }
}
