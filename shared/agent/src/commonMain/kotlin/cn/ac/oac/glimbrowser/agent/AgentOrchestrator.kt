package cn.ac.oac.glimbrowser.agent

import cn.ac.oac.glimbrowser.network.AiProfile
import cn.ac.oac.glimbrowser.network.ChatMessage
import cn.ac.oac.glimbrowser.network.ChatRequest
import cn.ac.oac.glimbrowser.network.OpenAiCompatClient
import cn.ac.oac.glimbrowser.network.OpenAiSseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Agent 编排器（PRD V1.1 §3.2.2：上下文构建 → 工具选择 → 协议适配 → 输出解析；
 * 附件 C.1.2/C.1.3/C.1.4/C.1.5 执行细则）。
 *
 * 上下文构建（PRD §3.2.2，附件 C.2.1）：
 *  - 正文基于阅读模式清洗结果（与去广告共用，PRD §3.3.4 协同——由调用方传入已清洗正文）
 *  - 超长分块：按 token 预算分块（简化按字符 ~4token/字英混合估算），结构化长文走"分块摘要+汇总"两级流水（C.1.3）
 *  - 注入防护（C.1.4 MUST）：system 固定"只输出 JSON"+<document> 围栏；正文围栏清洗已由 ChatMessage.userWithFencedDocument 完成
 *
 * @param chat 非流式客户端（结构化工具）
 * @param sse 流式客户端（对话类工具）
 * @param chunkChars 单块正文字符数（C.2.1：约 3500 tokens/块 ≈ 14000 chars 英文；中文 ~4000 chars/token 更紧，取保守 8000）
 */
class AgentOrchestrator(
    private val chat: OpenAiCompatClient,
    private val sse: OpenAiSseClient,
    private val fallback: JsonFallbackChain = JsonFallbackChain(),
    private val chunkChars: Int = 8000,
) {

    /** 分块并发度（附件 C.1.3：并发度 2，可配）。 */
    var chunkConcurrency: Int = 2

    /** 降级事件出口（打点：仅 stage+toolId+errorType，PRD §7 埋点红线）。 */
    var onFallbackEvent: ((JsonFallbackChain.FallbackEvent) -> Unit)? = null

    /**
     * 执行单个结构化工具任务（附件 C 矩阵 STRUCTURED）。
     *
     * @param profile BYOK 档案
     * @param task 任务（含 document/schemaHint/taskDefinition）
     * @param requiredKeys 该工具 Schema 必填键（附件 C 各工具 required；白名单校验）
     * @param chunkHandler 长文分块时的每块任务构造（附件 C.1.3 分段流水线；null=不分块单请求）
     */
    suspend fun runStructured(
        profile: AiProfile,
        task: AgentTask,
        requiredKeys: Set<String> = emptySet(),
    ): AgentResult {
        val tool = task.tool
        require(tool.kind == AgentTool.Kind.STRUCTURED) { "runStructured 仅适用结构化工具（附件 C.1.1）" }

        // 长文分块流水线（附件 C.1.3：分块 json_object → 客户端合并；不做 JSON 字符级流式）
        val chunks = if (task.document.length > chunkChars) chunkDocument(task.document) else listOf(task.document)
        if (chunks.size > 1) {
            val partials = ArrayList<String>(chunks.size)
            for (chunk in chunks) {   // 简化串行（并发度控制 Phase 5 后半；先保证正确性）
                val r = requestStructured(profile, task.copy(document = chunk), requiredKeys)
                when (r) {
                    is AgentResult.Structured -> partials.add(r.json)
                    is AgentResult.DegradedText -> return r  // 任一块全链失败 → 整体降级（不半成品渲染）
                    is AgentResult.Dialog -> Unit
                }
            }
            return mergeChunkSummaries(profile, partials, tool)
        }
        return requestStructured(profile, task, requiredKeys)
    }

    /**
     * 执行对话类工具（附件 C 矩阵 DIALOG：stream=true、无 response_format）。
     * @return 增量文本 Flow（调用方渲染纯文本，防注入：markdown 链接纯文本化，附件 C.4.4）
     */
    fun runDialog(profile: AiProfile, task: AgentTask): Flow<String> = flow {
        val tool = task.tool
        require(tool.kind == AgentTool.Kind.DIALOG) { "runDialog 仅适用对话类工具（附件 C.1.1）" }
        val messages = buildDialogMessages(task)
        val request = ChatRequest(
            model = profile.model,
            messages = messages,
            responseFormat = null,          // 对话类红线：不带 json_object
            stream = true,
            temperature = tool.temperature,
            maxTokens = tool.maxTokens,
        )
        // streamChat 是 suspend(返回 Flow)：在 flow{} 协程体内 collect 其产物并转发
        sse.streamChat(profile, request).collect { emit(it) }
    }

    /**
     * 多轮对话（AI 会话历史）：messages 由调用方按「system + 页面上下文 + 历史轮次 + 新问题」组织。
     * 仍遵守对话类红线：stream=true、不带 response_format。
     */
    fun runDialogMessages(profile: AiProfile, messages: List<ChatMessage>, tool: AgentTool = AgentTool.PAGE_QA): Flow<String> = flow {
        require(tool.kind == AgentTool.Kind.DIALOG) { "runDialogMessages 仅适用对话类工具" }
        val request = ChatRequest(
            model = profile.model,
            messages = messages,
            responseFormat = null,
            stream = true,
            temperature = tool.temperature,
            maxTokens = tool.maxTokens,
        )
        sse.streamChat(profile, request).collect { emit(it) }
    }

    // ===== internals =====

    /** 结构化请求（含 json_object 降级链解析，附件 C.1.5）。 */
    private suspend fun requestStructured(
        profile: AiProfile,
        task: AgentTask,
        requiredKeys: Set<String>,
    ): AgentResult {
        val tool = task.tool
        val messages = buildStructuredMessages(task)
        val request = ChatRequest(
            model = profile.model,
            messages = messages,
            responseFormat = tool.responseFormat(),   // 结构化红线：json_object
            stream = false,                          // 附件 C.1.1：结构化非流式
            temperature = tool.temperature,
            maxTokens = tool.maxTokens,
        )
        // 一次重试（附件 C.1.5：重试请求 1 次附上次错误摘要）
        repeat(2) { attempt ->
            val result = chat.chatCompletion(profile, request)
            result.onSuccess { resp ->
                val raw = resp.text ?: return AgentResult.DegradedText("", "EmptyResponse")
                return when (val outcome = fallback.parseStructured(raw, tool.id, requiredKeys, onFallbackEvent)) {
                    is JsonFallbackChain.ParseOutcome.Success ->
                        AgentResult.Structured(outcome.json, degraded = outcome.degraded)
                    is JsonFallbackChain.ParseOutcome.Failed -> {
                        if (attempt == 0) return@repeat  // 重试一次
                        AgentResult.DegradedText(outcome.rawText, "PARSE_FAILED")
                    }
                }
            }
            result.onFailure { t ->
                // AiException 已映射错误码（附件 C.8）；结构化失败不无限重试（withRetry 在 client 层已退避）
                return AgentResult.DegradedText("", t.message ?: "UNKNOWN")
            }
        }
        return AgentResult.DegradedText("", "RETRY_EXHAUSTED")
    }

    /**
     * 分块摘要合并（附件 C.2.1 两级流水：分块 json_object → 汇总 json_object）。
     * 简化实现：把各块 JSON 文本拼接为输入再请求一次 page_summary 汇总。
     */
    private suspend fun mergeChunkSummaries(profile: AiProfile, partials: List<String>, tool: AgentTool): AgentResult {
        val mergedDoc = partials.joinToString("\n---\n")
        val summaryTask = AgentTask(
            tool = AgentTool.PAGE_SUMMARY,
            document = mergedDoc,
            schemaHint = AgentSchemas.PAGE_SUMMARY_SCHEMA,
            taskDefinition = "你是网页摘要助手。阅读 <document> 内的分块摘要 JSON 片段，输出结构化汇总摘要 JSON。",
        )
        return requestStructured(profile, summaryTask, requiredKeys = emptySet())
    }
}

