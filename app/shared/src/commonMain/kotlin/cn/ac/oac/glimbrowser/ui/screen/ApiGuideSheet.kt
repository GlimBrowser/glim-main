package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.logic.ApiGuideActions
import cn.ac.oac.glimbrowser.logic.ApiGuideState
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * API 三步引导（附件 D.4 交互说明 + 原型 cfg 屏：stepper/字段/测试连接/能力确认）。
 *
 * 轻/重入口（PRD §3.2.4）：✨ 未配置 → 此 Sheet（侧边把手点出，55% 高度）；已配置 → 摘要（Phase 5 后半接入内容）。
 * 红线（附件 D.3/D.4）：Key 默认掩码；测试失败主按钮"返回修改"；能力位不阻断保存。
 *
 * @param state 引导状态（logic.ApiGuideState）
 * @param onEvent 状态事件分发（logic.ApiGuideActions 的调用入口）
 */
@Composable
fun ApiGuideSheet(
    state: ApiGuideState,
    onEvent: (ApiGuideEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // 抓手
        Box(
            modifier = Modifier
                .width(38.dp).height(4.dp)
                .clip(GlimTokens.RadiusPill)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state.step) {
            ApiGuideState.Step.WELCOME -> GuideWelcome(onEvent)
            ApiGuideState.Step.STEP1 -> GuideStep1(state, onEvent)
            ApiGuideState.Step.STEP2 -> GuideStep2(state, onEvent)
            ApiGuideState.Step.STEP3 -> GuideStep3(state, onEvent)
            ApiGuideState.Step.DONE -> GuideDone(onEvent)
        }
    }
}

/** UI 事件 → logic 状态迁移（纯函数映射，测试友好）。 */
fun ApiGuideEvent.applyTo(state: ApiGuideState): ApiGuideState = when (this) {
    ApiGuideEvent.Close -> ApiGuideActions.close(state)
    ApiGuideEvent.NextWelcome -> ApiGuideActions.nextFromWelcome(state)
    ApiGuideEvent.Back -> ApiGuideActions.back(state)
    is ApiGuideEvent.BaseUrlChanged -> ApiGuideActions.onBaseUrlChange(state, v)
    is ApiGuideEvent.ApiKeyChanged -> ApiGuideActions.onApiKeyChange(state, v)
    is ApiGuideEvent.ModelChanged -> ApiGuideActions.onModelChange(state, v)
    ApiGuideEvent.ValidateStep1 -> ApiGuideActions.validateStep1(state)
    ApiGuideEvent.TestConnection -> ApiGuideActions.beginTest(state)
    is ApiGuideEvent.TestResult -> ApiGuideActions.onTestResult(state, result, caps)
    ApiGuideEvent.NextStep2 -> ApiGuideActions.nextFromStep2(state)
    ApiGuideEvent.Finish -> ApiGuideActions.finish(state)
    ApiGuideEvent.FetchModels -> ApiGuideActions.beginFetchModels(state)
    is ApiGuideEvent.ModelsResult -> ApiGuideActions.onModelsResult(state, models, error)
    ApiGuideEvent.SkipTest -> ApiGuideActions.skipTest(state)
}

/** 引导事件（UI → logic；logic.ApiGuideActions 消费）。 */
sealed interface ApiGuideEvent {
    data object Close : ApiGuideEvent
    data object NextWelcome : ApiGuideEvent
    data object Back : ApiGuideEvent
    data class BaseUrlChanged(val v: String) : ApiGuideEvent
    data class ApiKeyChanged(val v: String) : ApiGuideEvent
    data class ModelChanged(val v: String) : ApiGuideEvent
    data object ValidateStep1 : ApiGuideEvent
    data object TestConnection : ApiGuideEvent
    /** 真实网络测试返回（UI 宿主调用后回填）。 */
    data class TestResult(
        val result: cn.ac.oac.glimbrowser.logic.ApiGuideState.ConnectivityUi,
        val caps: cn.ac.oac.glimbrowser.network.AiCapabilities? = null,
    ) : ApiGuideEvent
    /** STEP2 成功后 → STEP3（能力确认）。 */
    data object NextStep2 : ApiGuideEvent
    data object Finish : ApiGuideEvent
    /** 拉取模型列表（GET /models）。 */
    data object FetchModels : ApiGuideEvent
    /** 模型列表结果。 */
    data class ModelsResult(val models: List<String>, val error: String? = null) : ApiGuideEvent
    /** 跳过测试直接保存（附件 D 允许，但需告知未验证）。 */
    data object SkipTest : ApiGuideEvent
}

/** 步骤指示器（原型 cfg .stepper：✓ — ✓ — 3）。 */
@Composable
private fun Stepper(current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            val done = i < current
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(if (done || i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (done) "✓" else "$i",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done || i == current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                )
            }
            if (i < 3) Box(modifier = Modifier.width(24.dp).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        }
    }
}

