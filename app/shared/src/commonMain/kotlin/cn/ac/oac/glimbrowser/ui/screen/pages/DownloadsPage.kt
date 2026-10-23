package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.logic.DownloadItem
import cn.ac.oac.glimbrowser.logic.DownloadState

/**
 * 下载页：本次会话的下载任务（由平台下载事件真实驱动）。
 * 文件由系统下载器落盘（Android 通知栏/文件管理可见）；此处提供打开来源与清理。
 */
@Composable
fun DownloadsPage(
    downloads: List<DownloadItem>,
    onOpenUrl: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearFinished: () -> Unit,
    onBack: () -> Unit,
) {
    PageScaffold(
        title = "下载",
        onBack = onBack,
        action = { PageChip("清除已完成", onClick = onClearFinished) },
    ) {
        Spacer(Modifier.height(6.dp))
        if (downloads.isEmpty()) {
            EmptyHint("暂无下载。网页里点击下载链接后，任务会出现在这里（文件保存在系统下载目录）。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(downloads, key = { it.id }) { d ->
                    val stateText = when (d.state) {
                        DownloadState.RUNNING -> "下载中"
                        DownloadState.DONE -> "已完成"
                        DownloadState.FAILED -> "失败"
                    }
                    PageRow(
                        title = d.fileName,
                        subtitle = "$stateText · ${d.url}",
                        onClick = { onOpenUrl(d.url) },
                        actions = { PageChip("移除") { onRemove(d.id) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "下载由系统下载器接管，可在系统的「文件」或「下载」中查看。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
