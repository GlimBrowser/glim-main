package cn.ac.oac.glimbrowser.model

/**
 * 首页默认快捷站点（原型 §02 首页 .qsites 8 宫格：维基/GitHub/知乎/添加/少数派/文档/地图/更多）。
 * PRD V1.0 §3.5.3：「快捷站点 1..8 可自定义」「默认不展示任何商业推荐内容」——
 * Phase 2 提供默认集，"可自定义"持久化留待 Phase 6（shared:data）。
 */
object DefaultQuickSites {
    val all: List<QuickSite> = listOf(
        QuickSite("wiki", "维基", "https://zh.wikipedia.org/wiki/", "W"),
        QuickSite("github", "GitHub", "https://github.com", "G"),
        QuickSite("zhihu", "知乎", "https://www.zhihu.com", "H"),
        QuickSite("sspai", "少数派", "https://sspai.com", "A"),
        QuickSite("docs", "文档", "https://docs.example.io", "D"),
        QuickSite("maps", "地图", "https://maps.example.com", "M"),
    )

    /** 「+ 添加」占位（第 7 格）与「更多」占位（第 8 格），Phase 6 接可自定义编辑。 */
    const val ADD_PLACEHOLDER_ID: String = "add"
    const val MORE_PLACEHOLDER_ID: String = "more"
}
