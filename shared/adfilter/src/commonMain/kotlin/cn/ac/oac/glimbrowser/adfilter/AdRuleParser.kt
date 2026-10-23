package cn.ac.oac.glimbrowser.adfilter

/**
 * EasyList 兼容规则解析器（PRD V1.2 §2.4：adfilter「规则解析（EasyList 兼容）」；纯 Kotlin KMP）。
 *
 * 支持语法（首发 L1–L3 所需子集）：
 *  - 注释：`!` 开头行、空行 → 忽略
 *  - 头部：`[Adblock Plus 2.0]` 类方括号头 → 忽略
 *  - 化妆类：`##selector`（隐藏）、`#@#selector`（例外）
 *  - 网络类：其余（可含 `$options` 选项尾缀、`|` 锚点、`*` 通配、`^` 分隔符）
 *  - 不支持（解析期跳过并计数，Phase 后续/如实统计）：`#?//` scriptlet、`/regex/` 正则规则
 *
 * 解析为一次性构建（启动本地加载，PRD §3.3.2 L1"启动时本地加载"）；解析失败行**跳过不抛**（容错，
 * 规则清单质量不应导致浏览器崩溃），返回统计供诊断。
 */
object AdRuleParser {

    /** 解析结果：规则集 + 跳过/诊断统计。 */
    data class Result(
        val rules: List<AdRule>,
        /** 跳过的行数（注释/头部/不支持语法/空）。 */
        val skipped: Int,
        /** 因不支持语法被跳过的行（scriptlet/正则），用于如实统计（PRD 不虚报拦截能力）。 */
        val unsupportedSyntax: Int,
    ) {
        val networkRules: List<AdRule> get() = rules.filter { it.kind == AdRule.Kind.NETWORK }
        val cosmeticRules: List<AdRule> get() = rules.filter { it.kind == AdRule.Kind.COSMETIC }
        val cosmeticExceptions: List<AdRule> get() = rules.filter { it.kind == AdRule.Kind.COSMETIC_EXCEPTION }
    }

    /** 解析多行规则文本（EasyList 格式：\n 或 \r\n 分隔）。 */
    fun parse(text: String, listName: String = "GlimBase"): Result {
        val rules = ArrayList<AdRule>()
        var skipped = 0
        var unsupported = 0
        var id = 0L
        for (raw in text.split('\n')) {
            val line = raw.trim().removeSuffix("\r")
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
                skipped++
                continue
            }
            // 不支持语法（如实统计）：scriptlet #?// 与正则 /.../
            // 注意：'/adframe/' 这类纯路径规则不能误判为正则——仅当两斜杠内含正则元字符才算 regex
            if (line.contains("#?//") || isRegexRule(line)) {
                unsupported++
                skipped++
                continue
            }
            val rule = parseLine(line, id = id++, listName = listName)
            if (rule == null) skipped++ else rules.add(rule)
        }
        return Result(rules = rules, skipped = skipped, unsupportedSyntax = unsupported)
    }

    private fun parseLine(line: String, id: Long, listName: String): AdRule? {
        // 化妆类：## / #@#（注意选项符 $ 不会出现在化妆品行）
        val cosIdx = line.indexOf("##")
        val cosExcIdx = line.indexOf("#@#")
        if (cosIdx >= 0 || cosExcIdx >= 0) {
            val exc = cosExcIdx >= 0 && (cosIdx < 0 || cosExcIdx < cosIdx)
            val marker = if (exc) "#@#" else "##"
            val at = line.indexOf(marker)
            val selector = line.substring(at + marker.length)
            if (selector.isBlank()) return null
            return AdRule(
                id = id,
                source = line,
                kind = if (exc) AdRule.Kind.COSMETIC_EXCEPTION else AdRule.Kind.COSMETIC,
                selector = selector,
                isException = exc,
                list = listName,
            )
        }
        // 网络类：拆 $options（注意 pattern 内允许 $，EasyList 用最后一个 $ 分隔选项——此处按约定：options 只含 [a-z=-,]）
        val dollar = findOptionsSplit(line)
        val pattern = if (dollar >= 0) line.substring(0, dollar) else line
        val optionsRaw = if (dollar >= 0) line.substring(dollar + 1) else ""
        if (pattern.isBlank()) return null
        val options = optionsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return AdRule(
            id = id,
            source = line,
            kind = AdRule.Kind.NETWORK,
            pattern = pattern,
            options = options,
            thirdPartyOnly = options.any { it == "third-party" || it == "3p" },
            list = listName,
        )
    }

    /** 判定 `/.../` 正则规则：首尾是 '/' 且内部含正则元字符（纯路径如 /adframe/ 不算）。 */
    private fun isRegexRule(line: String): Boolean {
        if (!line.startsWith("/") || !line.endsWith("/")) return false
        val inner = line.substring(1, line.length - 1)
        if (inner.isEmpty()) return false
        val meta = charArrayOf('.', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|', '\\')
        return meta.any { it in inner }
    }

    /** EasyList 已知选项关键字（子集，PRD L1–L3 首发所需）——用于消歧：options 段必须全部由已知项构成才分割。 */
    private val KNOWN_OPTIONS: Set<String> = setOf(
        "third-party", "3p", "first-party", "1p", "script", "image", "stylesheet", "font",
        "media", "object", "subdocument", "document", "webrtc", "ping", "popup", "xmlhttp",
        "other", "important", "match-case", "ghide", "shide", "ehide", "ghide2", "badfilter",
        "csp", "removeparam", "generichide", "genericblock", "inline-script", "empty",
    )

    /**
     * 找"选项分隔 $"位置（消歧规则）：
     *  - `$` 右侧须能按 `,` 拆出**全部已知**选项（或 `domain=` / `denyallow=` 形式）才认定为选项分隔符；
     *  - 否则视为 pattern 内部字符（如 `||ex.com/pa$th` 的 `$th` 不是选项）。
     * 这比"全小写字母即可"更保守，避免把 pattern 尾巴误当选项（单测 dollarInPatternNotSplitWhenOptionsLike）。
     */
    private fun findOptionsSplit(line: String): Int {
        var i = line.length - 1
        while (i >= 0) {
            if (line[i] == '$') {
                val opts = line.substring(i + 1)
                if (opts.isNotEmpty() && looksLikeOptions(opts)) return i
            }
            i--
        }
        return -1
    }

    private fun looksLikeOptions(opts: String): Boolean {
        val parts = opts.split(',').map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() }) return false
        return parts.all { part ->
            part in KNOWN_OPTIONS ||
                part.startsWith("domain=") ||
                part.startsWith("denyallow=") ||
                part.startsWith("method=") ||
                part.startsWith("redirect=")
        }
    }
}
