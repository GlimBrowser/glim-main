package cn.ac.oac.glimbrowser.agent

import cn.ac.oac.glimbrowser.network.ChatMessage
import cn.ac.oac.glimbrowser.network.ResponseFormat

/**
 * Agent 工具协议（附件 C.1.1 输出协议矩阵 + PRD V1.1 §3.2.3 能力矩阵）。
 *
 * 硬约束（附件 C.1.1 MUST）：
 *  - 结构化工具（page_summary/page_translate/selection_enhance.{explain,summarize,translate}/table_extract/form_suggest）：
 *    `response_format=json_object`、**stream=false**
 *  - 对话类工具（selection_enhance.ask/page_qa/global_chat/write_assist）：
 *    **不带 response_format**、`stream=true`
 */
enum class AgentTool(
    val id: String,
    val kind: Kind,
    /** 结构化任务的 max_tokens 预算（附件 C 各工具）。 */
    val maxTokens: Int,
    /** 温度（附件 C.1.2：摘要/提取 0.2、翻译 0.3、解释 0.4）。 */
    val temperature: Double,
) {
    /** 网页摘要（附件 C.2）。 */
    PAGE_SUMMARY("page_summary", Kind.STRUCTURED, maxTokens = 1024, temperature = 0.2),

    /** 整页翻译（附件 C.3）。 */
    PAGE_TRANSLATE("page_translate", Kind.STRUCTURED, maxTokens = 1024, temperature = 0.3),

    /** 划词-解释（附件 C.4.1）。 */
    SELECTION_EXPLAIN("selection_enhance.explain", Kind.STRUCTURED, maxTokens = 512, temperature = 0.4),

    /** 划词-总结（附件 C.4.2）。 */
    SELECTION_SUMMARIZE("selection_enhance.summarize", Kind.STRUCTURED, maxTokens = 512, temperature = 0.2),

    /** 划词-翻译（附件 C.4.3）。 */
    SELECTION_TRANSLATE("selection_enhance.translate", Kind.STRUCTURED, maxTokens = 1024, temperature = 0.3),

    /** 划词-提问（附件 C.4.4，对话类）。 */
    SELECTION_ASK("selection_enhance.ask", Kind.DIALOG, maxTokens = 1024, temperature = 0.7),

    /** 页面问答（附件 C.5，对话类）。 */
    PAGE_QA("page_qa", Kind.DIALOG, maxTokens = 1024, temperature = 0.7),

    /** 全局对话（PRD §3.2.3 P1，对话类）。 */
    GLOBAL_CHAT("global_chat", Kind.DIALOG, maxTokens = 1024, temperature = 0.7),

    /** 表格提取（附件 C.6）。 */
    TABLE_EXTRACT("table_extract", Kind.STRUCTURED, maxTokens = 2048, temperature = 0.2),

    /** 写作辅助（PRD §3.2.3 P2，对话类）。 */
    WRITE_ASSIST("write_assist", Kind.DIALOG, maxTokens = 1024, temperature = 0.7),
    ;

    enum class Kind {
        /** 结构化：json_object + 非流式（附件 C.1.1）。 */
        STRUCTURED,
        /** 对话类：流式 + 无 response_format（附件 C.1.1）。 */
        DIALOG,
    }

    /** 构造 ChatRequest 级 response_format（结构化为 json_object；对话类 null —— 附件 C.1.1 红线）。 */
    fun responseFormat(): ResponseFormat? = when (kind) {
        Kind.STRUCTURED -> ResponseFormat.JsonObject
        Kind.DIALOG -> null
    }

    /** 是否流式（对话类 true）。 */
    fun stream(): Boolean = kind == Kind.DIALOG

    companion object {
        fun byId(id: String): AgentTool? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 工具任务请求（附件 C.1.2 结构化请求通用构造）。
 *
 * @property tool 工具
 * @property document 清洗后正文（结构化工具输入；与去广告清洗结果复用，PRD §3.3.4 协同）
 * @property selection 划词选区（selection_enhance 系列）
 * @property question 对话类问题（page_qa/global_chat/ask/write_assist）
 * @param schemaHint 工具 JSON Schema 描述（附件 C 各工具 schema；注入 system）
 */
data class AgentTask(
    val tool: AgentTool,
    val document: String = "",
    val selection: String = "",
    val question: String = "",
    val schemaHint: String = "",
    /** 任务定义句（system 首句，附件 C 各工具"system 任务句"）。 */
    val taskDefinition: String = "",
)

/**
 * Agent 任务结果（附件 C.1.5 降级链产物）。
 */
sealed interface AgentResult {
    /** 结构化成功：JSON 文本（已通过 Schema 校验 + 字段白名单，附件 C.1.2）。 */
    data class Structured(val json: String, val degraded: Boolean = false) : AgentResult

    /** 对话流式增量（拼接后渲染，附件 C.4.4/C.5）。 */
    data class Dialog(val text: String) : AgentResult

    /** 降级：原始纯文本（解析链全失败，附件 C.1.5 末级 + PRD §3.2.6 兜底）。 */
    data class DegradedText(val raw: String, val error: String) : AgentResult
}
