package cn.ac.oac.glimbrowser.ai

import cn.ac.oac.glimbrowser.data.GlimRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI 会话与消息（本地持久化：settings 键 `ai_sessions` 存 JSON；最多 30 会话 / 每会话 60 条）。
 * 说明：只存对话文本，不含 API Key，也不含整页正文（仅记录提问与回答）。
 */
@Serializable
data class AiMessage(val role: String, val content: String, val at: Long)

@Serializable
data class AiSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<AiMessage> = emptyList(),
) {
    val preview: String get() = messages.lastOrNull { it.role == "assistant" }?.content?.take(60)
        ?: messages.lastOrNull()?.content?.take(60) ?: "新会话"
}

/** 会话仓库（基于 KV 表，避免 schema 迁移）。 */
class AiSessionStore(private val repository: GlimRepository?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<AiSession> {
        val raw = runCatching { repository?.filterSetting(KEY) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<AiSession>>(raw) }.getOrElse { emptyList() }
    }

    fun save(sessions: List<AiSession>) {
        runCatching {
            repository?.putFilterSetting(
                KEY,
                json.encodeToString(sessions.take(30).map { it.copy(messages = it.messages.takeLast(60)) }),
                nowMs(),
            )
        }
    }

    fun append(sessions: List<AiSession>, sessionId: String, message: AiMessage): List<AiSession> {
        val idx = sessions.indexOfFirst { it.id == sessionId }
        val updated = if (idx >= 0) {
            val s = sessions[idx]
            sessions.toMutableList().also {
                it[idx] = s.copy(messages = s.messages + message, updatedAt = message.at)
            }
        } else {
            listOf(
                AiSession(
                    id = sessionId,
                    title = message.content.take(24).ifBlank { "新会话" },
                    createdAt = message.at,
                    updatedAt = message.at,
                    messages = listOf(message),
                ),
            ) + sessions
        }
        val sorted = updated.sortedByDescending { it.updatedAt }
        save(sorted)
        return sorted
    }

    fun delete(sessions: List<AiSession>, sessionId: String): List<AiSession> {
        val rest = sessions.filterNot { it.id == sessionId }
        save(rest)
        return rest
    }

    private fun nowMs(): Long = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds

    companion object {
        const val KEY = "ai_sessions"
    }
}
