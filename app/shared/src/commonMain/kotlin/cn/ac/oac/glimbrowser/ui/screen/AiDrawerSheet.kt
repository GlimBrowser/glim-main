package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ai.AiPanelState
import cn.ac.oac.glimbrowser.ai.SummaryView
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * AI 抽屉（真实链路：摘要/问答走 BYOK 直连；未配置→三步引导）。
 *
 * 状态全部来自 [AiPanelState]（loading/结构化摘要/流式回答/错误），**无静态示例数据**。
 */
@Composable
fun AiDrawerSheet(
    state: AiPanelState,
    summary: SummaryView?,
    onSummarize: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 会话历史（多轮）。 */
    sessions: List<cn.ac.oac.glimbrowser.ai.AiSession> = emptyList(),
    currentSessionId: String? = null,
    onNewSession: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val currentMessages = sessions.firstOrNull { it.id == currentSessionId }?.messages.orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Box(modifier = Modifier.width(38.dp).height(4.dp).clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)).align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ AI 助手", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "AI 设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(4.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 操作条：摘要本页 / 新问题
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Pill("摘要本页", enabled = !state.busy, onClick = onSummarize)
            Pill("清空", enabled = !state.busy, onClick = { onQuestionChange("") })
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 会话切换（最近 4 个 + 新建）
        if (sessions.isNotEmpty()) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                sessions.take(3).forEach { sess ->
                    val active = sess.id == currentSessionId
                    Box(
                        modifier = Modifier.clip(GlimTokens.RadiusPill)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelectSession(sess.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            sess.title.take(10).ifBlank { "会话" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                PageChipSmall("＋新会话", onNewSession)
                if (currentSessionId != null) PageChipSmall("删除", { onDeleteSession(currentSessionId) })
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 多轮消息
        if (currentMessages.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                currentMessages.forEach { msg ->
                    val mine = msg.role == "user"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(10.dp),
                        ) {
                            Text(msg.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 内容区（可滚动，固定最大高度）
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            when (state.phase) {
                AiPanelState.Phase.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.stage.ifBlank { "处理中…" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AiPanelState.Phase.Summary -> {
                    val s = summary
                    if (s == null) {
                        Text("摘要解析失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(s.title.ifBlank { "（无标题）" }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        s.readingMinutes?.let {
                            Text("约 $it 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        s.keyPoints.forEachIndexed { i, p ->
                            Text("${i + 1}. $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
                        }
                        if (s.conclusion.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(s.conclusion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.summaryDegraded) {
                            Text("（结果由兼容模式解析获得）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                AiPanelState.Phase.Answering, AiPanelState.Phase.Idle -> {
                    if (state.answer.isNotBlank()) {
                        Text(state.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else if (currentMessages.isEmpty()) {
                        Text("点「摘要本页」生成结构化摘要，或输入问题与页面问答。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AiPanelState.Phase.Error -> {
                    Text(state.error ?: "未知错误", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // 问答输入
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(GlimTokens.RadiusSm)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        if (state.question.isEmpty()) Text("问这页内容…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Pill("发送", enabled = !state.busy && state.question.isNotBlank(), onClick = onAsk)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClose).padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { Text("关闭", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(GlimTokens.RadiusPill)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun PageChipSmall(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
