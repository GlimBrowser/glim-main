package cn.ac.oac.glimbrowser.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * json_object 降级链单测（附件 C.1.5 四级链 + 埋点红线：仅 stage+toolId+errorType）。
 */
class JsonFallbackChainTest {

    private val chain = JsonFallbackChain()

    @Test
    fun directParseSuccess_noDegradation() {
        val r = chain.parseStructured("""{"schema_version":"1.0","title":"t"}""", toolId = "page_summary")
        assertTrue(r is JsonFallbackChain.ParseOutcome.Success)
        r as JsonFallbackChain.ParseOutcome.Success
        assertFalse(r.degraded)
        assertTrue(r.json.contains("title"))
    }

    @Test
    fun fenceStrippedSuccess() {
        val raw = "```json\n{\"schema_version\":\"1.0\",\"title\":\"fenced\"}\n```"
        val events = ArrayList<JsonFallbackChain.FallbackEvent>()
        val r = chain.parseStructured(raw, toolId = "page_summary", onEvent = { events.add(it) })
        assertTrue(r is JsonFallbackChain.ParseOutcome.Success)
        assertFalse((r as JsonFallbackChain.ParseOutcome.Success).degraded)
        // 围栏剥离是"第二级"但不算 degraded（仍是干净 JSON 解析，C.1.5 语义：不降级展示）
        assertTrue(events.any { it.stage == JsonFallbackChain.FallbackEvent.Stage.FULL_PARSE_FAILED })
    }

    @Test
    fun balancedExtractionRecoversPrefixedNoise() {
        // 模型输出前缀废话 + JSON（附件 C.1.5 第 3 级）
        val raw = "好的，以下是摘要：{\"schema_version\":\"1.0\",\"title\":\"x\"} 希望有帮助"
        val events = ArrayList<JsonFallbackChain.FallbackEvent>()
        val r = chain.parseStructured(raw, toolId = "page_summary", onEvent = { events.add(it) })
        assertTrue(r is JsonFallbackChain.ParseOutcome.Success)
        assertTrue((r as JsonFallbackChain.ParseOutcome.Success).degraded)  // 经平衡提取 → degraded=true
        assertTrue(events.any { it.stage == JsonFallbackChain.FallbackEvent.Stage.BALANCED_EXTRACT_OK })
    }

    @Test
    fun allFailed_degradesToPlainText_noContentInEvent() {
        val raw = "这不是 JSON，纯自然语言回答。"
        val events = ArrayList<JsonFallbackChain.FallbackEvent>()
        val r = chain.parseStructured(raw, toolId = "page_summary", onEvent = { events.add(it) })
        assertTrue(r is JsonFallbackChain.ParseOutcome.Failed)
        assertEquals(raw, (r as JsonFallbackChain.ParseOutcome.Failed).rawText)
        val last = events.last()
        assertEquals(JsonFallbackChain.FallbackEvent.Stage.ALL_FAILED, last.stage)
        // 埋点红线（附件 C.1.5 / PRD §7）：事件不含内容（只有 stage/toolId/errorType 字段）
        assertEquals("page_summary", last.toolId)
        assertFalse(last.errorType.contains("这不是"))   // 错误类型是枚举式，不含正文
    }

    @Test
    fun requiredKeysTriggerFallback() {
        // 缺 required 键 → 触发降级链（附件 C.2.4：字段不符丢弃/降级）
        val raw = """{"title":"only-title"}"""
        val r = chain.parseStructured(raw, toolId = "t", requiredKeys = setOf("schema_version", "title"))
        // 直接解析失败(缺键) → 围栏/平衡提取也救不回 → Failed(纯文本降级)
        assertTrue(r is JsonFallbackChain.ParseOutcome.Failed)
    }

    @Test
    fun bracesInsideStringDoNotBreakExtraction() {
        // 平衡提取须跳过字符串内括号（附件 C.1.5 注入防护边界）
        val raw = """noise {"a":"}{not-json","b":1} tail"""
        val extracted = chain.extractFirstBalancedJsonObject(raw)
        assertNotNull(extracted)
        assertEquals("""{"a":"}{not-json","b":1}""", extracted)
    }

    @Test
    fun stripFencesVariants() {
        assertEquals("{\"a\":1}", chain.stripFences("```json\n{\"a\":1}\n```"))
        assertEquals("{\"a\":1}", chain.stripFences("```\n{\"a\":1}\n```"))
        assertEquals("{\"a\":1}", chain.stripFences("{\"a\":1}"))
        assertEquals("", chain.stripFences("```json\n```"))
    }

    @Test
    fun noJsonAtAllReturnsNullExtraction() {
        assertNull(chain.extractFirstBalancedJsonObject("no braces here"))
        assertNull(chain.extractFirstBalancedJsonObject("{unclosed"))
    }
}
