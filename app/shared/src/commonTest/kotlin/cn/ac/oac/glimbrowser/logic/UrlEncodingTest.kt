package cn.ac.oac.glimbrowser.logic

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 搜索词百分号编码单测（RFC 3986 未保留字符直通；空格 %20；中文 UTF-8 三字节）。
 */
class UrlEncodingTest {

    @Test
    fun unreservedCharsPassThrough() {
        assertEquals("abcXYZ019-._~", "abcXYZ019-._~".encodeUrlQuery())
    }

    @Test
    fun spaceEncodesAsPercent20() {
        assertEquals("a%20b%20c", "a b c".encodeUrlQuery())
    }

    @Test
    fun chineseEncodesUtf8PercentTriplets() {
        // "中" UTF-8 = E4 B8 AD
        assertEquals("%E4%B8%AD", "中".encodeUrlQuery())
        // "文" UTF-8 = E6 96 87
        assertEquals("%E6%96%87", "文".encodeUrlQuery())
    }

    @Test
    fun reservedQueryCharsEncoded() {
        assertEquals("a%26b", "a&b".encodeUrlQuery())   // & → %26（防注入 query 结构）
        assertEquals("a%3Db", "a=b".encodeUrlQuery())
        assertEquals("a%2Bb", "a+b".encodeUrlQuery())
        assertEquals("a%2Fb", "a/b".encodeUrlQuery())
        assertEquals("%3Cscript%3E", "<script>".encodeUrlQuery())  // 防注入
    }

    @Test
    fun mixedAsciiAndChinese() {
        assertEquals("kmp%20%E5%A4%9A%E5%B9%B3%E5%8F%B0", "kmp 多平台".encodeUrlQuery())
    }

    @Test
    fun emptyStringStaysEmpty() {
        assertEquals("", "".encodeUrlQuery())
    }
}
