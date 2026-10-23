package cn.ac.oac.glimbrowser.ai

import cn.ac.oac.glimbrowser.logic.ApiGuideState
import cn.ac.oac.glimbrowser.network.AiProfile

/**
 * AI 抽屉 UI 状态（真实链路的状态机；无静态占位）。
 */
data class AiPanelState(
    val phase: Phase = Phase.Idle,
    /** 摘要结果（结构化 JSON 原文 → 渲染层解析字段）。 */
    val summaryJson: String? = null,
    val summaryDegraded: Boolean = false,
    /** 问答增量文本（流式追加）。 */
    val answer: String = "",
    val question: String = "",
    val error: String? = null,
    /** 长文分块进度（两级流水，附件 C.2.1）。 */
    val stage: String = "",
) {
    enum class Phase { Idle, Loading, Summary, Answering, Error }

    val busy: Boolean get() = phase == Phase.Loading || phase == Phase.Answering
}

/**
 * 档案解析：优先 DB 档案（真持久化），回退三步引导内存态（刚配置未重启）。
 * 返回的 [AiProfile.apiKeyRef] 供 [AiRuntime] 从 SecureKeyStore 取 Key。
 */
fun resolveAiProfile(guide: ApiGuideState, stored: cn.ac.oac.glimbrowser.data.Ai_profile_record?): AiProfile? {
    stored?.let { row ->
        return AiProfile(
            id = row.id,
            name = row.name,
            baseUrl = row.base_url,
            apiKeyRef = row.api_key_ref,
            model = row.model,
            timeoutSeconds = row.timeout_seconds.toInt(),
            streamEnabled = row.stream_enabled != 0L,
        )
    }
    if (guide.step == ApiGuideState.Step.DONE && guide.baseUrlInput.isNotBlank() && guide.modelInput.isNotBlank()) {
        return AiProfile(
            id = "default",
            name = "默认配置",
            baseUrl = guide.baseUrlInput,
            apiKeyRef = cn.ac.oac.glimbrowser.securekey.SecureKeyStore.aiProfileRef("default"),
            model = guide.modelInput,
        )
    }
    return null
}

/** 摘要 JSON → 可渲染字段（title/key_points/conclusion；缺失字段容错，不崩）。 */
data class SummaryView(
    val title: String,
    val keyPoints: List<String>,
    val conclusion: String,
    val readingMinutes: Int?,
)

fun parseSummaryView(json: String): SummaryView {
    fun str(key: String): String {
        val i = json.indexOf("\"$key\"")
        if (i < 0) return ""
        val colon = json.indexOf(':', i)
        val q1 = json.indexOf('"', colon + 1)
        if (q1 < 0) return ""
        val sb = StringBuilder()
        var k = q1 + 1
        while (k < json.length) {
            val c = json[k]
            if (c == '\\' && k + 1 < json.length) { sb.append(json[k + 1]); k += 2; continue }
            if (c == '"') break
            sb.append(c); k++
        }
        return sb.toString()
    }
    val points = ArrayList<String>()
    val kpIdx = json.indexOf("\"key_points\"")
    if (kpIdx >= 0) {
        val open = json.indexOf('[', kpIdx)
        val close = json.indexOf(']', open)
        if (open > 0 && close > open) {
            val arr = json.substring(open, close)
            var idx = 0
            while (true) {
                val i = arr.indexOf("\"text\"", idx)
                if (i < 0) break
                val colon = arr.indexOf(':', i)
                val q1 = arr.indexOf('"', colon + 1)
                if (q1 < 0) break
                val sb = StringBuilder(); var k = q1 + 1
                while (k < arr.length) {
                    val c = arr[k]
                    if (c == '\\' && k + 1 < arr.length) { sb.append(arr[k + 1]); k += 2; continue }
                    if (c == '"') break
                    sb.append(c); k++
                }
                if (sb.isNotEmpty()) points.add(sb.toString())
                idx = k + 1
            }
        }
    }
    val mins = Regex("\"reading_time_min\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
    return SummaryView(title = str("title"), keyPoints = points, conclusion = str("conclusion"), readingMinutes = mins)
}
