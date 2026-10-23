package cn.ac.oac.glimbrowser.ai

import cn.ac.oac.glimbrowser.agent.AgentOrchestrator
import cn.ac.oac.glimbrowser.agent.AgentResult
import cn.ac.oac.glimbrowser.agent.AgentSchemas
import cn.ac.oac.glimbrowser.agent.AgentTask
import cn.ac.oac.glimbrowser.agent.AgentTool
import cn.ac.oac.glimbrowser.agent.ContextBuilder
import cn.ac.oac.glimbrowser.network.AiError
import cn.ac.oac.glimbrowser.network.AiProfile
import cn.ac.oac.glimbrowser.network.OpenAiCompatClient
import cn.ac.oac.glimbrowser.network.OpenAiSseClient
import cn.ac.oac.glimbrowser.securekey.SecureKeyStore

/**
 * AI 运行时（真实链路：BYOK 直连 → orchestrator → 结果）。
 *
 * 组装：HttpClient（平台引擎）→ OpenAiCompatClient（结构化）/ OpenAiSseClient（对话流式）
 *      → AgentOrchestrator（附件 C.1.5 降级链）→ UI 状态。
 *
 * 红线（000 §2）：
 *  - Key 从平台 SecureKeyStore 读取，仅注入 Authorization 头（不落日志/DB）
 *  - 结构化走 json_object；对话类流式且不带 response_format（orchestrator 保证）
 *  - 页面正文仅经用户配置的端点（BYOK 直连，不经我方服务器）
 *
 * @param keyStore 平台安全区（读取 Key 明文仅本次调用栈）
 */
