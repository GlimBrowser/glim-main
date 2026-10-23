package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.data.Plugin_install
import cn.ac.oac.glimbrowser.plugin.PluginImporter
import cn.ac.oac.glimbrowser.plugin.Permission
import kotlinx.coroutines.launch

/**
 * 插件页：按 URL 导入用户脚本、启用/停用、查看权限、卸载。
 * 执行沙箱未接入，因此明确提示"仅登记与授权，脚本暂不执行"。
 */
@Composable
fun PluginsPage(
    repository: GlimRepository?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val rows = remember(version) { runCatching { repository?.plugins().orEmpty() }.getOrElse { emptyList() } }

    PageScaffold(title = "插件", onBack = onBack) {
        Spacer(Modifier.height(4.dp))
        PageSearchField(url, "粘贴 .user.js 地址后导入", { url = it; message = null })
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Row {
            PageChip("导入", primary = true) {
                if (url.isBlank() || importing) return@PageChip
                importing = true
                message = null
                scope.launch {
                    val fetched = cn.ac.oac.glimbrowser.plugin.PluginImporter.fetchAndParseWithSource(url.trim())
                    val parsed = fetched?.first
                    importing = false
                    if (parsed == null) {
                        message = "导入失败：无法读取该地址或不是有效的用户脚本"
                    } else {
                        runCatching {
                            val nowInstall = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                            val id = url.trim() + "@" + nowInstall
                            cn.ac.oac.glimbrowser.plugin.PluginRunner.saveScript(repository, id, fetched!!.second, nowInstall)
                            repository?.upsertPlugin(
                                Plugin_install(
                                    id = id,
                                    name = parsed.name,
                                    version = parsed.version,
                                    description = parsed.description,
                                    source_url = url.trim(),
                                    fingerprint = "",
                                    matches_json = parsed.matches.joinToString("\",\"", "[\"", "\"]").ifEmpty { "[]" },
                                    permissions_json = parsed.permissions.joinToString("\",\"", "[\"", "\"]").ifEmpty { "[]" },
                                    granted_json = parsed.permissions.joinToString("\",\"", "[\"", "\"]").ifEmpty { "[]" },
                                    enabled = 0,
                                    builtin = 0,
                                    subscription_id = null,
                                    revoked = 0,
                                    installed_at = nowInstall,
                                ),
                            )
                        }
                        message = "已导入「${parsed.name}」${parsed.version}，授予 ${parsed.permissions.size} 项权限"
                        url = ""
                        version++
                    }
                }
            }
            Spacer(Modifier.height(0.dp).then(Modifier.padding(start = 6.dp)))
            if (importing) CircularProgressIndicator(modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            EmptyHint("还没有插件。粘贴一个用户脚本地址即可导入；启用后会在匹配的网页加载完成时执行。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.id }) { p ->
                    PageRow(
                        title = "${p.name} ${p.version}",
                        subtitle = buildString {
                            append(if (p.revoked != 0L) "已吊销" else if (p.enabled != 0L) "已启用" else "已停用")
                            val perms = parsePerms(p.permissions_json)
                            if (perms.isNotEmpty()) append(" · 权限：").append(perms.joinToString("、"))
                        },
                        actions = {
                            PageChip(if (p.enabled != 0L) "停用" else "启用") {
                                runCatching { repository?.setPluginEnabled(p.id, p.enabled == 0L) }
                                version++
                            }
                            Spacer(Modifier.height(0.dp).then(Modifier.padding(start = 6.dp)))
                            PageChip("卸载") { runCatching { repository?.removePlugin(p.id) }; version++ }
                        },
                    )
                }
            }
        }
    }
}

private fun parsePerms(json: String): List<String> =
    json.removePrefix("[").removeSuffix("]").split(",")
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
        .map { raw: String ->
            runCatching { Permission.valueOf(raw).purpose }.getOrDefault(raw)
        }
