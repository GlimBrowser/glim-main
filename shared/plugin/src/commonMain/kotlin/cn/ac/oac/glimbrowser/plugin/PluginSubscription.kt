package cn.ac.oac.glimbrowser.plugin

import kotlinx.serialization.Serializable

/**
 * 插件订阅清单（PRD V1.1 §3.4 插件中心"订阅更新"；CDN 分发由 server 模块占位，PRD V1.1 §2.3 规则云 CDN）。
 *
 * 语义：
 *  - 清单列出可用脚本 + 最新版本 + 指纹（用于本地比对升级）
 *  - 更新流程：拉清单 → 版本比较 → 用户确认后安装（不静默改权限，PRD §3.4）
 *  - 吊销通道：清单可携带 revokedFingerprints（CDN 下发黑名单）
 */
@Serializable
data class PluginCatalog(
    val catalogVersion: Int = 1,
    /** 可用脚本条目。 */
    val entries: List<CatalogEntry> = emptyList(),
    /** 远程吊销指纹黑名单（PRD §3.4）。 */
    val revokedFingerprints: List<String> = emptyList(),
) {
    @Serializable
    data class CatalogEntry(
        val id: String,
        val name: String,
        val version: String,
        val description: String = "",
        val scriptUrl: String,
        val fingerprint: String = "",
        val matches: List<String> = emptyList(),
        /** 清单声明的所需权限（安装页展示对比，更新时权限变化需用户重新确认，PRD §3.4）。 */
        val permissions: List<Permission> = emptyList(),
    )

    /** 本地是否有更新（版本字符串不同即视为有更新，语义化版本比较 Phase 后续增强）。 */
    fun hasUpdate(installed: UserscriptMeta): Boolean =
        entries.firstOrNull { it.id == installed.id }?.version?.let { it != installed.version } == true

    /** 权限是否在更新中发生变化（变化 → 必须重新逐项授权，PRD §3.4 红线）。 */
    fun permissionsChanged(installed: UserscriptMeta): Boolean =
        entries.firstOrNull { it.id == installed.id }
            ?.permissions?.toSet()
            ?.let { it != installed.permissions } == true
}
