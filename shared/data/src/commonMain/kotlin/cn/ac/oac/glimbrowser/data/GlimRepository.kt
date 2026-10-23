package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver

/**
 * 数据仓库（PRD V1.2 §2.4 data 职责：历史/书签/AI 档案/插件注册表/规则统计；SQLDelight 六端同构）。
 *
 * 红线（000 §2）：
 *  - **AI 档案永不存明文 Key**（[upsertAiProfile] 只接受 apiKeyRef 引用）
 *  - 无痕标签不入历史（调用方写入前判定，PRD §3.1.3 —— 本仓库提供 [recordVisit] 但不判断模式）
 *  - 上报层需对 host 哈希（PRD §7.2；本仓库存明文 host 供本地 TOP SITES 展示）
 *
 * @param driver 各端注入（android-driver / native-driver / sqlite-driver(jvm test) / jdbc）
 */
class GlimRepository(driver: SqlDriver) {

    private val db = GlimBrowserDatabase(driver)
    private val q = db.glimBrowserQueries

    // ===== 历史（PRD §3.1.4） =====

    /** 记录访问：新 URL 插入，已存在则 touch（计数+1、标题/时间更新）。无痕不调用此方法（§3.1.3）。 */
    fun recordVisit(url: String, title: String, host: String, atEpochMs: Long) {
        // 先 UPDATE（命中则计数+1），未命中再 INSERT（避免先插后 +1 的双增 bug，实测踩坑）
        val updated = q.touchHistory(title, atEpochMs, url).value
        if (updated == 0L) {
            q.insertHistoryIfNew(url, title, host, atEpochMs)
        }
    }

    fun searchHistory(term: String, limit: Int = 50): List<History_entry> = q.searchHistory(term, term, limit.toLong()).executeAsList()

    fun recentHistory(limit: Int = 100): List<History_entry> = q.recentHistory(limit.toLong()).executeAsList()

    fun clearHistory() = q.clearHistory()

    fun removeHistory(id: Long) = q.deleteHistoryById(id)

    // ===== 书签（PRD §3.1.4 文件夹/导入导出） =====

    fun addBookmark(url: String, title: String, folder: String = "", atEpochMs: Long, position: Int = 0) =
        q.insertBookmark(url, title, folder, atEpochMs, position.toLong())

    fun listBookmarks(): List<Bookmark> = q.listBookmarks().executeAsList()

    fun removeBookmark(id: Long) = q.deleteBookmark(id)

    fun removeBookmarkByUrl(url: String) = q.deleteBookmarkByUrl(url)

    /** 更新书签标题/文件夹（delete+insert 保持 url 唯一约束语义）。 */
    fun updateBookmark(id: Long, url: String, title: String, folder: String, atEpochMs: Long) {
        q.deleteBookmark(id)
        q.insertBookmark(url, title, folder, atEpochMs, 0)
    }

    /** 文件夹改名（该文件夹下全部书签一起改）。 */
    fun renameFolder(oldName: String, newName: String) {
        q.listBookmarks().executeAsList()
            .filter { it.folder == oldName }
            .forEach { updateBookmark(it.id, it.url, it.title, newName, it.created_at) }
    }

    /** 删除文件夹（其中书签移入未分类，不丢数据）。 */
    fun deleteFolder(name: String) = renameFolder(name, "")

    /** 是否已收藏。 */
    fun isBookmarked(url: String): Boolean = q.listBookmarks().executeAsList().any { it.url == url }

    // ===== AI 档案（PRD §3.2.1；红线：仅存 apiKeyRef） =====

    fun upsertAiProfile(profile: Ai_profile_record) {
        // 双写：存在则 update（幂等），否则 insert（sqldelight 2.1 无 upsert 语法，见 106 文档）
        q.insertAiProfile(
            id = profile.id, name = profile.name, base_url = profile.base_url, api_key_ref = profile.api_key_ref,
            model = profile.model, timeout_seconds = profile.timeout_seconds, stream_enabled = profile.stream_enabled,
            summary_language = profile.summary_language, last_connectivity_json = profile.last_connectivity_json,
            capabilities_json = profile.capabilities_json, updated_at = profile.updated_at,
        )
        q.updateAiProfile(
            name = profile.name, base_url = profile.base_url, api_key_ref = profile.api_key_ref, model = profile.model,
            timeout_seconds = profile.timeout_seconds, stream_enabled = profile.stream_enabled,
            summary_language = profile.summary_language, last_connectivity_json = profile.last_connectivity_json,
            capabilities_json = profile.capabilities_json, updated_at = profile.updated_at, id = profile.id,
        )
    }

    fun aiProfiles(): List<Ai_profile_record> = q.listAiProfiles().executeAsList()

    fun aiProfile(id: String): Ai_profile_record? = q.getAiProfile(id).executeAsOneOrNull()

    fun deleteAiProfile(id: String) = q.deleteAiProfile(id)

    // ===== 插件（PRD §3.4） =====

    fun upsertPlugin(p: Plugin_install) {
        q.insertPlugin(
            id = p.id, name = p.name, version = p.version, description = p.description, source_url = p.source_url,
            fingerprint = p.fingerprint, matches_json = p.matches_json, permissions_json = p.permissions_json,
            granted_json = p.granted_json, enabled = p.enabled, builtin = p.builtin,
            subscription_id = p.subscription_id, revoked = p.revoked, installed_at = p.installed_at,
        )
        q.updatePlugin(
            name = p.name, version = p.version, description = p.description, source_url = p.source_url,
            fingerprint = p.fingerprint, matches_json = p.matches_json, permissions_json = p.permissions_json,
            granted_json = p.granted_json, enabled = p.enabled, subscription_id = p.subscription_id,
            revoked = p.revoked, id = p.id,
        )
    }

    fun plugins(): List<Plugin_install> = q.listPlugins().executeAsList()

    fun setPluginEnabled(id: String, enabled: Boolean) = q.setPluginEnabled(if (enabled) 1L else 0L, id)

    fun setPluginGranted(id: String, grantedJson: String) = q.setPluginGranted(grantedJson, id)

    fun removePlugin(id: String) = q.deletePlugin(id)

    // ===== 去广告设置与统计（PRD §3.3.2/§3.3.3） =====

    fun putFilterSetting(key: String, value: String, atEpochMs: Long) {
        q.insertFilterSetting(key, value, atEpochMs)
        q.updateFilterSetting(value, atEpochMs, key)
    }

    fun filterSetting(key: String): String? = q.getFilterSetting(key).executeAsOneOrNull()

    fun allFilterSettings(): List<Filter_setting> = q.allFilterSettings().executeAsList()

    fun addDailyStat(day: String, category: String, delta: Int) {
        // 先建行（count=0），再累加 delta —— 避免 insert(delta)+add(delta) 双计（实测踩坑）
        q.insertFilterStatDaily(day, category, 0)
        q.addFilterStatDaily(delta.toLong(), day, category)
    }

    fun dailyStats(day: String): List<Filter_stats_daily> = q.dailyFilterStats(day).executeAsList()

    fun sumBlocked(day: String): Long = q.sumTodayBlocked(day).executeAsOne()
}
