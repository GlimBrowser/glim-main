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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.logic.ClosedTab
import cn.ac.oac.glimbrowser.model.DefaultQuickSites
import cn.ac.oac.glimbrowser.model.HomeCard
import cn.ac.oac.glimbrowser.model.QuickSite
import cn.ac.oac.glimbrowser.ui.design.FavTile
import cn.ac.oac.glimbrowser.ui.design.GlimIcon
import cn.ac.oac.glimbrowser.ui.design.GlimIconName
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/** 简单时钟展示模型（注入以便测试/预览）。 */
data class HomeClock(val time: String, val date: String)

/**
 * 起始页（Safari 风格）：搜索框 → 收藏 → 隐私报告 → 最近关闭。
 * 所有数字与列表来自真实数据（书签 / 拦截统计 / 回收站），没有静态内容。
 */
@Composable
fun HomeScreen(
    now: HomeClock,
    enabledCardIds: Set<String>,
    onSearchBarClick: () -> Unit,
    onQuickSiteClick: (QuickSite) -> Unit,
    onAddSiteClick: () -> Unit = {},
    quickSites: List<QuickSite> = DefaultQuickSites.all,
    onMoreSitesClick: () -> Unit = {},
    todayBlocked: Int = 0,
    blockedSeries: List<Int> = emptyList(),
    aiConfigured: Boolean = false,
    onAiCardClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    /** 最近关闭（回收站），点击恢复。 */
    closedTabs: List<ClosedTab> = emptyList(),
    onRestoreClosed: (Long) -> Unit = {},
    onViewAllClosed: () -> Unit = {},
    /** 隐私报告入口。 */
    onOpenPrivacyReport: () -> Unit = {},
    /** 「编辑」收藏（书签页）。 */
    onEditFavorites: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 背景氛围（Safari 起始页在壁纸上的毛玻璃观感；这里用柔和渐变替代壁纸）
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部搜索胶囊（玻璃）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), GlimTokens.RadiusPill)
                    .clickable(onClick = onSearchBarClick)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlimIcon(GlimIconName.SEARCH, size = 18.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(10.dp))
                Text(
                    "搜索或输入网址",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(30.dp).clip(GlimTokens.RadiusPill)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .clickable(onClick = onAiClick),
                    contentAlignment = Alignment.Center,
                ) {
                    GlimIcon(GlimIconName.SPARKLE, size = 16.dp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(22.dp))

            // 收藏
            SectionHeader("收藏", action = "编辑", onAction = onEditFavorites)
            Spacer(Modifier.height(10.dp))
            val sites = quickSites.ifEmpty { DefaultQuickSites.all }.take(4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sites.forEach { site ->
                    Column(
                        modifier = Modifier.weight(1f).clickable { onQuickSiteClick(site) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FavTile(letter = site.letter, size = 54, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            site.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (sites.size < 4) {
                    repeat(4 - sites.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 隐私报告
            SectionHeader("隐私报告")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlimTokens.RadiusCard)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), GlimTokens.RadiusCard)
                    .clickable(onClick = onOpenPrivacyReport)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlimIcon(GlimIconName.SHIELD, size = 22.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$todayBlocked",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (todayBlocked > 0) "今日拦截的追踪器与广告" else "今日暂无拦截记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                GlimIcon(GlimIconName.CHEVRON_RIGHT, size = 16.dp, color = MaterialTheme.colorScheme.outline)
            }

            if (aiConfigured && enabledCardIds.contains(HomeCard.AI.id)) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlimTokens.RadiusCard)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(onClick = onAiCardClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlimIcon(GlimIconName.SPARKLE, size = 18.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("用 AI 摘要当前页面", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    GlimIcon(GlimIconName.CHEVRON_RIGHT, size = 16.dp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(22.dp))

            // 最近关闭
            SectionHeader("最近关闭", action = if (closedTabs.size > 3) "查看全部" else null, onAction = onViewAllClosed)
            Spacer(Modifier.height(10.dp))
            if (closedTabs.isEmpty()) {
                Text(
                    "关闭的标签会在这里保留，可随时恢复。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    closedTabs.take(4).forEach { closed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GlimTokens.Radius12)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), GlimTokens.Radius12)
                                .clickable { onRestoreClosed(closed.closedAt) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlimIcon(GlimIconName.HISTORY, size = 15.dp, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    closed.title.ifBlank { closed.url },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (closed.title.isNotBlank() && closed.url.isNotBlank()) {
                                    Text(
                                        closed.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            GlimIcon(GlimIconName.ARROW_RIGHT, size = 14.dp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable(onClick = onEditFavorites)
                    .padding(horizontal = 22.dp, vertical = 9.dp),
            ) {
                Text("编辑", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "${now.time} · ${now.date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}
