package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.data.GlimRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 历史页：搜索、按天分组、单条删除、清空。数据来自本地仓库；无痕不记录。
 */
@Composable
fun HistoryPage(
    repository: GlimRepository?,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    val rows = remember(query, version) {
        runCatching {
            if (query.isBlank()) repository?.recentHistory(300).orEmpty()
            else repository?.searchHistory(query, 300).orEmpty()
        }.getOrElse { emptyList() }
    }
    val tz = remember { TimeZone.currentSystemDefault() }
    val grouped = rows.groupBy { entry ->
        runCatching {
            val d = Instant.fromEpochMilliseconds(entry.visited_at).toLocalDateTime(tz).date
            "${d.monthNumber} 月 ${d.dayOfMonth} 日"
        }.getOrElse { "更早" }
    }

    PageScaffold(
        title = "历史",
        onBack = onBack,
        action = { PageChip("清空") { runCatching { repository?.clearHistory() }; version++ } },
    ) {
        Spacer(Modifier.height(4.dp))
        PageSearchField(query, "搜索历史", { query = it })
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            EmptyHint("暂无历史记录。无痕模式不会记录历史。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (day, list) ->
                    item(key = "h-$day") {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                            Text("$day · ${list.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(list, key = { "h-${it.id}" }) { entry ->
                        PageRow(
                            title = entry.title.ifBlank { entry.url },
                            subtitle = entry.host.ifBlank { entry.url },
                            onClick = { onOpenUrl(entry.url) },
                            actions = { PageChip("删除") { runCatching { repository?.removeHistory(entry.id) }; version++ } },
                        )
                    }
                }
            }
        }
    }
}
