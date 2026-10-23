package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.logic.TabManager
import cn.ac.oac.glimbrowser.model.BrowserTab
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 标签管理页 —— PRD V1.0 §3.1.3 标签页管理 + 原型 §03 平板中栏 .tabcard（域名 + 标题卡片）。
 *
 * Phase 2 范围：
 *  - 卡片列表（域名小字 + 标题 + 无痕标记），当前标签玉青高亮（原型 .tabcard.on）
 *  - 关闭单个 / 关闭其他（长按或菜单，Phase 2 用显式 ✕ 按钮）
 *  - 顶部「+ 新标签」
 *  - ⏳ 分组/拖拽/回收站（P1，Phase 6）
 */
@Composable
fun TabSwitcherScreen(
    tabs: List<BrowserTab>,
    currentTabId: Long?,
    onTabClick: (Long) -> Unit,
    onTabClose: (Long) -> Unit,
    onCloseOthers: (Long) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    /** 搜索词（过滤标题/域名/分组）。 */
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    /** 分组设置。 */
    onSetGroup: (Long, String?) -> Unit = { _, _ -> },
    /** 回收站。 */
    closedTabs: List<cn.ac.oac.glimbrowser.logic.ClosedTab> = emptyList(),
    onRestoreClosed: (Long) -> Unit = {},
    onClearClosed: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
        // 顶栏：标题 + 计数 + 新标签（原型 col-mid .hd「标签 · 4」）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "标签",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            cn.ac.oac.glimbrowser.ui.design.GlimBadge("${tabs.size}", cn.ac.oac.glimbrowser.ui.design.GlimBadgeTone.NEUTRAL)
            Spacer(modifier = Modifier.width(8.dp))
            // 「+」新标签入口（Compact 全屏标签页顶部）
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onNewTab),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "＋", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 搜索
        cn.ac.oac.glimbrowser.ui.screen.pages.PageSearchField(query, "搜索标签", onQueryChange)
        Spacer(modifier = Modifier.height(8.dp))

        val filtered = if (query.isBlank()) tabs else tabs.filter {
            it.title.contains(query, true) || it.faviconDomain.contains(query, true) ||
                (it.group ?: "").contains(query, true)
        }
        val groups = filtered.groupBy { it.group ?: "未分组" }

        if (filtered.isEmpty()) {
            EmptyTabsHint()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (groupName, list) ->
                    item(key = "gh-$groupName") {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$groupName · ${list.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f),
                            )
                            if (groupName == "未分组") {
                                // 把当前标签归入常用分组（真实写入）
                                list.forEach { t ->
                                    if (t.id == currentTabId) {
                                        Text("工作", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onSetGroup(t.id, "工作") }.padding(horizontal = 5.dp))
                                        Text("阅读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onSetGroup(t.id, "阅读") }.padding(horizontal = 5.dp))
                                    }
                                }
                            } else {
                                Text("移出分组", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.clickable { list.forEach { onSetGroup(it.id, null) } }.padding(horizontal = 5.dp))
                            }
                        }
                    }
                    items(list, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            isCurrent = tab.id == currentTabId,
                            onClick = { onTabClick(tab.id) },
                            onClose = { onTabClose(tab.id) },
                            onCloseOthers = { onCloseOthers(tab.id) },
                        )
                    }
                }
            }
        }

        // 回收站（最近关闭，可恢复）
        Spacer(modifier = Modifier.height(8.dp))
        if (closedTabs.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近关闭 · ${closedTabs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                Text("清空", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onClearClosed).padding(horizontal = 6.dp))
            }
            closedTabs.take(5).forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        c.title.ifBlank { c.url },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("恢复", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onRestoreClosed(c.closedAt) }.padding(horizontal = 6.dp))
                }
            }
        } else {
            Text(
                text = "关闭的标签会保留在这里，可随时恢复",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 标签卡片（原型 .tabcard：域名 + 标题；当前 .on 高亮）。 */
@Composable
private fun TabCard(
    tab: BrowserTab,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
) {
    val bg = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val border = if (isCurrent) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusCard)
            .background(bg)
            .border(1.dp, border, GlimTokens.RadiusCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tab.faviconDomain.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tab.isPrivate) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "无痕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clip(GlimTokens.RadiusPill)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (tab.isLoading) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tab.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 关闭按钮（Phase 2 显式；长按"关闭其他"延后到 Phase 6 手势体系）
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(GlimTokens.RadiusPill)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✕", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelMedium)
        }
        // "关闭其他" 快捷（原型菜单语义，桌面/大屏常用；Phase 2 收进卡片尾部小按钮）
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(GlimTokens.RadiusPill)
                .clickable(onClick = onCloseOthers),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "⌫", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 空态（PRD §3.1.3 全关后 UI 提示开新标签）。 */
@Composable
private fun EmptyTabsHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "没有打开的标签", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点右上角 ＋ 新建标签（或点底部「首页」）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
