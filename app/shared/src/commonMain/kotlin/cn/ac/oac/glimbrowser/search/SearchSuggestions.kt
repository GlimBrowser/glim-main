package cn.ac.oac.glimbrowser.search

import cn.ac.oac.glimbrowser.data.GlimRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * 本地搜索联想（不联网、不上传）：
 *  - 记录用户提交过的搜索词（次数 + 最近使用时间）
 *  - 输入时做前缀/包含匹配，按「次数 × 时间衰减」排序
 *  - 随着使用自动进化（越常搜越靠前；久不用的自然衰减）
 */
@Serializable
data class SuggestionRecord(
    val query: String,
    val count: Int = 1,
    /** 最近使用时间（epoch ms）。 */
    val lastUsed: Long = 0,
)

class SearchSuggestionStore(private val repository: GlimRepository?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<SuggestionRecord> {
        val raw = runCatching { repository?.filterSetting(KEY) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<SuggestionRecord>>(raw) }.getOrElse { emptyList() }
    }

    private fun save(list: List<SuggestionRecord>) {
        runCatching { repository?.putFilterSetting(KEY, json.encodeToString(list), nowMs()) }
    }

    /** 记录一次搜索（提交或点击联想项都算），用于自进化。 */
    fun record(query: String, now: Long = nowMs()) {
        val q = query.trim()
        if (q.isEmpty() || q.length > 80) return
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.query.equals(q, ignoreCase = true) }
        if (idx >= 0) {
            list[idx] = list[idx].copy(count = list[idx].count + 1, lastUsed = now)
        } else {
            list.add(SuggestionRecord(q, 1, now))
        }
        save(list.sortedWith(compareByDescending<SuggestionRecord> { score(it, now) }).take(MAX_ENTRIES))
    }

    /** 输入联想：前缀优先，其次包含；空输入返回最常用的若干条。 */
    fun suggest(input: String, limit: Int = 8, now: Long = nowMs()): List<String> {
        val q = input.trim()
        val all = load()
        if (all.isEmpty()) return emptyList()
        val matched = if (q.isEmpty()) all else all.filter { it.query.contains(q, ignoreCase = true) }
        return matched
            .sortedWith(
                compareByDescending<SuggestionRecord> { it.query.startsWith(q, ignoreCase = true) }
                    .thenByDescending { score(it, now) },
            )
            .take(limit)
            .map { it.query }
    }

    fun remove(query: String) {
        save(load().filterNot { it.query.equals(query.trim(), ignoreCase = true) })
    }

    fun clear() = save(emptyList())

    /** 次数权重 + 7 天半衰：旧词自动淡出，常用词自动上浮。 */
    private fun score(r: SuggestionRecord, now: Long): Double {
        val ageDays = ((now - r.lastUsed).coerceAtLeast(0L)) / 86_400_000.0
        val decay = 0.5.pow(ageDays / 7.0)
        return r.count * (0.4 + 0.6 * decay)
    }

    private fun nowMs(): Long = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds

    companion object {
        const val KEY = "search_suggestions"
        const val MAX_ENTRIES = 200
    }
}
