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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 全屏搜索视图（原型 view-search V3.1）。
 *
 * 与原型一致：**没有"取消"文案**——关闭靠系统返回/下拉手势；右侧是一个圆形「前往」按钮。
 * 输入网址直接打开，输入文字交给搜索引擎解析（复用 resolveAddressBarInput）。
 */
@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    repository: GlimRepository?,
    modifier: Modifier = Modifier,
    /** 模型智能联想（为空则只用本地历史）。失败返回空列表即可。 */
    aiSuggest: (suspend (String) -> List<String>)? = null,
) {
    val focus = remember { FocusRequester() }
    val store = remember(repository) { cn.ac.oac.glimbrowser.search.SearchSuggestionStore(repository) }
    var version by remember { mutableStateOf(0) }
    val suggestions = remember(query, version, repository) { store.suggest(query) }
    var aiList by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiLoading by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(query, aiSuggest) {
        val q = query.trim()
        if (aiSuggest == null || q.length < 2) {
            aiList = emptyList(); aiLoading = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)   // 防抖
        aiLoading = true
        aiList = runCatching { aiSuggest(q) }.getOrElse { emptyList() }
        aiLoading = false
    }
    // 提交：若是搜索词（非 URL）则记录，供下次联想
    val submit: (String) -> Unit = { raw ->
        val q = raw.trim()
        if (q.isNotEmpty()) {
            val r = cn.ac.oac.glimbrowser.logic.resolveAddressBarInput(q)
            if (r.intent == cn.ac.oac.glimbrowser.logic.AddressBarIntent.SEARCH) {
                store.record(q)
                version++
            }
            onSubmit(q)
        }
    }
    val recent = remember(repository) {
        runCatching { repository?.recentHistory(8).orEmpty().map { it.url to it.title }.distinctBy { it.first } }
            .getOrElse { emptyList() }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部：输入胶囊（玉青描边 + 光晕）+ 圆形前往
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, GlimTokens.RadiusPill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                    name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.SEARCH,
                    size = 18.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜索或输入网址",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(GlimTokens.RadiusPill)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                            name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.CLOSE,
                            size = 12.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // 圆形「前往」按钮（原型 .go-icon）
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(
                        if (query.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                    .clickable(enabled = query.isNotBlank()) { submit(query) },
                contentAlignment = Alignment.Center,
            ) {
                cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                    name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.ARROW_RIGHT,
                    size = 18.dp,
                    color = if (query.isBlank()) MaterialTheme.colorScheme.outline else Color.White,
                )
            }
        }

        Text(
            "输入网址直接打开，输入文字用搜索引擎",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                cn.ac.oac.glimbrowser.ui.design.GlimIconName.SPARKLE,
                size = 11.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "支持搜索联想与快捷搜索，敬请期待",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp),
        ) {
            // 模型智能联想
            if (query.isNotBlank() && aiLoading) {
                GroupTitle("AI 联想")
                Text(
                    "正在生成联想…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else if (query.isNotBlank() && aiList.isNotEmpty()) {
                GroupTitle("AI 联想")
                aiList.forEach { sug ->
                    SuggestRow(
                        icon = cn.ac.oac.glimbrowser.ui.design.GlimIconName.SPARKLE,
                        title = sug,
                        subtitle = "",
                        onClick = { submit(sug) },
                    )
                }
            }
            // 本地联想（基于历史搜索的自进化排序）
            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                GroupTitle("历史联想")
                suggestions.forEach { sug ->
                    SuggestRow(
                        icon = cn.ac.oac.glimbrowser.ui.design.GlimIconName.SEARCH,
                        title = sug,
                        subtitle = "",
                        onClick = { submit(sug) },
                    )
                }
            }
            if (query.isBlank() && recent.isNotEmpty()) {
                GroupTitle("最近访问")
                recent.forEach { (url, title) ->
                    SuggestRow(
                        icon = cn.ac.oac.glimbrowser.ui.design.GlimIconName.CLOCK,
                        title = title.ifBlank { url },
                        subtitle = url,
                        onClick = { submit(url) },
                    )
                }
            }
            GroupTitle("快捷站点")
            listOf("维基百科" to "https://zh.wikipedia.org", "GitHub" to "https://github.com").forEach { (name, url) ->
                SuggestRow(
                    icon = null,
                    letter = name.take(1),
                    title = name,
                    subtitle = url,
                    onClick = { submit(url) },
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun SuggestRow(
    icon: cn.ac.oac.glimbrowser.ui.design.GlimIconName?,
    letter: String? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(GlimTokens.Radius12)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            if (letter != null) {
                Text(
                    letter.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (icon != null) {
                cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                    name = icon,
                    size = 17.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        cn.ac.oac.glimbrowser.ui.design.GlimIcon(
            name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.ARROW_RIGHT,
            size = 14.dp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SearchGlyph() {
    Box(modifier = Modifier.size(18.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.TopStart)
                .border(2.dp, MaterialTheme.colorScheme.primary, GlimTokens.RadiusPill),
        )
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 2.dp)
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
