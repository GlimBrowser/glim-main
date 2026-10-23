package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.adfilter.AdFilterEngine
import cn.ac.oac.glimbrowser.adfilter.FilterSettings
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 净化报告浮层（PRD §3.3.3 净化报告：今日拦截/分类 TOP/SITE 开关；原型 §02 rep）。
 * 数据全来自 [AdFilterEngine]（真实统计，非静态；红线6 按端标注"拦截/净化"）。
 */
@Composable
fun PurifyReportSheet(
    adFilter: AdFilterEngine,
    currentHost: String?,
    onClose: () -> Unit,
    onToggleSite: (host: String, enable: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = adFilter.stats
    val host = currentHost?.takeIf { it.isNotBlank() && !it.startsWith("glim://") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(modifier = Modifier.width(38.dp).height(4.dp).clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("净化报告", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            // 红线6：按端如实标注（PurifyStats.platformLabel）
            Text(stats.platformLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 今日 + 本页拦截数（真实计数）
        ReportRow("本页拦截", "${stats.currentPageCount}")
        ReportRow("今日累计", "${stats.todayCount}")
        Spacer(modifier = Modifier.height(6.dp))

        // 分类 TOP（真实 byCategory 占比）
        Text("分类占比", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(4.dp))
        val cats = stats.categoryCounts()
        if (cats.isEmpty()) {
            Text("暂无拦截记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            cats.forEach { (cat, count) -> ReportRow(cat, "$count 次") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // 本站开关（误杀闭环入口：PRD §3.3.3 流程 B "本页暂停拦截"）
        if (host != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本页拦截：$host", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                val enabled = adFilter.settings.effectiveModeFor(host) == FilterSettings.SiteMode.NORMAL
                Box(
                    modifier = Modifier
                        .clip(GlimTokens.RadiusPill)
                        .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggleSite(host, !enabled) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(if (enabled) "开" else "暂停", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("误伤了这个网站？暂停本站拦截，页面会恢复正常显示", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClose).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("关闭", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}
