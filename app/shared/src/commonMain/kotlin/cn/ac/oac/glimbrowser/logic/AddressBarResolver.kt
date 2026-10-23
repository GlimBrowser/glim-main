package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.looksLikeUrl

/**
 * 地址栏输入解析（PRD V1.0 §3.1.1 智能地址栏 / 多源搜索，Phase 2 简化版）。
 *
 * 纯函数：输入原始串 → 解析结果（导航 or 搜索），UI 只负责展示与触发。
 * P1 能力（搜索联想、`!bang` 快捷搜索、拼音首字母匹配）按 PRD 留待后续阶段。
 */
enum class AddressBarIntent {
    /** 视为 URL/主机，直接导航。 */
    NAVIGATE,

    /** 视为搜索词，交给默认搜索引擎。 */
    SEARCH,
}

data class AddressBarResolution(
    val intent: AddressBarIntent,
    /** NAVIGATE：规范化后的目标 URL；SEARCH：编码前的搜索词。 */
    val value: String,
    /** NAVIGATE 且输入未带 scheme 时补 `https://`（HTTPS 优先，PRD §3.3.2 HTTPS）。 */
    val normalizedUrl: String? = null,
)

/**
 * 搜索引擎（PRD V1.0 §3.1.1 多源搜索：首次启动可选默认搜索引擎；Phase 2 内置常见四源 + 自定义占位）。
 */
enum class SearchEngine(
    val id: String,
    val label: String,
    /** 搜索 URL 模板，`%s` 为未编码查询词。 */
    val template: String,
) {
    BAIDU("baidu", "百度", "https://www.baidu.com/s?wd=%s"),
    BING("bing", "必应", "https://www.bing.com/search?q=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    ;

    fun buildSearchUrl(query: String, encode: (String) -> String = { it }): String =
        template.replace("%s", encode(query.trim()))

    companion object {
        /** 默认引擎（PRD §3.1.1 用户可自由更换，见 §8 搜索合作红线）。 */
        val DEFAULT: SearchEngine = BAIDU

        fun byId(id: String): SearchEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 解析地址栏输入。
 *
 * @param raw 原始输入（未 trim 也可）
 * @param queryEncoder 搜索词 URL 编码器（common 层不依赖平台 URL 编码实现，由调用方注入；
 *                     JVM/Android 可用 URLEncoder，JS 用 encodeURIComponent，默认为恒等仅供单测）
 */
fun resolveAddressBarInput(
    raw: String,
    engine: SearchEngine = SearchEngine.DEFAULT,
    queryEncoder: (String) -> String = { it },
): AddressBarResolution {
    val s = raw.trim()
    if (s.isEmpty()) return AddressBarResolution(AddressBarIntent.SEARCH, s)
    return if (s.looksLikeUrl()) {
        val url = if (s.startsWith("http://", true) || s.startsWith("https://", true) ||
            s.startsWith("file://", true) || s.startsWith("about:", true)
        ) s else "https://$s"
        AddressBarResolution(AddressBarIntent.NAVIGATE, s, normalizedUrl = url)
    } else {
        AddressBarResolution(AddressBarIntent.SEARCH, engine.buildSearchUrl(s, queryEncoder))
    }
}
