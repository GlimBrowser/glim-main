package cn.ac.oac.glimbrowser.agent

/**
 * 上下文构建（PRD V1.1 §3.2.2：正文抽取 → 清洗 → 分块；token 预算；附件 C.2.1 分块规则）。
 *
 * 协同去广告（PRD §3.3.4）：调用方传入的 [rawText] 应为**去广告清洗后的正文**（与净化结果共用解析产物），
 * 本类只做 Agent 侧清洗（去控制字符、超长行截断、空白归一），不做 HTML 抽取（阅读模式解析器职责，Phase 6/后续）。
 *
 * 纯 Kotlin KMP；token 估算为保守近似（中英混合 ~2.5 chars/token 英文、~1.5 chars/token 中文 → 取 2 chars/token 保守上界）。
 */
object ContextBuilder {

    /** token 估算（保守：2 字符/token）。 */
    fun estimateTokens(text: String): Int = (text.length + 1) / 2

    /**
     * 清洗正文（附件 C.1.4 围栏清洗的前置步骤；Agent 上下文用）。
     *  - 去控制字符（保留 \n \t）
     *  - 超长行截断（2000 字符/行，防单行炸弹）
     *  - 连续空行折叠为单空行
     */
    fun clean(rawText: String): String = buildString(rawText.length) {
        var blankRun = 0
        for (rawLine in rawText.lineSequence()) {
            val line = rawLine.filter { it == '\n' || it == '\t' || !it.isISOControl() }
            val trimmedLine = if (line.length > 2000) line.substring(0, 2000) else line
            if (trimmedLine.isBlank()) {
                blankRun++
                if (blankRun > 1) continue
            } else blankRun = 0
            append(trimmedLine).append('\n')
        }
    }

    /**
     * 按 token 预算分块（附件 C.2.1：约 3500 tokens/块；C.1.3 并发度 2）。
     * @param maxTokens 单块 token 预算（默认 3500，附件 C.2.1）
     * @return 分块列表（尽量在换行处断开）
     */
    fun chunkByTokens(text: String, maxTokens: Int = 3500): List<String> {
        val maxChars = maxTokens * 2   // 保守 2 chars/token
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            var end = minOf(i + maxChars, text.length)
            if (end < text.length) {
                val nl = text.lastIndexOf('\n', end)
                // 在换行处断开：保留换行符在前一块结尾（段落边界，PRD §3.2.2）
                if (nl > i + maxChars / 2) end = nl + 1
            }
            out.add(text.substring(i, end))
            i = end
        }
        return out
    }

    /**
     * 分块摘要 + 汇总的两级流水入口（附件 C.2.1）：返回分块数供 UI 展示进度（渐进呈现，PRD §3.2.4）。
     */
    fun planSummaryPipeline(text: String): PipelinePlan {
        val cleaned = clean(text)
        val chunks = chunkByTokens(cleaned)
        return PipelinePlan(
            cleanedText = cleaned,
            chunks = chunks,
            estimatedTotalTokens = estimateTokens(cleaned),
        )
    }

    data class PipelinePlan(
        val cleanedText: String,
        val chunks: List<String>,
        val estimatedTotalTokens: Int,
    ) {
        val requiresTwoStage: Boolean get() = chunks.size > 1
    }
}
