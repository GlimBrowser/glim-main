package cn.ac.oac.glimbrowser.plugin

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.ac.oac.glimbrowser.data.GlimBrowserDatabase
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.data.Plugin_install
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 插件脚本存取与作用域解析（不依赖真实内核）。 */
class PluginRunnerTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
    private val repo: GlimRepository

    init {
        GlimBrowserDatabase.Schema.create(driver)
        repo = GlimRepository(driver)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun scriptSavedAndLoaded() {
        PluginRunner.saveScript(repo, "p1", "console.log('hi')", 1)
        assertEquals("console.log('hi')", PluginRunner.loadScript(repo, "p1"))
        assertNull(PluginRunner.loadScript(repo, "missing"))
    }

    @Test
    fun installStoresBothScriptAndRecord() {
        val plugin = Plugin_install(
            id = "p2", name = "测试脚本", version = "1.0", description = "",
            source_url = "https://x/p2.user.js", fingerprint = "",
            matches_json = """["*.example.com"]""", permissions_json = """["MODIFY_STYLE"]""",
            granted_json = """["MODIFY_STYLE"]""", enabled = 1, builtin = 0,
            subscription_id = null, revoked = 0, installed_at = 1,
        )
        PluginRunner.install(repo, plugin, "document.title = 'x';", 1)
        assertEquals(1, repo.plugins().size)
        assertEquals("document.title = 'x';", PluginRunner.loadScript(repo, "p2"))
    }

    @Test
    fun scopeMatchingWorks() {
        assertTrue(ScopeMatcher.matchesAny(listOf("*.example.com"), "news.example.com"))
        assertTrue(!ScopeMatcher.matchesAny(listOf("*.example.com"), "evil.org"))
    }
}
