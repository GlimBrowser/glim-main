package cn.ac.oac.glimbrowser.search

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.ac.oac.glimbrowser.data.GlimBrowserDatabase
import cn.ac.oac.glimbrowser.data.GlimRepository
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 本地搜索联想：记录、前缀优先、时间衰减、上限。 */
class SearchSuggestionsTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
    private val repo: GlimRepository

    init {
        GlimBrowserDatabase.Schema.create(driver)
        repo = GlimRepository(driver)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun recordsAndSuggestsByPrefixFirst() {
        val store = SearchSuggestionStore(repo)
        store.record("kmp 多窗口", now = 1_000)
        store.record("webview 拦截", now = 1_000)
        store.record("kmp 多窗口", now = 2_000)      // 再次搜索，权重更高
        val out = store.suggest("kmp")
        assertEquals("kmp 多窗口", out.first())
        assertTrue(out.none { it.contains("webview") })
    }

    @Test
    fun frequentBeatsOld() {
        val store = SearchSuggestionStore(repo)
        val now = 10_000_000_000L
        // 旧但常用 vs 新但只用一次
        store.record("openai", now = now - 30L * 86_400_000)   // 30 天前
        store.record("openai", now = now - 30L * 86_400_000)
        store.record("openai", now = now - 30L * 86_400_000)
        store.record("ollama", now = now)
        val out = store.suggest("o", now = now)
        assertTrue(out.contains("openai") && out.contains("ollama"))
        assertEquals("openai", out.first())   // 次数权重压过 30 天衰减
    }

    @Test
    fun emptyInputReturnsTop() {
        val store = SearchSuggestionStore(repo)
        store.record("github kotlin", now = 1)
        store.record("github actions", now = 2)
        val out = store.suggest("")
        assertEquals(2, out.size)
    }

    @Test
    fun removeAndClear() {
        val store = SearchSuggestionStore(repo)
        store.record("a")
        store.record("b")
        store.remove("a")
        assertEquals(listOf("b"), store.suggest(""))
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun capAtMaxEntries() {
        val store = SearchSuggestionStore(repo)
        repeat(210) { store.record("q$it", now = it.toLong()) }
        assertTrue(store.load().size <= SearchSuggestionStore.MAX_ENTRIES)
    }
}
