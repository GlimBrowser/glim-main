package cn.ac.oac.glimbrowser.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 编排器红线单测（消息构造 + 分块；不依赖网络客户端 —— 工具矩阵守卫见 AgentProtocolTest）。
 */
class AgentOrchestratorTest {

    @Test
    fun toolKindsGuard() {
        assertEquals(AgentTool.Kind.DIALOG, AgentTool.PAGE_QA.kind)
        assertEquals(AgentTool.Kind.STRUCTURED, AgentTool.PAGE_SUMMARY.kind)
    }

    @Test
    fun buildDialogMessagesContainsDocumentFenceAndQuestion() {
        val task = AgentTask(tool = AgentTool.PAGE_QA, document = "页面正文片段", question = "结论是什么？")
        val messages = buildDialogMessages(task)
        assertEquals(3, messages.size)
        assertTrue(messages.any { it.content.contains("<document>") })
        assertTrue(messages.any { it.content.contains("结论是什么？") })
        // 对话类 system 不含"只输出 JSON"（附件 C.1.1 对话类语义）
        assertTrue(messages.none { it.role == "system" && it.content.contains("只输出一个合法 JSON 对象") })
    }

    @Test
    fun buildStructuredMessagesUsesSystemFence() {
        val task = AgentTask(
            tool = AgentTool.PAGE_SUMMARY,
            document = "正文",
            schemaHint = AgentSchemas.PAGE_SUMMARY_SCHEMA,
            taskDefinition = "你是网页摘要助手。",
        )
        val messages = buildStructuredMessages(task)
        assertEquals(2, messages.size)
        assertTrue(messages[0].content.contains("只输出一个合法 JSON 对象"))
        assertTrue(messages[0].content.contains("你是网页摘要助手"))
        assertTrue(messages[1].content.contains("<document>"))
        assertTrue(messages[1].content.contains("正文"))
    }

    @Test
    fun chunkDocumentRespectsSizeAndCompleteness() {
        val doc = "行内容\n".repeat(5000)
        val chunks = chunkDocument(doc)
        assertTrue(chunks.size > 1)
        assertEquals(doc, chunks.joinToString(""))  // 无丢字
        assertTrue(chunks.first().endsWith("\n"))   // 换行处断开保留边界
    }
}
