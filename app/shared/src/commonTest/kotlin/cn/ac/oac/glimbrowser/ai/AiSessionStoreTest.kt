package cn.ac.oac.glimbrowser.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 会话存储（无仓库时应安全降级；有仓库时读写 JSON）。 */
class AiSessionStoreTest {

    @Test
    fun loadWithoutRepositoryReturnsEmpty() {
        assertTrue(AiSessionStore(null).load().isEmpty())
    }

    @Test
    fun appendCreatesSessionAndKeepsOrder() {
        val store = AiSessionStore(null)
        val s1 = store.append(emptyList(), "s1", AiMessage("user", "第一个问题", 1))
        assertEquals(1, s1.size)
        assertEquals("第一个问题", s1.first().title)
        val s2 = store.append(s1, "s1", AiMessage("assistant", "回答", 2))
        assertEquals(2, s2.first().messages.size)
        // 新会话排在前面
        val s3 = store.append(s2, "s2", AiMessage("user", "另一个问题", 3))
        assertEquals("s2", s3.first().id)
        assertEquals(2, s3.size)
    }

    @Test
    fun deleteRemovesSession() {
        val store = AiSessionStore(null)
        val one = store.append(emptyList(), "s1", AiMessage("user", "q", 1))
        val two = store.append(one, "s2", AiMessage("user", "q2", 2))
        val left = store.delete(two, "s1")
        assertEquals(1, left.size)
        assertEquals("s2", left.first().id)
    }
}
