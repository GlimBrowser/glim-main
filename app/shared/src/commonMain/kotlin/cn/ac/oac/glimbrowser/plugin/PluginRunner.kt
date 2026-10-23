package cn.ac.oac.glimbrowser.plugin

import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.data.Plugin_install
import cn.ac.oac.glimbrowser.engine.BrowserEngine

/**
 * 插件执行器：在页面加载完成时，把「已启用 + 作用域匹配 + 已授权」的用户脚本注入内核。
 *
 * 存储：脚本文本保存在 settings 表 `plugin_script:<id>`（不新增表，避免 schema 迁移）。
 * 安全（如实说明）：注入的是 WebView 内 JS，无法做到进程级隔离；
 * 通过 (1) 只注入白名单桥、(2) 未授权 API 直接拒绝、(3) 不提供文件/任意网络能力 来收敛风险。
 */
object PluginRunner {

    private const val PREFIX = "plugin_script:"

    fun saveScript(repository: GlimRepository?, id: String, source: String, nowMs: Long) {
        runCatching { repository?.putFilterSetting(PREFIX + id, source, nowMs) }
    }

    fun loadScript(repository: GlimRepository?, id: String): String? =
        runCatching { repository?.filterSetting(PREFIX + id) }.getOrNull()

    /** 页面加载完成后调用：执行所有匹配当前 host 的已启用脚本。 */
    fun runForPage(
        repository: GlimRepository?,
        engine: BrowserEngine?,
        host: String,
    ) {
        if (repository == null || engine == null || host.isBlank()) return
        val plugins = runCatching { repository.plugins() }.getOrElse { return }
        plugins.filter { it.enabled != 0L && it.revoked == 0L }.forEach { p ->
            val matches = parseJsonArray(p.matches_json)
            val granted = parseJsonArray(p.granted_json).toSet()
            val needed = parseJsonArray(p.permissions_json).toSet()
            // 授权必须覆盖声明权限（权限收缩过 → 不执行）
            if (!granted.containsAll(needed)) return@forEach
            if (!ScopeMatcher.matchesAny(matches, host)) return@forEach
            val source = loadScript(repository, p.id) ?: return@forEach
            engine.evaluatePluginScript(p.id, source, granted)
        }
    }

    /** 导入时保存脚本正文 + 记录（授权即安装时勾选的集合）。 */
    fun install(
        repository: GlimRepository?,
        plugin: Plugin_install,
        source: String,
        nowMs: Long,
    ) {
        saveScript(repository, plugin.id, source, nowMs)
        runCatching { repository?.upsertPlugin(plugin) }
    }

    private fun parseJsonArray(json: String): List<String> =
        json.removePrefix("[").removeSuffix("]").split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
}
