package cn.ac.oac.glimbrowser.adfilter

/**
 * 广告过滤规则模型（EasyList 兼容子集，PRD V1.0 §3.3.1 L1 规则过滤 + V1.2 §2.4 adfilter 职责）。
 *
 * EasyList 规则三要素：`[scheme://]domain/path`（网络类）、`##selector`（化妆隐藏）、`#?//script`（脚本拦截，本阶段不启用）。
 * 本模块支持的子集（对齐 PRD 首发承诺 L1–L3，注释标注取舍）：
 *  - 网络类（block）：URL/域名/通配片段（`*`）与锚点（`^` 分隔符）
 *  - 化妆类（cosmetic）：`##` / `#@#`（例外）元素隐藏选择器
 *  - 选项尾缀：`$third-party`、`$domain=...`、`$script` 等（Phase 4 解析基础字段，细分按需）
 *  - ⏳ 不支持（后续）：`#?//`（scriptlet 注入）、`@@` 之外的复杂正则（EasyList 正则 ` /.../ `）
 *
 * @property source 原始规则行（保留供"误杀上报：域名+规则 ID"——PRD §3.3.3 不含页面内容）
 */
data class AdRule(
    /** 稳定 ID（行序或内容哈希）；净化报告/误杀上报引用（PRD §3.3.3）。 */
    val id: Long,
    /** 原始规则文本（上报用）。 */
    val source: String,
    /** 规则类型。 */
    val kind: Kind,
    /** 网络类：URL 匹配模式（已归一化：`*` 通配、`^` 分隔符、去 option 尾缀后的主模式）。 */
    val pattern: String? = null,
    /** 化妆类：CSS 选择器（`##` 右侧）。 */
    val selector: String? = null,
    /** 化妆例外（`#@#`），命中时撤销同 selector 的隐藏。 */
    val isException: Boolean = false,
    /** 网络类选项（$ 右侧，逗号分隔已拆）。 */
    val options: List<String> = emptyList(),
    /** 是否仅第三方（$third-party）。 */
    val thirdPartyOnly: Boolean = false,
    /** 规则来源清单名（如 "GlimBase" / "EasyList"），用于报告分组。 */
    val list: String = "GlimBase",
) {
    enum class Kind {
        /** 网络请求拦截（PRD §3.3.5"请求级"）。 */
        NETWORK,
        /** 元素隐藏（化妆过滤，"内容级"）。 */
        COSMETIC,
        /** 化妆例外。 */
        COSMETIC_EXCEPTION,
    }
}
