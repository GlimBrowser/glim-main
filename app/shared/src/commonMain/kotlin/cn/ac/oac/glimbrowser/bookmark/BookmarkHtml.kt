package cn.ac.oac.glimbrowser.bookmark

import cn.ac.oac.glimbrowser.data.Bookmark

/**
 * 书签 HTML 互操作（Netscape Bookmark 格式，浏览器通用导入导出）。
 * 导出：按文件夹分组的 Netscape HTML；导入：解析 `<DT><A HREF>` 与 `<H3>` 文件夹。
 */
object BookmarkHtml {

    data class Entry(val url: String, val title: String, val folder: String)

    fun export(bookmarks: List<Bookmark>, title: String = "GlimBrowser 书签"): String {
        val grouped = bookmarks.groupBy { it.folder }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>$title</TITLE>\n<H1>$title</H1>\n<DL><p>\n")
        grouped.forEach { (folder, list) ->
            if (folder.isNotBlank()) {
                sb.append("    <DT><H3>${escape(folder)}</H3>\n    <DL><p>\n")
                list.forEach { appendEntry(sb, it, "        ") }
                sb.append("    </DL><p>\n")
            } else {
                list.forEach { appendEntry(sb, it, "    ") }
            }
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun appendEntry(sb: StringBuilder, bm: Bookmark, indent: String) {
        sb.append("$indent<DT><A HREF=\"${escapeAttr(bm.url)}\" ADD_DATE=\"${bm.created_at}\">${escape(bm.title.ifBlank { bm.url })}</A>\n")
    }

    /**
     * 解析书签 HTML（Netscape 结构）：按出现顺序处理 `<H3>` 文件夹名与 `<DL>` 嵌套，
     * 锚点归属当前打开的文件夹，`</DL>` 回到上一层。
     */
    fun parse(html: String): List<Entry> {
        val token = Regex(
            "(?s)<DL\\b[^>]*>|</DL\\s*>|<H3\\b[^>]*>(.*?)</H3\\s*>|<A\\b[^>]*HREF\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</A\\s*>",
            RegexOption.IGNORE_CASE,
        )
        val out = ArrayList<Entry>()
        val stack = ArrayList<String>()
        var currentFolder = ""
        var pendingFolder: String? = null

        token.findAll(html).forEach { m ->
            val raw = m.value
            when {
                raw.startsWith("<DL", ignoreCase = true) -> {
                    stack.add(currentFolder)
                    pendingFolder?.let { currentFolder = it; pendingFolder = null }
                }
                raw.startsWith("</DL", ignoreCase = true) -> {
                    currentFolder = if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else ""
                }
                raw.startsWith("<H3", ignoreCase = true) -> {
                    pendingFolder = unescape(m.groupValues[1].replace(Regex("<[^>]+>"), "").trim())
                }
                else -> {
                    val url = m.groupValues[2].trim()
                    if (url.isNotEmpty()) {
                        val title = unescape(m.groupValues[3].replace(Regex("<[^>]+>"), "").trim())
                        out.add(Entry(url, title, currentFolder))
                    }
                }
            }
        }
        return out.distinctBy { it.url }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escapeAttr(s: String) = escape(s).replace("\"", "&quot;")
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
}
