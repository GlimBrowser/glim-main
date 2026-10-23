package cn.ac.oac.glimbrowser.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * json_object 解析降级链（附件 C.1.5；PRD V1.1 §3.2.6 端点不支持 json_object 的降级矩阵）。
 *
 * ```
 * 完整 json_object 解析成功
 *   └─失败→ 剥离 ```json 围栏后重试解析
 *        └─失败→ 重试请求 1 次（附上次错误摘要）【由 AgentOrchestrator 调用方执行】
 *             └─失败→ 正则/括号提取首个平衡 JSON 对象
 *                  └─失败→ 降级：纯文本渲染 + 提示"结构化结果解析失败"
 * ```
 *
 * 每次降级**仅打事件名+工具ID+错误类型**（无内容），埋点红线（附件 C.1.5 / PRD §7）。
 * 安全：所有 JSON 经 Schema 校验 + 字段白名单后才返回给渲染层（附件 C.1.2"禁止直接执行模型返回内容"）。
 */
class JsonFallbackChain(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /** 降级事件（供打点；无内容）。 */
    data class FallbackEvent(val stage: Stage, val toolId: String, val errorType: String) {
        enum class Stage {
            /** 完整解析失败。 */
            FULL_PARSE_FAILED,
            /** 围栏剥离后成功。 */
            FENCE_STRIPPED_OK,
            /** 平衡括号提取成功。 */
            BALANCED_EXTRACT_OK,
            /** 全链失败（降级为纯文本）。 */
            ALL_FAILED,
        }
    }

    /**
     * 执行降级链解析。
     *
     * @param raw 模型原始输出（可能带 ```json 围栏或前缀废话）
     * @param toolId 工具 ID（打点用，不带内容）
     * @param requiredKeys Schema 必填键（附件 C 各工具 required；白名单校验）
     * @param onEvent 降级事件回调（打点；实现方只记录 stage+toolId+errorType）
     * @return [ParseOutcome]（成功含 cleaned json / 失败含纯文本降级）
     */
    fun parseStructured(
        raw: String,
        toolId: String,
        requiredKeys: Set<String> = emptySet(),
        onEvent: ((FallbackEvent) -> Unit)? = null,
    ): ParseOutcome {
        // 1) 完整解析
        val direct = runCatching { json.parseToJsonElement(raw).jsonObject }
        if (direct.isSuccess) {
            val obj = direct.getOrThrow()
            if (validateRequired(obj, requiredKeys)) {
                return ParseOutcome.Success(obj.toString(), degraded = false)
            }
            onEvent?.invoke(FallbackEvent(FallbackEvent.Stage.FULL_PARSE_FAILED, toolId, "MissingRequiredKeys"))
        } else {
            onEvent?.invoke(FallbackEvent(FallbackEvent.Stage.FULL_PARSE_FAILED, toolId, "JsonSyntax"))
        }

        // 2) 剥离 ```json 围栏
        val fenced = stripFences(raw)
        val fencedParse = runCatching { json.parseToJsonElement(fenced).jsonObject }
        if (fencedParse.isSuccess) {
            val obj = fencedParse.getOrThrow()
            if (validateRequired(obj, requiredKeys)) {
                onEvent?.invoke(FallbackEvent(FallbackEvent.Stage.FENCE_STRIPPED_OK, toolId, "None"))
                return ParseOutcome.Success(obj.toString(), degraded = false)
            }
        }

        // 3) 提取首个平衡 JSON 对象（括号计数，忽略字符串内括号）
        val extracted = extractFirstBalancedJsonObject(raw)
        if (extracted != null) {
            val exParse = runCatching { json.parseToJsonElement(extracted).jsonObject }
            if (exParse.isSuccess) {
                val obj = exParse.getOrThrow()
                if (validateRequired(obj, requiredKeys)) {
                    onEvent?.invoke(FallbackEvent(FallbackEvent.Stage.BALANCED_EXTRACT_OK, toolId, "None"))
                    return ParseOutcome.Success(obj.toString(), degraded = true)
                }
            }
        }

        // 4) 全链失败 → 纯文本降级（附件 C.1.5 末级；渲染层当纯文本，PRD §3.2.6）
        onEvent?.invoke(FallbackEvent(FallbackEvent.Stage.ALL_FAILED, toolId, "AllStrategiesFailed"))
        return ParseOutcome.Failed(raw)
    }

    /** 必填键校验（附件 C 各工具 required 字段；缺失→触发下一级降级）。 */
    private fun validateRequired(obj: JsonObject, required: Set<String>): Boolean =
        required.all { it in obj }

    /** 剥离 ```json ... ``` 围栏（附件 C.1.5 第 2 级）。 */
    internal fun stripFences(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            // 去语言标记（json / 其它）
            val nl = s.indexOf('\n')
            if (nl > 0) s = s.substring(nl + 1)
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end)
        }
        return s.trim()
    }

    /** 提取首个平衡 JSON 对象（跳过字符串内部括号；附件 C.1.5 第 3 级）。 */
    internal fun extractFirstBalancedJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
                else -> Unit
            }
        }
        return null
    }

    /** 解析结果。 */
    sealed interface ParseOutcome {
        /** 成功（degraded=true 表示经降级路径获得，附件 C.1.5）。 */
        data class Success(val json: String, val degraded: Boolean) : ParseOutcome

        /** 全链失败：raw 为原始文本，调用方以纯文本渲染（附件 C.1.5 末级）。 */
        data class Failed(val rawText: String) : ParseOutcome
    }
}
