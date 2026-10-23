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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.model.DefaultQuickSites
import cn.ac.oac.glimbrowser.model.HomeCard
import cn.ac.oac.glimbrowser.model.QuickSite
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens
import cn.ac.oac.glimbrowser.ui.theme.JadeLight

/**
 * 首页（新标签页）—— PRD V1.0 §3.5.3 首页信息架构 + 原型 §02 手机原型 screen "home"。
 *
 * 结构（原型自上而下）：时间日期（轻量）→ 搜索/地址输入框 → 快捷站点 8 宫格 → 可选卡片（隐私/AI）。
 * 红线：默认零商业推荐（PRD §3.5.3）；卡片可关（PRD §3.5.3「可选」+ 注释 01）。
 *
 * @param now 时间展示（注入以便单测/预览固定；Phase 2 用简单字符串，国际化/时区后续接）
 * @param enabledCardIds 已开启的首页卡片 id 集合（来自 [cn.ac.oac.glimbrowser.logic.BrowserState]）
 * @param onSearchBarClick 点搜索框 → 聚焦地址栏（切到浏览壳编辑态）
 * @param onQuickSiteClick 快捷站点点击 → 导航
 */
@Composable
fun HomeScreen(
    now: HomeClock,
    enabledCardIds: Set<String>,
    onSearchBarClick: () -> Unit,
    onQuickSiteClick: (QuickSite) -> Unit,
    onAddSiteClick: () -> Unit = {},
    /** 快捷站点（来自书签真实数据；空则回退内置默认，108 去静态）。 */
    quickSites: List<QuickSite> = DefaultQuickSites.all,
    onMoreSitesClick: () -> Unit = {},
    /** 今日已拦截数量（真实统计，0 时如实显示"暂无"）。 */
    todayBlocked: Int = 0,
    /** 各类别拦截次数（驱动迷你柱状图，真实数据；空则显示占位柱）。 */
    blockedSeries: List<Int> = emptyList(),
    /** AI 是否已配置（驱动入口卡片文案）。 */
    aiConfigured: Boolean = false,
    onAiCardClick: () -> Unit = {},
    /** 搜索胶囊右侧的 ✦ 圆钮（原型 .s-ai）：直接进入 AI。 */
    onAiClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 时间日期（原型 .home-top：大字时间 + 小字日期，轻量）
        Column(
            modifier = Modifier.padding(top = 34.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = now.time,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    fontSize = 68.sp,
                    lineHeight = 68.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = now.date,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // 搜索/地址输入框（原型 .searchbar 胶囊）
        Row(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                .fillMaxWidth()
                .clip(GlimTokens.RadiusPill)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), GlimTokens.RadiusPill)
                .clickable(onClick = onSearchBarClick)
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchGlyph(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "搜索或输入网址",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 原型 .s-ai：28dp 玉青圆钮
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAiClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        // 快捷站点 8 宫格（PRD §3.5.3；Phase 2 默认 6 + 添加 + 更多占位 = 8 格）
        val sites = quickSites.ifEmpty { DefaultQuickSites.all }
        Column(modifier = Modifier.padding(top = 22.dp)) {
            // 两行四列（原型 grid-template-columns:repeat(4,1fr)）——用固定两行 Row 实现更贴近原型
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sites.take(4).forEach { site ->
                    QuickSiteCell(site, Modifier.weight(1f), onClick = { onQuickSiteClick(site) })
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sites.drop(4).take(4).forEach { site ->
                    QuickSiteCell(site, Modifier.weight(1f), onClick = { onQuickSiteClick(site) })
                }
                // 第 7 格：+ 添加
                QuickSiteCell(
                    site = QuickSite(
                        id = DefaultQuickSites.ADD_PLACEHOLDER_ID,
                        label = "添加",
                        url = "",
                        letter = "+",
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onAddSiteClick,
                )
                // 第 8 格：更多
                QuickSiteCell(
                    site = QuickSite(
                        id = DefaultQuickSites.MORE_PLACEHOLDER_ID,
                        label = "更多",
                        url = "",
                        letter = "·",
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onMoreSitesClick,
                )
            }
        }

        // 可选卡片（隐私统计 / AI 快速入口）——原型 .cards，仅展示已开启者
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (HomeCard.PRIVACY.id in enabledCardIds) {
                PrivacyMiniCard(todayBlocked = todayBlocked, series = blockedSeries)
            }
            if (HomeCard.AI.id in enabledCardIds) {
                AiMiniCard(configured = aiConfigured, onClick = onAiCardClick)
            }
        }
    }
}

/** 简单时钟展示模型（注入以便测试/预览；Phase 3+ 接平台时钟）。 */
data class HomeClock(val time: String, val date: String)

/** 快捷站点单元格（原型 .qsite：字母占位图 + 名称）。 */
@Composable
private fun QuickSiteCell(site: QuickSite, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        cn.ac.oac.glimbrowser.ui.design.FavTile(
            letter = site.letter,
            size = 56,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = site.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 隐私统计卡片（今日拦截数量与分类分布，均来自真实统计）。 */
@Composable
private fun PrivacyMiniCard(todayBlocked: Int, series: List<Int>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusCard)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), GlimTokens.RadiusCard)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "今日已拦截",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = if (todayBlocked > 0) "$todayBlocked" else "0",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    fontSize = 32.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (todayBlocked > 0) "项" else "暂无拦截",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        // 迷你柱状图（原型 .bars：奇偶交替填充）
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 真实数据：各分类拦截次数归一化为柱高；无数据时统一低柱
            val max = (series.maxOrNull() ?: 0).coerceAtLeast(1)
            val heights = if (series.isEmpty()) List(12) { 0.12f }
                          else series.take(12).map { (it.toFloat() / max).coerceIn(0.12f, 1f) }
            heights.forEachIndexed { i, h ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((30 * h).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i % 2 == 0) JadeLight else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                )
            }
        }
    }
}

/** AI 快速入口卡片（状态真实：已配置/未配置）。 */
@Composable
private fun AiMiniCard(configured: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusMd)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), GlimTokens.RadiusMd)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "✦ AI 助手",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (configured) "点 ✦ 摘要当前页，或与页面问答" else "点击 ✦ 完成配置后即可摘要与问答",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 3.dp),
                textAlign = TextAlign.Start,
            )
        }
        // ✦ 主按钮（原型 .ai-btn：42dp 玉青圆钮）
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(GlimTokens.RadiusPill)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✦", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 搜索图标（极简放大镜，纯 Compose 绘制，Phase 2 先不引入矢量图标库）。 */
@Composable
private fun SearchGlyph(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .align(Alignment.TopStart)
                .border(1.6.dp, MaterialTheme.colorScheme.primary, GlimTokens.RadiusPill),
        )
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 1.6.dp)
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
        )
    }
}