class AiRuntime(
    private val keyStore: SecureKeyStore,
) {
    private val httpClient = createAiHttpClient()
    private val chat = OpenAiCompatClient(httpClient).apply {
        keyProvider = { ref -> keyStore.get(ref) ?: "" }
    }
    private val sse = OpenAiSseClient(httpClient).apply {
        keyProvider = { ref -> keyStore.get(ref) ?: "" }
    }
    private val orchestrator = AgentOrchestrator(chat = chat, sse = sse)

    /** 摘要当前页（结构化 page_summary；正文超长自动分块两级流水，附件 C.2.1）。 */
    suspend fun summarize(
        profile: AiProfile,
        pageText: String,
        prefs: cn.ac.oac.glimbrowser.ai.AiPreferences = cn.ac.oac.glimbrowser.ai.AiPreferences(),
    ): AiOutcome {
        val cleaned = ContextBuilder.clean(pageText)
        if (cleaned.isBlank()) return AiOutcome.Error("页面正文为空（可能仍在加载或该页无可读文本）")

        val effective = profile.copy(
            timeoutSeconds = prefs.timeoutSeconds,
            extraHeaders = profile.extraHeaders + prefs.parsedHeaders(),
        )
        val languageHint = when (prefs.summaryLanguage) {
            "zh" -> "请用中文输出。"
            "en" -> "Please answer in English."
            else -> ""
        }

        // 关闭"优先 JSON"时：走纯文本摘要（真实文本模式，而非假装结构化）
        if (!prefs.preferJsonObject) {
            val text = runCatching {
                val req = cn.ac.oac.glimbrowser.network.ChatRequest(
                    model = effective.model,
                    messages = listOf(
                        cn.ac.oac.glimbrowser.network.ChatMessage(
                            cn.ac.oac.glimbrowser.network.ChatMessage.ROLE_SYSTEM,
                            "你是网页摘要助手。用 3-5 句话总结页面要点，第一行给标题。$languageHint",
                        ),
                        cn.ac.oac.glimbrowser.network.ChatMessage.userWithFencedDocument(cleaned),
                    ),
                    responseFormat = null,
                    stream = false,
                    temperature = prefs.temperature,
                    maxTokens = prefs.maxTokens,
                )
                chat.chatCompletion(effective, req).getOrNull()?.text
            }.getOrNull()
            if (text.isNullOrBlank()) return AiOutcome.Error("模型未返回内容，请检查端点或网络")
            return AiOutcome.Summary(textToSummaryJson(text), degraded = true)
        }

        val task = AgentTask(
            tool = AgentTool.PAGE_SUMMARY,
            document = cleaned,
            schemaHint = AgentSchemas.PAGE_SUMMARY_SCHEMA,
            taskDefinition = "你是网页摘要助手。阅读 <document> 内的网页正文，输出结构化摘要 JSON。$languageHint",
        )
        return when (val r = orchestrator.runStructured(effective, task, AgentSchemas.PAGE_SUMMARY_REQUIRED)) {
            is AgentResult.Structured -> AiOutcome.Summary(r.json, degraded = r.degraded)
            is AgentResult.DegradedText ->
                // 关闭"允许降级"时：不静默用降级结果
                if (!prefs.allowDegrade) AiOutcome.Error(r.error.ifBlank { "结构化解析失败（已按设置禁用降级）" })
                else AiOutcome.Error(r.error.ifBlank { "解析失败，已降级" }, raw = r.raw.takeIf { it.isNotBlank() })
            is AgentResult.Dialog -> AiOutcome.Error("协议不符")
        }
    }

    /** 纯文本摘要 → 与结构化渲染兼容的 JSON（标题/要点/结论）。 */
    private fun textToSummaryJson(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.firstOrNull()?.take(120) ?: "摘要"
        val points = lines.drop(1).take(6)
        val conclusion = lines.lastOrNull()?.take(300) ?: ""
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"schema_version\":\"1.0\",\"title\":\"${esc(title)}\",\"key_points\":[" +
            points.joinToString(",") { "{\"text\":\"${esc(it.take(300))}\"}" } +
            "],\"conclusion\":\"${esc(conclusion)}\"}"
    }

    /**
     * 页面问答（对话类，流式）：返回增量文本流；UI 逐段渲染（附件 C.5）。
     * 调用方需在协程内 collect。
     */
    fun askFlow(profile: AiProfile, pageText: String, question: String): kotlinx.coroutines.flow.Flow<String> =
        askFlow(profile, pageText, emptyList(), question)

    /**
     * 多轮问答：把历史轮次一并发送（上下文 = system + 页面正文 + 历史 + 新问题）。
     * @param history 既往往返（role/content），由会话存储提供
     */
    fun askFlow(
        profile: AiProfile,
        pageText: String,
        history: List<Pair<String, String>>,
        question: String,
        prefs: cn.ac.oac.glimbrowser.ai.AiPreferences = cn.ac.oac.glimbrowser.ai.AiPreferences(),
    ): kotlinx.coroutines.flow.Flow<String> {
        val effective = profile.copy(
            timeoutSeconds = prefs.timeoutSeconds,
            extraHeaders = profile.extraHeaders + prefs.parsedHeaders(),
        )
        val messages = buildList {
            add(
                cn.ac.oac.glimbrowser.network.ChatMessage(
                    role = cn.ac.oac.glimbrowser.network.ChatMessage.ROLE_SYSTEM,
                    content = "你是网页阅读助手。基于 <document> 内的页面内容回答；无法定位细节时如实说明。",
                ),
            )
            add(cn.ac.oac.glimbrowser.network.ChatMessage.userWithFencedDocument(ContextBuilder.clean(pageText)))
            history.forEach { (role, content) ->
                if (content.isNotBlank()) add(cn.ac.oac.glimbrowser.network.ChatMessage(role, content))
            }
            add(cn.ac.oac.glimbrowser.network.ChatMessage(cn.ac.oac.glimbrowser.network.ChatMessage.ROLE_USER, question))
        }
        return if (prefs.streamEnabled) {
            orchestrator.runDialogMessages(effective, messages)
        } else {
            // 非流式：一次性返回（对话类仍不带 response_format）
            kotlinx.coroutines.flow.flow {
                val req = cn.ac.oac.glimbrowser.network.ChatRequest(
                    model = effective.model,
                    messages = messages,
                    responseFormat = null,
                    stream = false,
                    temperature = prefs.temperature,
                    maxTokens = prefs.maxTokens,
                )
                chat.chatCompletion(effective, req).getOrNull()?.text?.let { emit(it) }
            }
        }
    }

    /** 连通性测试（已保存档案；AI 设置页使用）。 */
    suspend fun testConnection(profile: AiProfile) = chat.testConnection(profile)

    /** 连通性测试（三步引导：Key 尚未落安全区，显式传入）。 */
    suspend fun testConnectionRaw(baseUrl: String, apiKey: String, model: String, timeoutSeconds: Int = 30) =
        chat.testConnectionRaw(baseUrl, apiKey, model, timeoutSeconds)

    /** 模型列表（引导 STEP1 使用：端点/Key 未落库）。 */
    suspend fun listModelsRaw(baseUrl: String, apiKey: String, timeoutSeconds: Int = 30): List<String> =
        chat.listModelsRaw(baseUrl, apiKey, timeoutSeconds)
            .map { r -> r.data.map { it.id }.filter { it.isNotBlank() }.distinct().sorted() }
            .getOrElse { throw it }

    /** 关闭底层客户端（应用退出）。 */
    fun dispose() = httpClient.close()

    /** 错误文案（复用附件 D.3 映射表，全平台一致）。 */
    fun messageOf(error: AiError): String = error.userMessage
}

/** AI 调用结果（结构化用于渲染）。 */
sealed interface AiOutcome {
    data class Summary(val json: String, val degraded: Boolean) : AiOutcome
    data class Error(val message: String, val raw: String? = null) : AiOutcome
}