@Composable
private fun GuideWelcome(onEvent: (ApiGuideEvent) -> Unit) {
    Text("AI 未配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "摘要和问答需要你自己的 AI 服务。三步完成，密钥只保存在本机安全区。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(14.dp))
    PrimaryButton("开始配置（3 步）") { onEvent(ApiGuideEvent.NextWelcome) }
}

@Composable
private fun GuideStep1(state: ApiGuideState, onEvent: (ApiGuideEvent) -> Unit) {
    Stepper(1)
    Spacer(modifier = Modifier.height(10.dp))
    Text("Base URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    GuideField(state.baseUrlInput, "https://api.openai.com/v1") { onEvent(ApiGuideEvent.BaseUrlChanged(it)) }
    Spacer(modifier = Modifier.height(10.dp))
    Text("API Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    GuideField(state.apiKeyInput, "sk-…", isPassword = true) { onEvent(ApiGuideEvent.ApiKeyChanged(it)) }
    Spacer(modifier = Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
        SecondaryTextButton(if (state.modelsLoading) "拉取中…" else "拉取模型列表") {
            if (!state.modelsLoading) onEvent(ApiGuideEvent.FetchModels)
        }
    }
    GuideField(state.modelInput, "gpt-4o-mini") { onEvent(ApiGuideEvent.ModelChanged(it)) }

    // 真实模型列表（点击即填入）
    if (state.models.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            state.models.take(12).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { id ->
                        ModelChip(id, selected = id == state.modelInput) {
                            onEvent(ApiGuideEvent.ModelChanged(id))
                        }
                    }
                }
            }
            if (state.models.size > 12) {
                Text(
                    "共 ${state.models.size} 个模型，已显示前 12 个；可直接输入其它模型名",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    state.modelsError?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }

    state.validationError?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(14.dp))
    PrimaryButton("测试连接（超时 30 秒）") { onEvent(ApiGuideEvent.ValidateStep1) }
    Spacer(modifier = Modifier.height(6.dp))
    SecondaryTextButton("跳过测试，直接保存") { onEvent(ApiGuideEvent.SkipTest) }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "本地 http 端点（例如 Ollama）需要在高级设置中确认后可用",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun GuideStep2(state: ApiGuideState, onEvent: (ApiGuideEvent) -> Unit) {
    Stepper(2)
    Spacer(modifier = Modifier.height(10.dp))
    Text("测试连接", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(8.dp))
    when {
        state.testing -> {
            CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text("正在向你的服务发起一次最小请求…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        state.connectivity?.success == true -> {
            val c = state.connectivity
            Text("连接成功 · ${c.latencyMs ?: "?"}ms", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            c.modelEcho?.let { Text("模型：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton("下一步（能力确认）") { onEvent(ApiGuideEvent.NextStep2) }
        }
        state.connectivity != null -> {
            val err = state.connectivity.error
            Text("连接失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Text(err?.userMessage ?: "未知错误", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton("返回修改") { onEvent(ApiGuideEvent.Back) }   // D.3：主按钮"返回修改"
        }
        else -> {
            Text("准备测试…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            PrimaryButton("返回修改") { onEvent(ApiGuideEvent.Back) }
        }
    }
}

@Composable
private fun GuideStep3(state: ApiGuideState, onEvent: (ApiGuideEvent) -> Unit) {
    Stepper(3)
    Spacer(modifier = Modifier.height(10.dp))
    Text("能力确认", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(6.dp))
    val caps = state.capabilities
    if (caps != null) {
        CapabilityRow("结构化摘要与提取", caps.jsonObject)
        CapabilityRow("逐字输出（打字机效果）", caps.stream)
        if (!caps.jsonObject) {
            Text(
                "该服务不支持结构化输出，将自动改用兼容模式，功能仍然可用。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (state.skippedTest) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "你选择了跳过测试：配置会保存并启用，但端点是否可用尚未验证。可稍后在「AI 设置」里测试连接。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    PrimaryButton("完成并启用") { onEvent(ApiGuideEvent.Finish) }
}

@Composable
private fun GuideDone(onEvent: (ApiGuideEvent) -> Unit) {
    Text("配置完成", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "已可用：点地址栏的 AI 按钮可以摘要当前页面，也能做划词解释、翻译和页面问答。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(14.dp))
    PrimaryButton("关闭") { onEvent(ApiGuideEvent.Close) }
}

@Composable
private fun CapabilityRow(label: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "—", style = MaterialTheme.typography.labelLarge, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GuideField(value: String, placeholder: String, isPassword: Boolean = false, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlimTokens.RadiusSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                inner()
            }
        },
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** 紧凑的二级文字按钮（不抢主按钮视觉）。 */
@Composable
private fun SecondaryTextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(cn.ac.oac.glimbrowser.ui.theme.GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 模型选择 chip（等宽小字，选中态玉青）。 */
@Composable
private fun ModelChip(id: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .widthIn(max = 152.dp)
            .clip(cn.ac.oac.glimbrowser.ui.theme.GlimTokens.RadiusPill)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(id, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
