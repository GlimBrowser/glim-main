package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import cn.ac.oac.glimbrowser.data.Bookmark
import cn.ac.oac.glimbrowser.data.GlimRepository

/**
 * 书签页：搜索、按文件夹分组、添加当前页、编辑（标题/文件夹）、删除、打开。
 * 数据全部来自本地仓库（真实，无静态）。
 */
@Composable
fun BookmarksPage(
    repository: GlimRepository?,
    currentUrl: String?,
    currentTitle: String?,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    nowMs: () -> Long,
    /** 打开文件夹管理页。 */
    onOpenFolders: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var showFolders by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val all = remember(version) { runCatching { repository?.listBookmarks().orEmpty() }.getOrElse { emptyList() } }
    val filtered = if (query.isBlank()) all else all.filter {
        it.title.contains(query, true) || it.url.contains(query, true) || it.folder.contains(query, true)
    }
    val grouped = filtered.groupBy { it.folder.ifBlank { "未分类" } }

    PageScaffold(
        title = "书签",
        onBack = onBack,
        action = {
            androidx.compose.foundation.layout.Row {
                PageChip("文件夹") { onOpenFolders() }
                Spacer(androidx.compose.ui.Modifier.width(6.dp))
                PageChip("导入") { showImport = true }
                Spacer(androidx.compose.ui.Modifier.width(6.dp))
                PageChip("导出") {
                    val list = runCatching { repository?.listBookmarks().orEmpty() }.getOrElse { emptyList() }
                    if (list.isEmpty()) {
                        status = "没有可导出的书签"
                    } else {
                        val html = cn.ac.oac.glimbrowser.bookmark.BookmarkHtml.export(list)
                        val saved = cn.ac.oac.glimbrowser.platform.saveTextFile("glimbookmarks.html", html)
                        status = if (saved != null) "已导出到：$saved" else "导出失败（存储不可写）"
                    }
                }
                val url = currentUrl
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    Spacer(androidx.compose.ui.Modifier.width(6.dp))
                    PageChip("收藏本页", primary = true) {
                        runCatching { repository?.addBookmark(url, currentTitle ?: url, "", nowMs()) }
                        version++
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.height(4.dp))
        PageSearchField(query, "搜索书签", { query = it })
        status?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (filtered.isEmpty()) {
            EmptyHint(if (query.isBlank()) "还没有书签。打开一个网页后点右上角的「收藏本页」。" else "没有匹配的书签。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (folder, list) ->
                    item(key = "h-$folder") {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                            Text("$folder · ${list.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(list, key = { "bm-${it.id}" }) { bm ->
                        PageRow(
                            title = bm.title.ifBlank { bm.url },
                            subtitle = bm.url,
                            onClick = { onOpenUrl(bm.url) },
                            actions = {
                                PageChip("编辑") { editing = bm }
                                Spacer(Modifier.width(6.dp))
                                PageChip("删除") {
                                    runCatching { repository?.removeBookmark(bm.id) }
                                    version++
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showImport) {
        BookmarkImportDialog(
            onDismiss = { showImport = false },
            onImport = { entries ->
                var added = 0
                entries.forEach { e ->
                    runCatching {
                        val existed = repository?.listBookmarks()?.any { it.url == e.url } == true
                        if (!existed) { repository?.addBookmark(e.url, e.title, e.folder, nowMs()); added++ }
                    }
                }
                showImport = false
                version++
                status = "已导入 $added 个书签（重复已跳过）"
            },
        )
    }

    if (showFolders) {
        val folders = runCatching { repository?.listBookmarks().orEmpty().groupBy { it.folder }.mapValues { it.value.size } }.getOrElse { emptyMap() }
        FolderManageDialog(
            folders = folders,
            onDismiss = { showFolders = false },
            onRename = { old, new -> runCatching { repository?.renameFolder(old, new) }; version++; showFolders = false },
            onDelete = { name -> runCatching { repository?.deleteFolder(name) }; version++; showFolders = false },
        )
    }

    editing?.let { bm ->
        BookmarkEditDialog(
            initialTitle = bm.title,
            initialFolder = bm.folder,
            onDismiss = { editing = null },
            onSave = { title, folder ->
                runCatching { repository?.updateBookmark(bm.id, bm.url, title, folder, nowMs()) }
                editing = null
                version++
            },
        )
    }
}

@Composable
private fun BookmarkEditDialog(
    initialTitle: String,
    initialFolder: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var folder by remember { mutableStateOf(initialFolder) }
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Text("编辑书签", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            PageSearchField(title, "标题", { title = it })
            Spacer(Modifier.height(8.dp))
            PageSearchField(folder, "文件夹（可留空）", { folder = it })
            Spacer(Modifier.height(12.dp))
            Row {
                PageChip("保存", primary = true) { onSave(title, folder) }
                Spacer(Modifier.width(8.dp))
                PageChip("取消", onClick = onDismiss)
            }
        }
    }
}


/** 导入对话框：支持"从网址拉取 HTML"与"粘贴 HTML"。 */
@Composable
private fun BookmarkImportDialog(
    onDismiss: () -> Unit,
    onImport: (List<cn.ac.oac.glimbrowser.bookmark.BookmarkHtml.Entry>) -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        ) {
            Text("导入书签", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("从网址导入（导出的书签 HTML 链接）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            PageSearchField(source, "https://example.com/bookmarks.html", { source = it })
            Spacer(Modifier.height(8.dp))
            Text("或粘贴 HTML", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            PageSearchField(pasted, "<DT><A HREF=\"…\">…</A>", { pasted = it })
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row {
                PageChip(if (busy) "导入中…" else "开始导入", primary = true) {
                    if (busy) return@PageChip
                    busy = true
                    error = null
                    scope.launch {
                        val html = when {
                            pasted.isNotBlank() -> pasted
                            source.startsWith("http") -> {
                                val client = cn.ac.oac.glimbrowser.ai.createAiHttpClient()
                                try {
                                    client.get(source).bodyAsText()
                                } catch (t: Throwable) {
                                    null
                                } finally {
                                    client.close()
                                }
                            }
                            else -> null
                        }
                        busy = false
                        if (html.isNullOrBlank()) {
                            error = "无法获取内容，请检查链接或粘贴 HTML"
                        } else {
                            val entries = cn.ac.oac.glimbrowser.bookmark.BookmarkHtml.parse(html)
                            if (entries.isEmpty()) error = "没有解析到书签（格式不受支持）" else onImport(entries)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                PageChip("取消", onClick = onDismiss)
            }
        }
    }
}

/** 文件夹管理：重命名 / 删除（书签移入未分类）。 */
@Composable
private fun FolderManageDialog(
    folders: Map<String, Int>,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editingFolder by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        ) {
            Text("文件夹", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            if (folders.isEmpty()) {
                Text("暂无文件夹。编辑书签时填写文件夹名即可创建。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(folders.entries.toList(), key = { it.key.ifEmpty { "__none__" } }) { (name, count) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "${name.ifBlank { "未分类" }} · $count",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            PageChip("改名") { editingFolder = name; newName = name }
                            Spacer(Modifier.width(6.dp))
                            if (name.isNotBlank()) {
                                PageChip("删除") { onDelete(name) }
                            }
                        }
                    }
                }
            }
            editingFolder?.let { old ->
                Spacer(Modifier.height(8.dp))
                PageSearchField(newName, "新的文件夹名（留空=移入未分类）", { newName = it })
                Spacer(Modifier.height(6.dp))
                Row {
                    PageChip("保存", primary = true) { onRename(old, newName.trim()) }
                    Spacer(Modifier.width(8.dp))
                    PageChip("取消") { editingFolder = null }
                }
            }
            Spacer(Modifier.height(10.dp))
            PageChip("关闭", onClick = onDismiss)
        }
    }
}
