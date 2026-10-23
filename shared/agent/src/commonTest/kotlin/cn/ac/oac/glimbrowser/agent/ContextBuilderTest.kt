package cn.ac.oac.glimbrowser.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 上下文构建单测（PRD §3.2.2 + 附件 C.2.1 分块规则）。
 */
class ContextBuilderTest {

    @Test
    fun cleanRemovesControlCharsKeepsNewlineTab() {
        val dirty = "第一行\u0000\n\t第二行\u0007"
        val cleaned = ContextBuilder.clean(dirty)
        assertFalse(cleaned.contains("\u0000"))
        assertFalse(cleaned.contains("\u0007"))
        assertTrue(cleaned.contains("第一行"))
        assertTrue(cleaned.contains("第二行"))
        assertTrue(cleaned.contains('\t'))
    }

    @Test
    fun cleanCollapsesBlankLinesAndTruncatesLongLines() {
        val text = "a\n\n\n\nb\n" + "x".repeat(5000)
        val cleaned = ContextBuilder.clean(text)
        assertFalse(cleaned.contains("\n\n\n"))   // 连续空行折叠
        assertTrue(cleaned.lines().none { it.length > 2000 })  // 超长行截断
    }

    @Test
    fun estimateTokensConservative() {
        assertEquals(0, ContextBuilder.estimateTokens(""))
        assertEquals(1, ContextBuilder.estimateTokens("ab"))
        assertEquals(2, ContextBuilder.estimateTokens("abcd"))
        assertEquals(500, ContextBuilder.estimateTokens("x".repeat(1000)))
    }

    @Test
    fun chunkByTokensSplitsAtNewlines() {
        val text = (1..20).joinToString("\n") { "line-$it-" + "y".repeat(500) }
        val chunks = ContextBuilder.chunkByTokens(text, maxTokens = 1000)  // 2000 chars/块
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 2000 })
        // 完整性：拼接等于原文（无丢字）
        assertEquals(text, chunks.joinToString(""))
        // 每块尽量以换行结尾（段落边界）
        assertTrue(chunks.dropLast(1).all { it.endsWith("\n") })
    }

    @Test
    fun shortTextSingleChunk() {
        val chunks = ContextBuilder.chunkByTokens("short text")
        assertEquals(1, chunks.size)
    }

    @Test
    fun pipelinePlanFlagsTwoStage() {
        val short = ContextBuilder.planSummaryPipeline("short")
        assertFalse(short.requiresTwoStage)
        val long = ContextBuilder.planSummaryPipeline((1..200).joinToString("\n") { "段落$it" + "字".repeat(200) })
        assertTrue(long.requiresTwoStage)
        assertTrue(long.estimatedTotalTokens > 0)
    }
}
