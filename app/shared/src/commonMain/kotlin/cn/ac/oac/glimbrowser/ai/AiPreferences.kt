package cn.ac.oac.glimbrowser.ai

import cn.ac.oac.glimbrowser.data.GlimRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI 配置项（此前只做了 Base URL / Key / Model，其余开关未落地）。
 * 持久化：settings 键 `ai_prefs` 存 JSON，随应用启动恢复。
 */
@Serializable
data class AiPreferences(
    /** AI 总开关（关闭后所有入口提示未启用）。 */
    val enabled: Boolean = true,
    /** 打开 AI 面板时自动摘要当前页。 */
    val autoSummaryOnOpen: Boolean = false,
    /** 流式输出（对话类；关闭则整段返回）。 */
    val streamEnabled: Boolean = true,
    /** 优先要求 json_object（关闭则直接走文本 + 本地解析）。 */
    val preferJsonObject: Boolean = true,
    /** 允许降级解析（关闭则解析失败直接提示错误，不自动降级）。 */
    val allowDegrade: Boolean = true,
    /** 无痕模式下禁用 AI（隐私）。 */
    val disableInIncognito: Boolean = true,
    /** 是否把当前页面 URL 一并发送（默认关，最少信息原则）。 */
    val sendPageUrl: Boolean = false,
    /** 摘要语言：auto / zh / en。 */
    val summaryLanguage: String = "auto",
    /** 请求超时（秒）。 */
    val timeoutSeconds: Int = 60,
    /** 采样温度 ×10（存整数避免浮点序列化差异；50 = 0.5）。 */
    val temperaturePermille: Int = 3,
    /** 单次最大输出 tokens。 */
    val maxTokens: Int = 1024,
    /** 自定义请求头（每行 `Key: Value`）。 */
    val customHeaders: String = "",
) {
    val temperature: Double get() = temperaturePermille / 10.0

    /** 解析自定义头（忽略空行与非法行）。 */
    fun parsedHeaders(): Map<String, String> = customHeaders.lineSequence()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
        .filter { it.first.isNotEmpty() }
        .toMap()
}

/** AI 配置读写（KV 表，避免 schema 迁移）。 */
class AiPreferencesStore(private val repository: GlimRepository?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AiPreferences {
        val raw = runCatching { repository?.filterSetting(KEY) }.getOrNull() ?: return AiPreferences()
        return runCatching { json.decodeFromString<AiPreferences>(raw) }.getOrElse { AiPreferences() }
    }

    fun save(prefs: AiPreferences) {
        runCatching {
            repository?.putFilterSetting(KEY, json.encodeToString(prefs), nowMs())
        }
    }

    private fun nowMs(): Long = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds

    companion object { const val KEY = "ai_prefs" }
}
