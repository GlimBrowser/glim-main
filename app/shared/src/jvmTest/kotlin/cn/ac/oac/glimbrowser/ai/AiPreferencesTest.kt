package cn.ac.oac.glimbrowser.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.ac.oac.glimbrowser.data.GlimBrowserDatabase
import cn.ac.oac.glimbrowser.data.GlimRepository
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AI 配置项持久化与解析。 */
class AiPreferencesTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
    private val repo: GlimRepository

    init {
        GlimBrowserDatabase.Schema.create(driver)
        repo = GlimRepository(driver)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun defaultsAreSane() {
        val p = AiPreferences()
        assertTrue(p.enabled)
        assertTrue(p.disableInIncognito)
        assertTrue(!p.sendPageUrl)
        assertEquals(60, p.timeoutSeconds)
        assertEquals(0.3, p.temperature, 0.001)
        assertEquals(1024, p.maxTokens)
    }

    @Test
    fun roundTripThroughSettings() {
        val store = AiPreferencesStore(repo)
        val custom = AiPreferences(
            enabled = false,
            autoSummaryOnOpen = true,
            streamEnabled = false,
            preferJsonObject = false,
            allowDegrade = false,
            disableInIncognito = false,
            sendPageUrl = true,
            summaryLanguage = "en",
            timeoutSeconds = 90,
            temperaturePermille = 7,
            maxTokens = 2048,
            customHeaders = "X-A: 1\nX-B: two",
        )
        store.save(custom)
        val loaded = store.load()
        assertEquals(custom, loaded)
        assertEquals(mapOf("X-A" to "1", "X-B" to "two"), loaded.parsedHeaders())
    }

    @Test
    fun malformedHeadersIgnored() {
        val p = AiPreferences(customHeaders = "no-colon-line\nX-Ok: v\n:bad")
        assertEquals(mapOf("X-Ok" to "v"), p.parsedHeaders())
    }
}
