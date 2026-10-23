package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.adfilter.AdFilterEngine
import cn.ac.oac.glimbrowser.adfilter.FilterSettings
import cn.ac.oac.glimbrowser.logic.SearchEngine
import cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode

/**
 * 设置页（真实读写：搜索引擎、去广告层级与白名单、外观、隐私清数据、AI 配置状态）。
 */
@Composable
fun SettingsPage(
    searchEngine: SearchEngine,
    onSetSearchEngine: (SearchEngine) -> Unit,
    adFilter: AdFilterEngine,
    themeMode: GlimThemeMode,
    onSetThemeMode: (GlimThemeMode) -> Unit,
    desktopMode: Boolean,
    onSetDesktopMode: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    forceDarkWeb: Boolean,
    onSetForceDarkWeb: (Boolean) -> Unit,
    aiConfigured: Boolean,
    docsEndpoint: String,
    enabledHomeCards: Set<String>,
    onToggleHomeCard: (String, Boolean) -> Unit,
    historyCount: Int,
    bookmarkCount: Int,
    onClearBrowsingData: () -> Unit,
    onOpenAiSetup: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = adFilter.settings
    PageScaffold(title = "设置", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            SectionTitle("通用")
            ToggleLine(
                label = "显示“今日拦截”卡片",
                subtitle = "首页显示净化统计",
                on = "privacy" in enabledHomeCards,
                onChange = { onToggleHomeCard("privacy", it) },
            )
            ToggleLine(
                label = "显示“AI 助手”卡片",
                subtitle = "首页显示 AI 快捷入口",
                on = "ai" in enabledHomeCards,
                onChange = { onToggleHomeCard("ai", it) },
            )
            ToggleLine(
                label = "桌面版网站",
                subtitle = "用电脑版页面加载（部分站点更完整）",
                on = desktopMode,
                onChange = onSetDesktopMode,
            )
            ToggleLine(
                label = "强制深色网页（实验）",
                subtitle = "页面本身没有深色样式时反转配色；部分站点可能显示异常",
                on = forceDarkWeb,
                onChange = onSetForceDarkWeb,
            )
            PageRow(
                title = "清除缓存与 Cookie",
                subtitle = "不会删除历史与书签",
                actions = { PageChip("清除") { onClearCache() } },
            )
            PageRow(
                title = "清除浏览数据",
                subtitle = "历史 $historyCount 条 · 书签 $bookmarkCount 个（书签需在书签页单独删除）",
                actions = { PageChip("清空历史") { onClearBrowsingData() } },
            )

            SectionTitle("搜索")
            SearchEngine.entries.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetSearchEngine(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (e == searchEngine) Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionTitle("去广告")
            ToggleLine(
                label = "总开关",
                subtitle = "关闭后所有站点都不再拦截",
                on = settings.globalEnabled,
                onChange = { on -> adFilter.updateSettings { it.withGlobalEnabled(on) } },
            )
            FilterSettings.Layer.entries.forEach { layer ->
                ToggleLine(
                    label = layer.title,
                    subtitle = layerSubtitle(layer),
                    on = settings.layerEnabled[layer] == true,
                    onChange = { on -> adFilter.updateSettings { it.withLayer(layer, on) } },
                )
            }
            val paused = settings.siteModes.filterValues { it == FilterSettings.SiteMode.DISABLED }.keys
            if (paused.isNotEmpty()) {
                Text("已暂停的站点", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                paused.forEach { host ->
                    PageRow(
                        title = host,
                        subtitle = "本页不拦截",
                        actions = { PageChip("恢复") { adFilter.updateSettings { it.resumeSite(host) } } },
                    )
                }
            }
            Text(
                "今日已拦截 ${adFilter.stats.todayCount} 项 · ${adFilter.stats.platformLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle("外观")
            GlimThemeMode.entries.forEach { m ->
                val label = when (m) {
                    GlimThemeMode.LIGHT -> "浅色"
                    GlimThemeMode.DARK -> "深色"
                    GlimThemeMode.SYSTEM -> "跟随系统"
                    GlimThemeMode.EYE_CARE -> "护眼（纸感低蓝光）"
                }
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetThemeMode(m) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (m == themeMode) Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionTitle("AI")
            PageRow(
                title = if (aiConfigured) "已配置" else "未配置",
                subtitle = if (aiConfigured) "${docsEndpoint}（密钥在本机安全区）" else "配置后可使用摘要与页面问答",
                onClick = onOpenAiSettings,
                actions = {
                    PageChip("AI 设置") { onOpenAiSettings() }
                    Spacer(androidx.compose.ui.Modifier.width(6.dp))
                    PageChip(if (aiConfigured) "重新配置" else "去配置", primary = !aiConfigured) { onOpenAiSetup() }
                },
            )

            SectionTitle("其他")
            PageRow(title = "关于 GlimBrowser", subtitle = "版本与隐私说明", onClick = onOpenAbout)

            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun layerSubtitle(layer: FilterSettings.Layer): String = when (layer) {
    FilterSettings.Layer.L1 -> "域名与规则匹配的基础拦截"
    FilterSettings.Layer.L2 -> "第三方追踪器与统计脚本"
    FilterSettings.Layer.L3 -> "页面广告位隐藏（化妆过滤）"
    FilterSettings.Layer.L4 -> "实验性增强规则（可能误伤，默认关闭）"
}

private fun Modifier.background2(color: androidx.compose.ui.graphics.Color) = this.then(background(color))
