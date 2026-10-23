package cn.ac.oac.glimbrowser.bookmark

import cn.ac.oac.glimbrowser.data.GlimRepository
import kotlinx.serialization.json.Json

/**
 * 书签文件夹管理：
 *  - 现有文件夹 = 书签里用到的文件夹 ∪ 用户显式创建过的空文件夹（存 settings 键 `bookmark_folders`）
 *  - 支持新建、改名（联动书签）、删除（书签移入未分类，不丢数据）
 */
class FolderStore(private val repository: GlimRepository?) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun extras(): List<String> {
        val raw = runCatching { repository?.filterSetting(KEY) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }
    }

    private fun saveExtras(list: List<String>) {
        runCatching {
            repository?.putFilterSetting(KEY, json.encodeToString(list.distinct().filter { it.isNotBlank() }), nowMs())
        }
    }

    /** 全部文件夹及其书签数量（按名称排序）。 */
    fun folders(): Map<String, Int> {
        val counts = runCatching { repository?.listBookmarks().orEmpty().groupingBy { it.folder.ifBlank { "" } }.eachCount() }
            .getOrElse { emptyMap() }
        val out = LinkedHashMap<String, Int>()
        counts.entries.filter { it.key.isNotBlank() }.sortedBy { it.key }.forEach { e -> out[e.key] = e.value }
        extras().sorted().forEach { name -> if (name !in out) out[name] = 0 }
        if (counts.containsKey("")) out[""] = counts.getValue("")
        return out
    }

    fun create(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        saveExtras(extras() + n)
    }

    fun rename(old: String, new: String) {
        val n = new.trim()
        if (old.isBlank()) return
        runCatching { repository?.renameFolder(old, n) }
        saveExtras(extras().filterNot { it == old } + listOfNotNull(n.takeIf { it.isNotBlank() }))
    }

    fun delete(name: String) {
        if (name.isBlank()) return
        runCatching { repository?.deleteFolder(name) }
        saveExtras(extras().filterNot { it == name })
    }

    private fun nowMs(): Long = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds

    companion object { const val KEY = "bookmark_folders" }
}
