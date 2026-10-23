package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.model.BrowserTab
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 浮动标签条（Safari 风格）：毛玻璃底 + 激活标签底部亮线 + 悬停关闭。
 * 桌面/Pad 全量显示；手机只显示当前标签（节省高度，由 BottomNav 承担切换）。
 */
@Composable
fun FloatingTabStrip(
    tabs: List<BrowserTab>,
    currentTabId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val shown = if (compact) tabs.filter { it.id == currentTabId } else tabs
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(GlimTokens.RadiusSm)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        shown.forEach { tab ->
            val active = tab.id == currentTabId
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(GlimTokens.Radius12)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(tab.id) }
                    .padding(start = 9.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (tab.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.title.ifBlank { tab.faviconDomain }.ifBlank { "新标签" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(if (compact) 150.dp else 132.dp),
                )
                if (!compact || active) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.size(20.dp).clip(GlimTokens.RadiusPill)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .clickable { onClose(tab.id) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                }
            }
        }
        Box(
            modifier = Modifier.size(40.dp).clip(GlimTokens.Radius12)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onNewTab),
            contentAlignment = Alignment.Center,
        ) { Text("＋", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
    }
}
