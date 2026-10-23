package cn.ac.oac.glimbrowser.ai

import cn.ac.oac.glimbrowser.logic.ApiGuideState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AI 抽屉状态/结果解析（真实链路纯逻辑，无网络）。 */
class AiPanelTest {

    @Test
    fun parseSummaryViewExtractsFields() {
        val json = """
        {"schema_version":"1.0","title":"KMP 跨端实践","reading_time_min":6,
         "key_points":[{"text":"共享逻辑下沉"},{"text":"平台层只做执行"}],
         "conclusion":"适合中小团队","tone":"tutorial"}
        """.trimIndent()
        val v = parseSummaryView(json)
        assertEquals("KMP 跨端实践", v.title)
        assertEquals(listOf("共享逻辑下沉", "平台层只做执行"), v.keyPoints)
        assertEquals("适合中小团队", v.conclusion)
        assertEquals(6, v.readingMinutes)
    }

    @Test
    fun parseSummaryViewToleratesMissingFields() {
        val v = parseSummaryView("""{"title":"只有标题"}""")
        assertEquals("只有标题", v.title)
        assertTrue(v.keyPoints.isEmpty())
        assertEquals("", v.conclusion)
        assertNull(v.readingMinutes)
    }

    @Test
    fun resolveProfilePrefersStoredRow() {
        val stored = cn.ac.oac.glimbrowser.data.Ai_profile_record(
            id = "default", name = "n", base_url = "https://api.x/v1", api_key_ref = "ai:default",
            model = "m", timeout_seconds = 60, stream_enabled = 1, summary_language = "auto",
            last_connectivity_json = null, capabilities_json = null, updated_at = 1,
        )
        val p = resolveAiProfile(ApiGuideState(), stored)
        assertNotNull(p)
        assertEquals("https://api.x/v1", p!!.baseUrl)
        assertEquals("ai:default", p.apiKeyRef)
    }

    @Test
    fun resolveProfileFallsBackToGuideDone() {
        val guide = ApiGuideState(
            step = ApiGuideState.Step.DONE,
            baseUrlInput = "https://api.y/v1",
            apiKeyInput = "sk-x",
            modelInput = "gpt-4o-mini",
        )
        val p = resolveAiProfile(guide, null)
        assertNotNull(p)
        assertEquals("gpt-4o-mini", p!!.model)
        // 红线：返回值只带引用，不含明文 Key
        assertTrue(!p.apiKeyRef.contains("sk-"))
    }

    @Test
    fun resolveProfileNullWhenUnconfigured() {
        assertNull(resolveAiProfile(ApiGuideState(), null))
    }
}
