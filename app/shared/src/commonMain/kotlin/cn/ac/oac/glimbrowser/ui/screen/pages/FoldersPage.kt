package cn.ac.oac.glimbrowser.ui.screen.pages

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
import cn.ac.oac.glimbrowser.bookmark.FolderStore
import cn.ac.oac.glimbrowser.data.GlimRepository

/** 文件夹管理页：新建 / 改名 / 删除（书签移入未分类）。 */
@Composable
fun FoldersPage(
    repository: GlimRepository?,
    onBack: () -> Unit,
) {
    val store = remember(repository) { FolderStore(repository) }
    var version by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val folders = remember(version) { store.folders() }

    PageScaffold(
        title = "文件夹",
        onBack = onBack,
        action = { PageChip("新建", primary = true) { creating = true; newName = "" } },
    ) {
        Spacer(Modifier.height(6.dp))
        if (folders.isEmpty()) {
            EmptyHint("还没有文件夹。点右上角“新建”，或编辑书签时填写文件夹名。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(folders.entries.toList(), key = { it.key.ifEmpty { "__none__" } }) { (name, count) ->
                    PageRow(
                        title = name.ifBlank { "未分类" },
                        subtitle = "$count 个书签",
                        actions = {
                            if (name.isNotBlank()) {
                                PageChip("改名") { editing = name; newName = name }
                                Spacer(Modifier.width(6.dp))
                                PageChip("删除") { store.delete(name); version++ }
                            }
                        },
                    )
                }
            }
        }
        if (creating || editing != null) {
            Spacer(Modifier.height(10.dp))
            PageSearchField(newName, "文件夹名称", { newName = it })
            Spacer(Modifier.height(6.dp))
            Row {
                PageChip("保存", primary = true) {
                    val target = editing
                    if (target == null) store.create(newName) else store.rename(target, newName)
                    creating = false
                    editing = null
                    version++
                }
                Spacer(Modifier.width(8.dp))
                PageChip("取消") { creating = false; editing = null }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "删除文件夹不会删除书签，其中的书签会移到「未分类」。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
