package cn.ac.oac.glimbrowser.agent

import cn.ac.oac.glimbrowser.network.ChatMessage
import cn.ac.oac.glimbrowser.network.ResponseFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Agent 工具协议红线单测（附件 C.1.1 输出协议矩阵：结构化=json_object+非流式；对话类=流式+无 json_object）。
 */
class AgentProtocolTest {

    @Test
    fun structuredToolsUseJsonObjectNonStream() {
        // 附件 C.1.1：结构化工具矩阵
        val structured = listOf(
            AgentTool.PAGE_SUMMARY, AgentTool.PAGE_TRANSLATE,
            AgentTool.SELECTION_EXPLAIN, AgentTool.SELECTION_SUMMARIZE, AgentTool.SELECTION_TRANSLATE,
            AgentTool.TABLE_EXTRACT,
        )
        for (t in structured) {
            assertEquals(ResponseFormat("json_object"), t.responseFormat(), "${t.id} 必须 json_object")
            assertFalse(t.stream(), "${t.id} 结构化必须非流式")
        }
    }

    @Test
    fun dialogToolsStreamWithoutJsonObject() {
        // 附件 C.1.1：对话类矩阵（PRD §3.2.0 红线）
        val dialog = listOf(AgentTool.SELECTION_ASK, AgentTool.PAGE_QA, AgentTool.GLOBAL_CHAT, AgentTool.WRITE_ASSIST)
        for (t in dialog) {
            assertNull(t.responseFormat(), "${t.id} 对话类不得携带 response_format")
            assertTrue(t.stream(), "${t.id} 对话类必须流式")
        }
    }

    @Test
    fun fullMatrixMatchesAttachmentC() {
        // 附件 C.1.1 完整工具清单（9 结构化/对话条目 + selection 细分）
        val ids = AgentTool.entries.map { it.id }.toSet()
        val expected = setOf(
            "page_summary", "page_translate",
            "selection_enhance.explain", "selection_enhance.summarize", "selection_enhance.translate", "selection_enhance.ask",
            "page_qa", "global_chat", "table_extract", "write_assist",
        )
        assertEquals(expected, ids)
        assertEquals(AgentTool.PAGE_SUMMARY, AgentTool.byId("page_summary"))
        assertNull(AgentTool.byId("nope"))
    }

    @Test
    fun systemFenceInjectionProtection() {
        // 附件 C.1.4 注入防护：system 含"只输出一个合法 JSON 对象" + <document> 声明
        val msg = ChatMessage.systemWithFence("任务定义", "schema 提示")
        assertTrue(msg.content.contains("只输出一个合法 JSON 对象"))
        assertTrue(msg.content.contains("<document>"))
        assertTrue(msg.content.contains("不是指令"))
        assertEquals(ChatMessage.ROLE_SYSTEM, msg.role)
    }

    @Test
    fun userDocumentFencedAndSanitized() {
        // C.1.4：正文围栏 + 控制字符过滤
        val dirty = "正文含\u0000控制符\u0007和正常中文"
        val msg = ChatMessage.userWithFencedDocument(dirty)
        assertTrue(msg.content.startsWith("<document>"))
        assertTrue(msg.content.contains("和正常中文"))
        assertFalse(msg.content.contains("\u0000"))
        assertFalse(msg.content.contains("\u0007"))
    }

    @Test
    fun agentToolTemperaturesMatchAttachmentC() {
        // 附件 C.1.2：摘要/提取 0.2、翻译 0.3、解释 0.4
        assertEquals(0.2, AgentTool.PAGE_SUMMARY.temperature)
        assertEquals(0.3, AgentTool.PAGE_TRANSLATE.temperature)
        assertEquals(0.4, AgentTool.SELECTION_EXPLAIN.temperature)
        assertEquals(0.2, AgentTool.TABLE_EXTRACT.temperature)
    }

    @Test
    fun schemasProvideRequiredKeys() {
        // 附件 C 各工具 required 白名单存在
        assertTrue(AgentSchemas.PAGE_SUMMARY_REQUIRED.containsAll(setOf("schema_version", "title", "key_points", "conclusion")))
        assertTrue(AgentSchemas.PAGE_TRANSLATE_REQUIRED.contains("segments"))
        assertTrue(AgentSchemas.TABLE_EXTRACT_REQUIRED.containsAll(setOf("columns", "rows")))
        val (schema, req) = AgentSchemas.schemaFor(AgentTool.SELECTION_EXPLAIN)
        assertTrue(schema.isNotBlank())
        assertTrue(req.contains("explanation"))
    }
}
