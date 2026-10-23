package cn.ac.oac.glimbrowser.logic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.ac.oac.glimbrowser.data.GlimBrowserDatabase
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 6 持久化接线测试（PRD §3.2.1 档案落库仅存 apiKeyRef + §3.1.3 历史 + 无痕不入史）。
 * jvmTest：内存 SQLite 驱动（SQLDelight 官方测试驱动）。
 */
class PersistenceWiringTest {

    private fun repo(): GlimRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
        GlimBrowserDatabase.Schema.create(driver)
        return GlimRepository(driver)
    }

    @Test
    fun guideDonePersistsProfileRefOnly() {
        val repo = repo()
        val c = BrowserController(repository = repo)
        // 走完三步引导
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.NextWelcome))
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.BaseUrlChanged("https://api.openai.com/v1")))
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.ApiKeyChanged("sk-SECRET-PLAINTEXT")))
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.ModelChanged("gpt-4o-mini")))
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.ValidateStep1))
        // 模拟测试成功（直接构造回填需要 event；用 finish 前置需 canSave——connectivity 为空 → finish 仍执行（finish 无守卫））
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.Finish))
        val profiles = repo.aiProfiles()
        assertEquals(1, profiles.size)
        val p = profiles.first()
        assertEquals("https://api.openai.com/v1", p.base_url)
        assertEquals("gpt-4o-mini", p.model)
        // 红线：明文 Key 永不入库（只存引用）
        assertTrue(p.api_key_ref.startsWith("ai:"))   // 引用前缀（明文 Key 在 SecureKeyStore）
        assertTrue(!p.api_key_ref.contains("SECRET") && !p.api_key_ref.contains("sk-"))
        // 整个库里都找不到明文 Key（PRD §7 埋点/存储红线）
        val dumped = repo.aiProfiles().joinToString { it.toString() + (it.last_connectivity_json ?: "") + (it.capabilities_json ?: "") }
        assertTrue(!dumped.contains("SECRET"))
    }

    @Test
    fun navigationRecordsHistory_ignoringHome() {
        val repo = repo()
        val c = BrowserController(repository = repo)
        val id = c.state.tabs.currentTabId!!
        // 首页伪 URL 不入史
        c.dispatch(BrowserController.Event.TabNavigated(id, TabManager.NEW_TAB_HOME_URL, null))
        assertTrue(repo.recentHistory().isEmpty())
        // 真实页面入史
        c.dispatch(BrowserController.Event.TabNavigated(id, "https://tech.example.com/kmp", "KMP 文章"))
        val rows = repo.recentHistory()
        assertEquals(1, rows.size)
        assertEquals("tech.example.com", rows.first().host)
        assertEquals("KMP 文章", rows.first().title)
    }

    @Test
    fun incognitoDoesNotRecordHistory() {
        // PRD §3.1.3 无痕不入史
        val repo = repo()
        val c = BrowserController(repository = repo)
        c.dispatch(BrowserController.Event.SetIncognito(true))
        val id = c.state.tabs.currentTabId!!
        c.dispatch(BrowserController.Event.TabNavigated(id, "https://secret.example/page", "机密"))
        assertTrue(repo.recentHistory().isEmpty())
    }

    @Test
    fun noRepositoryMeansNoCrash() {
        val c = BrowserController()   // repository = null（测试/无持久化模式）
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.NextWelcome))
        c.dispatch(BrowserController.Event.ApiGuide(ApiGuideEvent.Finish))
        val id = c.state.tabs.currentTabId!!
        c.dispatch(BrowserController.Event.TabNavigated(id, "https://x.io/", "x"))
        assertEquals(ApiGuideState.Step.DONE, c.state.apiGuide.step)
    }
}
