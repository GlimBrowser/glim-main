package cn.ac.oac.glimbrowser.bookmark

import cn.ac.oac.glimbrowser.data.Bookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkHtmlTest {

    private fun bm(id: Long, url: String, title: String, folder: String) =
        Bookmark(id = id, url = url, title = title, folder = folder, created_at = 1_700_000_000_000, position = 0)

    @Test
    fun exportContainsNetscapeHeaderAndEntries() {
        val html = BookmarkHtml.export(
            listOf(
                bm(1, "https://a.example/1", "A 站", "工作"),
                bm(2, "https://b.example/2", "B 站", ""),
            ),
        )
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<H3>工作</H3>"))
        assertTrue(html.contains("<A HREF=\"https://a.example/1\" ADD_DATE=\"1700000000000\">A 站</A>"))
        assertTrue(html.contains("https://b.example/2"))
    }

    @Test
    fun parseRoundTripKeepsUrlTitleFolder() {
        val html = BookmarkHtml.export(
            listOf(
                bm(1, "https://a.example/x?a=1&b=2", "带 & 与 <标签>", "阅读"),
                bm(2, "https://c.example/", "C", ""),
            ),
        )
        val parsed = BookmarkHtml.parse(html)
        assertEquals(2, parsed.size)
        val a = parsed.first { it.url.contains("a.example") }
        assertEquals("带 & 与 <标签>", a.title)
        assertEquals("阅读", a.folder)
    }

    @Test
    fun parseChromeStyleHtml() {
        val chrome = """
            <DL><p>
                <DT><H3>技术</H3>
                <DL><p>
                    <DT><A HREF="https://kotlinlang.org" ADD_DATE="1700">Kotlin</A>
                    <DT><A HREF="https://ktor.io">Ktor</A>
                </DL><p>
                <DT><A HREF="https://example.com">Example</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarkHtml.parse(chrome)
        assertEquals(3, parsed.size)
        assertEquals("技术", parsed.first { it.url.contains("kotlinlang") }.folder)
        assertEquals("", parsed.first { it.url.contains("example.com") }.folder)
    }

    @Test
    fun parseIgnoresDuplicatesAndEmpty() {
        val html = """
            <A HREF="https://x.example">X</A>
            <A HREF="https://x.example">X again</A>
            <A HREF="">empty</A>
        """.trimIndent()
        val parsed = BookmarkHtml.parse(html)
        assertEquals(1, parsed.size)
    }
}
