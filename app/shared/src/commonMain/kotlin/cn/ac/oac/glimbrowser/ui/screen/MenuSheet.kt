package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.model.MenuItem
import cn.ac.oac.glimbrowser.model.MenuItemIcon
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 菜单（浮层/面板）—— PRD V1.0 §6.1 全局信息架构 + 原型注释：菜单含书签/历史/下载/插件/设置/无痕等。
 *
 * Phase 2 范围：入口骨架 + 无痕开关 + 桌面模式占位（PRD §3.1.2 桌面模式 UA 切换 P1，Phase 3 接容器）。
 * 书签/历史/下载/插件/设置页面本阶段为占位入口（Phase 2 交付「入口骨架」，页面内容 Phase 6 数据层接入后填充）。
 *
 * @param entries 菜单项（调用方决定顺序与是否显示；便于测试断言）
 * @param incognito 无痕开关当前态
 * @param onItemClick 点击入口（id 路由；Phase 2 各页为占位页面）
 * @param onIncognitoToggle 无痕开关
 */
@Composable
fun MenuSheet(
    entries: List<MenuItem>,
    incognito: Boolean,
    onItemClick: (MenuItem) -> Unit,
    onIncognitoToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 抓手（原型 .drawer .grab）
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(GlimTokens.RadiusPill)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "菜单",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 无痕开关行（PRD §3.1.3 隐私标签：无痕模式 P0）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlimTokens.RadiusCard)
                .clickable { onIncognitoToggle(!incognito) }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuGlyph(MenuItemIcon.INCOGNITO)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "无痕模式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "新标签不入历史、不留缓存",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            // 开关（原型 .gtoggle：开=玉青；关=纸面灰）
            val on = incognito
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 27.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = if (on) 22.dp else 3.dp, top = 3.dp)
                        .clip(GlimTokens.RadiusPill)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        entries.forEach { item ->
            MenuRow(item = item, onClick = { onItemClick(item) })
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "GlimBrowser 1.0 · 轻量浏览 · 本地优先",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** 菜单入口行（图标 + 标题 + 副标题 + 尾部箭头）。 */
@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusCard)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuGlyph(item.icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (item.subtitle.isNotEmpty()) {
                Text(text = item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text(text = "›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}

/** 极简单字形图标（Phase 2 不引图标库；Phase 3 接容器后换矢量资源，字形仅占位辨识）。 */
@Composable
private fun MenuGlyph(icon: MenuItemIcon) {
    val glyph = when (icon) {
        MenuItemIcon.BOOKMARK -> "☆"
        MenuItemIcon.HISTORY -> "◷"
        MenuItemIcon.DOWNLOAD -> "↓"
        MenuItemIcon.PLUGIN -> "⬡"
        MenuItemIcon.SETTINGS -> "⚙"
        MenuItemIcon.NEW_TAB -> "＋"
        MenuItemIcon.INCOGNITO -> "◎"
        MenuItemIcon.DESKTOP_MODE -> "▣"
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(GlimTokens.RadiusCard)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
