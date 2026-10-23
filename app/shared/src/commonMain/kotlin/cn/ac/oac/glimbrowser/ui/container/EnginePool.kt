package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.engine.localBrowserEngineFactory

/**
 * 每标签引擎池（修复「切换标签销毁重建 → 页面状态/缓存丢失、重复加载报错」，108 文档）。
 *
 * 语义：
 *  - 引擎按 tabId 惰性创建并**常驻**（只要标签存在就不销毁）→ 切换标签复用同一内核实例
 *  - 标签关闭（池中 tabId 不再存在于 aliveIds）→ 移除引擎（调用方在 Shell 传入现有标签 id 集）
 *  - 平台 factory 为 null（桌面 jvm 等未接内核）→ 保持 null（容器走诚实占位）
 *
 * 由 [rememberEnginePool] 在 Shell 持有；每次重组不重建（仅依赖 aliveIds 变化做清理）。
 */
class EnginePool {
    private val engines = mutableStateMapOf<Long, BrowserEngine>()

    /** 取（或创建）标签引擎；factory 为 null 时返回 null。 */
    fun engineFor(tabId: Long?, isPrivate: Boolean = false): BrowserEngine? {
        if (tabId == null) return null
        engines[tabId]?.let { return it }
        val factory = factoryProvider() ?: return null
        // 无痕标签：引擎按 ephemeral 存储/禁缓存创建（PRD §3.1.3 不留缓存）
        val created = factory(null, isPrivate)
        engines[tabId] = created
        return created
    }

    /** 移除（标签关闭/应用退出）。 */
    fun remove(tabId: Long) {
        val e = engines.remove(tabId)
        e?.setListener(null)
    }

    /** 清理：仅保留 aliveIds 中的引擎（关闭的标签对应引擎销毁）。 */
    fun prune(aliveIds: Set<Long>) {
        val stale = engines.keys.filterNot { it in aliveIds }
        stale.forEach { remove(it) }
    }

    fun size(): Int = engines.size

    companion object {
        /** 平台工厂注入点（由 App 层在 CompositionLocalProvider 提供；此处代理读取避免类内组合依赖）。 */
        var factoryProvider: () -> ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = { null }
    }
}

/** Shell 层记住池；[aliveIds] 变化时清理已关闭标签的引擎。 */
@Composable
fun rememberEnginePool(aliveIds: Set<Long>): EnginePool {
    val pool = remember { EnginePool() }
    // 平台工厂（CompositionLocal）每次提供值可能相同 → 记到池的静态代理（供 engineFor 使用）
    val factory = localBrowserEngineFactory.current
    EnginePool.factoryProvider = { factory }
    DisposableEffect(aliveIds) {
        pool.prune(aliveIds)
        onDispose { }
    }
    return pool
}
