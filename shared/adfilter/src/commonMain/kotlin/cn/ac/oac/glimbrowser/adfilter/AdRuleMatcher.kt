package cn.ac.oac.glimbrowser.adfilter

/**
 * 规则匹配器（PRD V1.2 §2.4：adfilter「匹配」；纯 Kotlin KMP，供平台执行层调用）。
 *
 * 匹配语义（EasyList 网络类子集）：
 *  - `*` → 任意串；`^` → 分隔符（EasyList：非 [A-Za-z0-9] 且非 `_-./%` 等）
 *  - 选项（Phase 4 子集）：`third-party`（第三方判定由调用方传入 isThirdParty）、
 *    `domain=a|b`（发起域白名单，调用方传 requestHost）
 *  - 化妆类：selector 字符串相等匹配 + `#@#` 例外优先（真正选择器求值在平台 DOM 执行层）
 *
 * 实现：common 无 java.util.regex → 手写**分段通配匹配器**（字面量/`*`/`^` token 化 + 记忆化回溯）。
 * 对 EasyList 典型 pattern（<200 char）足够；长 pattern 性能风险已注释，Phase 4 需要时再优化。
 */
class AdRuleMatcher(
    rules: List<AdRule>,
) {

    /** 预编译网络规则。 */
    private val network: List<CompiledNetworkRule> = rules
        .filter { it.kind == AdRule.Kind.NETWORK }
        .map { r -> compilePattern(r.pattern ?: "").copy(rule = r) }

    /** 化妆隐藏 selector → 规则。 */
    private val cosmeticHide: Map<String, AdRule> = rules
        .filter { it.kind == AdRule.Kind.COSMETIC && it.selector != null }
        .associateBy { it.selector!! }

    /** 化妆例外 selector 集合（#@#，EasyList：例外优先）。 */
    private val cosmeticExceptions: Set<String> = rules
        .filter { it.kind == AdRule.Kind.COSMETIC_EXCEPTION && it.selector != null }
        .mapNotNull { it.selector }
        .toSet()

    /**
     * 网络请求判定。
     *
     * @param url 完整请求 URL
     * @param requestHost 请求发起页域名（用于 `$domain=`，null 时跳过该选项判定）
     * @param isThirdParty 是否第三方（调用方判定；null 时不启用 third-party 过滤）
     * @return 命中的规则（null=放行）；命中规则供统计/上报（**不含页面内容**，PRD §7 埋点红线）
     */
    fun matchNetwork(url: String, requestHost: String? = null, isThirdParty: Boolean? = null): AdRule? {
        for (c in network) {
            if (c.rule.thirdPartyOnly && isThirdParty == false) continue
            val domainOpt = c.rule.options.firstOrNull { it.startsWith("domain=") }
            if (domainOpt != null && requestHost != null) {
                val allowed = domainOpt.removePrefix("domain=").split('|')
                if (allowed.none { hostMatches(requestHost, it) }) continue
            }
            if (matchesTokens(c.tokens, url)) return c.rule
        }
        return null
    }

    /**
     * 化妆隐藏判定：返回该选择器对应的隐藏规则；例外（#@#）命中则返回 null（撤销隐藏）。
     * 平台执行层（iOS/macOS WKContentRuleList css-display-none / Android 注入 CSS）消费（PRD §3.3.5）。
     */
    fun matchCosmetic(selector: String): AdRule? {
        if (selector in cosmeticExceptions) return null
        return cosmeticHide[selector]
    }

    fun isCosmeticException(selector: String): Boolean = selector in cosmeticExceptions

    val networkRuleCount: Int get() = network.size
    val cosmeticRuleCount: Int get() = cosmeticHide.size

    // ===== pattern 编译与匹配 =====

    private fun compilePattern(pattern: String): CompiledNetworkRule {
        // ---- 首部锚点解析（EasyList 语义，实测踩坑：直接丢 | 会导致 ||domain^ 在位置 0 永不命中）----
        // ||xxx  → 域锚：匹配 "任一前缀(含 scheme://host 边界) + xxx"，等价 leading Any（分段匹配从任意位置起）
        // |xxx   → 严格起点锚：URL 必须以 xxx 开头（无 leading Any，从位置 0 匹配）
        // （无锚） → 同分段任意起点（EasyList 无锚 pattern 是子串匹配）
        var body = pattern
        var leadingAnchorStart = false   // true=严格位置0（单|锚）；false=任意起点（分段匹配本身允许）
        if (body.startsWith("||")) {
            body = body.substring(2)     // 域锚：剥掉 ||，用 leading Any 表达"任意 scheme/host 前缀"
        } else if (body.startsWith("|")) {
            body = body.substring(1)
            leadingAnchorStart = true
        }
        // 尾锚 '|'（如 '...$' 或 '...|'）：子集近似——尾锚要求 URL 结束匹配（记录标记）
        var trailingAnchorEnd = false
        if (body.endsWith("|") && !body.endsWith("||")) {
            body = body.dropLast(1)
            trailingAnchorEnd = true
        }

        val tokens = ArrayList<Token>()
        if (!leadingAnchorStart) tokens.add(Token.Any)  // 默认/域锚：允许任意起点（等价分段前 Any）
        val literal = StringBuilder()
        fun flush() {
            if (literal.isNotEmpty()) {
                tokens.add(Token.Literal(literal.toString()))
                literal.clear()
            }
        }
        for (ch in body) {
            when (ch) {
                '*' -> { flush(); tokens.add(Token.Any) }
                '^' -> { flush(); tokens.add(Token.Sep) }
                '|' -> Unit // pattern 中间残余 | （如 a||b）按子集忽略
                else -> literal.append(ch)
            }
        }
        flush()
        if (trailingAnchorEnd) tokens.add(Token.EndAnchor)
        return CompiledNetworkRule(
            rule = AdRule(id = -1, source = pattern, kind = AdRule.Kind.NETWORK), // 占位：调用方 map 时替换真 rule
            tokens = tokens,
        )
    }

    /** 分段模式 token。 */
    private sealed interface Token {
        data class Literal(val s: String) : Token
        data object Any : Token
        data object Sep : Token
        /** 尾锚 `|`：URL 必须在此 token 后结束。 */
        data object EndAnchor : Token
    }

    private fun matchesTokens(tokens: List<Token>, text: String): Boolean {
        memo.clear()
        return matchFrom(tokens, 0, text, 0)
    }

    private val memo = HashMap<String, Boolean>()

    private fun matchFrom(tokens: List<Token>, ti: Int, text: String, xi: Int): Boolean {
        if (ti == tokens.size) return true
        val key = "$ti,$xi"
        memo[key]?.let { return it }
        val result: Boolean = when (val t = tokens[ti]) {
            is Token.Literal -> {
                val s = t.s
                xi + s.length <= text.length && text.regionMatches(xi, s, 0, s.length) &&
                    matchFrom(tokens, ti + 1, text, xi + s.length)
            }
            Token.Any -> {
                // 关键：k 必须允许到 text.length（Any 可消费空串，否则 [Literal@k, EndAnchor] 类模式在串尾失败）
                var k = xi
                var found = false
                while (k <= text.length) {
                    if (matchFrom(tokens, ti + 1, text, k)) { found = true; break }
                    k++
                }
                found
            }
            Token.Sep -> {
                // ^：分隔符 = 非字母数字 且 非 `_-./%`（EasyList 规范核心集）
                if (xi >= text.length) true
                else {
                    val c = text[xi]
                    val alphaNum = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'
                    val keepChar = c == '_' || c == '-' || c == '.' || c == '%'  // EasyList：'/' 是分隔符（^ 应命中），不能列入 keep
                    val isSeparator = !alphaNum && !keepChar
                    if (isSeparator) matchFrom(tokens, ti + 1, text, xi + 1) else false
                }
            }
            Token.EndAnchor ->
                // 尾锚：只在其为最后 token 时表示"URL 到此结束"；若后面还有 token 则按可消费零宽继续
                ti == tokens.lastIndex && xi == text.length
        }
        memo[key] = result
        return result
    }

    /** 域名精确/子域匹配（`$domain=` 与 host 判定）。 */
    private fun hostMatches(host: String, pattern: String): Boolean {
        if (pattern == "*") return true
        return host == pattern || host.endsWith(".$pattern")
    }

    /** 预编译网络规则（rule + token 串）。 */
    private data class CompiledNetworkRule(val rule: AdRule, val tokens: List<Token>)
}
