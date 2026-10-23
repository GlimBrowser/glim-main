package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 数据仓库集成测试（内存 SQLite；PRD V1.2 §2.4 六端同构 schema + 000 §2 红线）。
 * jvmTest 用 sqlite-driver（SQLDelight 官方测试驱动），commonTest 不依赖驱动。
 */
class GlimRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
    private val repo: GlimRepository
    private val db: GlimBrowserDatabase

    init {
        // 首次建表（生成的 schema 创建语句）
        GlimBrowserDatabase.Schema.create(driver)
        db = GlimBrowserDatabase(driver)
        repo = GlimRepository(driver)
    }

    @AfterTest
    fun tearDown() { driver.close() }

    @Test
    fun historyInsertAndTouchIncrementsCount() {
        repo.recordVisit("https://a.com/x", "标题A", "a.com", atEpochMs = 1000)
        repo.recordVisit("https://a.com/x", "标题A2", "a.com", atEpochMs = 2000)
        repo.recordVisit("https://b.com/y", "标题B", "b.com", atEpochMs = 3000)
        val recent = repo.recentHistory()
        assertEquals(2, recent.size)                      // 去重按 url
        val a = recent.first { it.url == "https://a.com/x" }
        assertEquals(2L, a.visit_count)
        assertEquals("标题A2", a.title)
        assertEquals(2000L, a.visited_at)
    }

    @Test
    fun historySearchMatchesTitleOrUrl() {
        repo.recordVisit("https://kmp-guide.dev/article", "KMP 指南", "kmp-guide.dev", 1000)
        repo.recordVisit("https://other.org/", "别的", "other.org", 2000)
        assertEquals(1, repo.searchHistory("KMP").size)
        assertEquals(1, repo.searchHistory("kmp-guide").size)
        assertEquals(2, repo.searchHistory("").size)
    }

    @Test
    fun historyClearWorks() {
        repo.recordVisit("https://x.io/", "t", "x.io", 1)
        repo.clearHistory()
        assertTrue(repo.recentHistory().isEmpty())
    }

    @Test
    fun bookmarksFolderAndDedupe() {
        repo.addBookmark("https://b.io/", "B", folder = "工具", atEpochMs = 1)
        repo.addBookmark("https://b.io/", "B-dup", folder = "工具", atEpochMs = 2)   // UNIQUE url → 忽略
        repo.addBookmark("https://c.io/", "C", folder = "阅读", atEpochMs = 3)
        val all = repo.listBookmarks()
        assertEquals(2, all.size)
        assertTrue(all.any { it.folder == "工具" })
        repo.removeBookmarkByUrl("https://b.io/")
        assertEquals(1, repo.listBookmarks().size)
    }

    @Test
    fun aiProfileStoredWithoutPlaintextKey_refOnly() {
        // 000 §2 红线：schema 无明文 Key 列；api_key_ref 仅引用
        val p = Ai_profile_record(
            id = "p1", name = "官方", base_url = "https://api.openai.com/v1",
            api_key_ref = "keystore://key-1",   // 引用（非 sk- 明文）
            model = "gpt-4o-mini", timeout_seconds = 60, stream_enabled = 1,
            summary_language = "auto", last_connectivity_json = null, capabilities_json = null, updated_at = 100,
        )
        repo.upsertAiProfile(p)
        repo.upsertAiProfile(p.copy(model = "gpt-4o"))   // 幂等 update
        val rows = repo.aiProfiles()
        assertEquals(1, rows.size)
        assertEquals("gpt-4o", rows.first().model)
        assertTrue(rows.first().api_key_ref.startsWith("keystore://"))
        assertFalse(rows.first().api_key_ref.contains("sk-"))
        // 二次 upsert 不重复（insertOrIgnore + update 幂等）
        assertEquals(1, repo.aiProfiles().size)
    }

    @Test
    fun aiProfileDelete() {
        val p = Ai_profile_record(
            id = "p2", name = "n", base_url = "https://x/v1", api_key_ref = "kr", model = "m",
            timeout_seconds = 60, stream_enabled = 1, summary_language = "auto",
            last_connectivity_json = null, capabilities_json = null, updated_at = 1,
        )
        repo.upsertAiProfile(p)
        assertNotNull(repo.aiProfile("p2"))
        repo.deleteAiProfile("p2")
        assertNull(repo.aiProfile("p2"))
    }

    @Test
    fun pluginInstallEnableRevoke() {
        val plugin = Plugin_install(
            id = "ad-killer", name = "广告清理", version = "1.0", description = "d",
            source_url = "https://cdn/x.js", fingerprint = "fp", matches_json = """["*.a.com"]""",
            permissions_json = """["MODIFY_STYLE"]""", granted_json = """["MODIFY_STYLE"]""",
            enabled = 1, builtin = 0, subscription_id = null, revoked = 0, installed_at = 1,
        )
        repo.upsertPlugin(plugin)
        assertEquals(1, repo.plugins().size)
        repo.setPluginEnabled("ad-killer", false)
        assertEquals(0L, repo.plugins().first().enabled)
        repo.setPluginGranted("ad-killer", """["MODIFY_STYLE","NETWORK"]""")
        assertEquals("""["MODIFY_STYLE","NETWORK"]""", repo.plugins().first().granted_json)
        // 幂等 upsert 不重复
        repo.upsertPlugin(plugin.copy(version = "2.0"))
        assertEquals(1, repo.plugins().size)
        assertEquals("2.0", repo.plugins().first().version)
        repo.removePlugin("ad-killer")
        assertTrue(repo.plugins().isEmpty())
    }

    @Test
    fun filterSettingsRoundTrip() {
        repo.putFilterSetting("global_enabled", "true", 1)
        repo.putFilterSetting("global_enabled", "false", 2)   // update path
        assertEquals("false", repo.filterSetting("global_enabled"))
        repo.putFilterSetting("site:news.site", "DISABLED", 3)
        assertEquals(2, repo.allFilterSettings().size)
        assertEquals("DISABLED", repo.filterSetting("site:news.site"))
    }

    @Test
    fun dailyFilterStatsAggregate() {
        repo.addDailyStat("2026-10-20", "展示广告", 3)
        repo.addDailyStat("2026-10-20", "展示广告", 2)   // 累加 → 5
        repo.addDailyStat("2026-10-20", "追踪器", 1)
        assertEquals(6L, repo.sumBlocked("2026-10-20"))  // 当日全部类别总拦截 = 5+1（PRD §3.3.3 报告口径）
        val stats = repo.dailyStats("2026-10-20")
        assertEquals(2, stats.size)
        val display = stats.first { it.category == "展示广告" }
        assertEquals(5L, display.count)
    }
}