// ===== 顶层 internal 工具函数（消息构造/分块；独立于客户端实例，便于无网络单测） =====

/** system 构造（附件 C.1.2/C.1.4：任务定义 + JSON 约束 + 围栏声明）。 */
internal fun buildStructuredMessages(task: AgentTask): List<ChatMessage> = listOf(
    ChatMessage.systemWithFence(task.taskDefinition, task.schemaHint),
    ChatMessage.userWithFencedDocument(task.document.ifBlank { task.selection }),
)

/** 对话类 messages：页面上下文摘要 + 最近问答（附件 C.5：上下文 = 页面元信息+正文摘要块）。 */
internal fun buildDialogMessages(task: AgentTask): List<ChatMessage> = listOf(
    ChatMessage(
        role = ChatMessage.ROLE_SYSTEM,
        content = "你是网页阅读助手。基于<document>内的页面内容回答；如无法定位细节，如实说明。" +
            "回答中的引用以 (§块号) 标注来源（附件 C.5.2）。",
    ),
    ChatMessage.userWithFencedDocument(task.document),
    ChatMessage(ChatMessage.ROLE_USER, task.question),
)

/** 分块（附件 C.2.1：超长按块；C.1.3 分段流水线）。 */
internal fun chunkDocument(doc: String, size: Int = 8000): List<String> {
    if (doc.length <= size) return listOf(doc)
    val out = ArrayList<String>()
    var i = 0
    while (i < doc.length) {
        val end = minOf(i + size, doc.length)
        // 尽量在换行处断开（保持段落边界，PRD §3.2.2 上下文构建）
        var cut = end
        if (end < doc.length) {
            val nl = doc.lastIndexOf('\n', end)
            if (nl > i + size / 2) cut = nl + 1   // 换行处断开且保留换行（段落边界）
        }
        out.add(doc.substring(i, cut))
        i = cut
    }
    return out
}

