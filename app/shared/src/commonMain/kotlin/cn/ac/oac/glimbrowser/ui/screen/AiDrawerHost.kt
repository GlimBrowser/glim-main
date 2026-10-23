package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ai.AiOutcome
import cn.ac.oac.glimbrowser.ai.AiPanelState
import cn.ac.oac.glimbrowser.ai.AiRuntime
import cn.ac.oac.glimbrowser.ai.parseSummaryView
import cn.ac.oac.glimbrowser.ai.resolveAiProfile
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.logic.ApiGuideState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * AI 抽屉宿主（真实链路）：
 * 1. 摘要 = 平台内核取正文（document.innerText）→ AiRuntime.summarize（json_object + 降级链）→ 渲染
 * 2. 问答 = 同上下文 → 流式问答，逐段追加（对话类 stream=true，附件 C.1.1）
 * 3. 未配置档案 → 引导用户进三步引导（不伪造结果）
 */
@Composable
fun AiDrawerHost(
    visible: Boolean,
    runtime: AiRuntime?,
    repository: GlimRepository?,
    guide: ApiGuideState,
    engine: BrowserEngine?,
    onRequestGuide: () -> Unit,
    onRequestAiSettings: () -> Unit = {},
    prefs: cn.ac.oac.glimbrowser.ai.AiPreferences = cn.ac.oac.glimbrowser.ai.AiPreferences(),
    incognito: Boolean = false,
    pageUrl: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf(AiPanelState()) }
    val stored = remember(repository, guide.step) { runCatching { repository?.aiProfile("default") }.getOrNull() }
    val profile = remember(stored, guide) { resolveAiProfile(guide, stored) }
    val summary = remember(panel.summaryJson) { panel.summaryJson?.let { parseSummaryView(it) } }
    // 会话历史（本地持久化；含多轮）
    val store = remember(repository) { cn.ac.oac.glimbrowser.ai.AiSessionStore(repository) }
    var sessions by remember { mutableStateOf(store.load()) }
    var currentSessionId by remember { mutableStateOf(sessions.firstOrNull()?.id) }

    // 自动摘要（设置开启）：首次打开且有配置时自动跑一次
    var autoRan by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(visible, prefs.autoSummaryOnOpen, profile?.id) {
        if (visible && prefs.autoSummaryOnOpen && !autoRan && profile != null && runtime != null && engine != null) {
            autoRan = true
            panel = panel.copy(phase = AiPanelState.Phase.Loading, stage = "读取页面正文…")
            engine.evaluatePageText { text ->
                if (!text.isNullOrBlank()) {
                    scope.launch {
                        when (val r = runtime.summarize(profile, text, prefs)) {
                            is AiOutcome.Summary -> panel = panel.copy(phase = AiPanelState.Phase.Summary, summaryJson = r.json, summaryDegraded = r.degraded)
                            is AiOutcome.Error -> panel = panel.copy(phase = AiPanelState.Phase.Error, error = r.message, summaryJson = r.raw)
                        }
                    }
                } else {
                    panel = panel.copy(phase = AiPanelState.Phase.Error, error = "正文为空，无法自动摘要")
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)).clickable(onClick = onClose),
    )
    Box(modifier = Modifier.fillMaxSize().padding(top = 150.dp), contentAlignment = Alignment.BottomCenter) {
        if (!prefs.enabled || (prefs.disableInIncognito && incognito)) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        if (!prefs.enabled) "AI 已关闭" else "无痕模式下 AI 已禁用",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    androidx.compose.material3.Text(
                        if (!prefs.enabled) "到「AI 设置」打开总开关后可使用摘要与问答。"
                        else "这是隐私设置：无痕模式不向外部服务发送页面内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 10.dp))
                    cn.ac.oac.glimbrowser.ui.design.GlimButton("打开 AI 设置", onRequestAiSettings, small = true)
                }
            }
            return@Box
        }
        if (profile == null || runtime == null) {
            // 未配置：引导（真实入口，不展示假数据）
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                cn.ac.oac.glimbrowser.ui.screen.ApiGuideHint(onStart = onRequestGuide, onClose = onClose)
            }
            return@Box
        }
        AiDrawerSheet(
            state = panel,
            summary = summary,
            onSummarize = {
                val eng = engine
                if (eng == null) {
                    panel = panel.copy(phase = AiPanelState.Phase.Error, error = "当前页未接入内核，无法读取正文")
                } else {
                    panel = panel.copy(phase = AiPanelState.Phase.Loading, stage = "读取页面正文…", error = null)
                    eng.evaluatePageText { text ->
                        if (text.isNullOrBlank()) {
                            panel = panel.copy(phase = AiPanelState.Phase.Error, error = "正文为空（页面可能仍在加载或不支持）")
                        } else {
                            panel = panel.copy(stage = "请求模型中…")
                            scope.launch {
                                when (val r = runtime.summarize(profile, text, prefs)) {
                                    is AiOutcome.Summary -> panel = panel.copy(
                                        phase = AiPanelState.Phase.Summary,
                                        summaryJson = r.json,
                                        summaryDegraded = r.degraded,
                                    )
                                    is AiOutcome.Error -> panel = panel.copy(
                                        phase = AiPanelState.Phase.Error,
                                        error = r.message,
                                        summaryJson = r.raw,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            sessions = sessions,
            currentSessionId = currentSessionId,
            onNewSession = {
                currentSessionId = null
                panel = panel.copy(answer = "", phase = AiPanelState.Phase.Idle, error = null)
            },
            onSelectSession = { id ->
                currentSessionId = id
                panel = panel.copy(answer = "", phase = AiPanelState.Phase.Idle, error = null)
            },
            onDeleteSession = { id ->
                sessions = store.delete(sessions, id)
                if (currentSessionId == id) currentSessionId = null
            },
            onOpenSettings = { onRequestAiSettings() },
            onQuestionChange = { panel = panel.copy(question = it) },
            onAsk = {
                val q = panel.question.trim()
                val eng = engine
                if (q.isEmpty()) return@AiDrawerSheet
                if (eng == null) {
                    panel = panel.copy(phase = AiPanelState.Phase.Error, error = "当前页未接入内核")
                } else {
                    panel = panel.copy(phase = AiPanelState.Phase.Answering, answer = "", error = null, stage = "读取页面正文…")
                    eng.evaluatePageText { text ->
                        if (text.isNullOrBlank()) {
                            panel = panel.copy(phase = AiPanelState.Phase.Error, error = "正文为空")
                            return@evaluatePageText
                        }
                        scope.launch {
                            panel = panel.copy(stage = "生成回答…")
                            // 会话：无当前会话则新建
                            val sid = currentSessionId ?: "s-" + kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                            currentSessionId = sid
                            // 历史轮次（不含本轮）
                            val history = sessions.firstOrNull { it.id == sid }?.messages
                                ?.map { it.role to it.content }.orEmpty()
                            val at = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                            sessions = store.append(sessions, sid, cn.ac.oac.glimbrowser.ai.AiMessage("user", q, at))
                            var acc = ""
                            runtime.askFlow(profile, text, history, q, prefs)
                                .catch { e ->
                                    panel = panel.copy(phase = AiPanelState.Phase.Error, error = e.message ?: "网络错误")
                                }
                                .collect { piece ->
                                    acc += piece
                                    panel = panel.copy(answer = acc)
                                }
                            if (acc.isNotBlank()) {
                                sessions = store.append(
                                    sessions, sid,
                                    cn.ac.oac.glimbrowser.ai.AiMessage("assistant", acc,
                                        kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds),
                                )
                                panel = panel.copy(phase = AiPanelState.Phase.Idle, answer = "", question = "", stage = "")
                            } else if (panel.phase == AiPanelState.Phase.Answering) {
                                panel = panel.copy(stage = "")
                            }
                        }
                    }
                }
            },
            onClose = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 未配置提示（真实跳转引导）。 */
@Composable
private fun ApiGuideHint(onStart: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text("尚未配置 AI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))
            androidx.compose.material3.Text("填入你自己的 AI 服务地址与密钥，即可使用摘要和页面问答。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.layout.Row {
                Box(
                    modifier = Modifier.backgroundColor(MaterialTheme.colorScheme.primary).clickable(onClick = onStart).padding(horizontal = 16.dp, vertical = 8.dp),
                ) { androidx.compose.material3.Text("去配置", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 8.dp))
                Box(
                    modifier = Modifier.backgroundColor(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 8.dp),
                ) { androidx.compose.material3.Text("关闭", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Compose Modifier 扩展：背景色（简化叠加写法）。 */
private fun Modifier.backgroundColor(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color))
