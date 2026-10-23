package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ai.AiRuntime
import cn.ac.oac.glimbrowser.logic.ApiGuideState
import cn.ac.oac.glimbrowser.network.AiError
import cn.ac.oac.glimbrowser.network.ConnectivityResult
import cn.ac.oac.glimbrowser.network.HttpErrorMapper

/**
 * 三步引导宿主：把「测试连接」真正接到网络层（此前只有状态机、没有请求）。
 *
 * STEP2 触发一次真实最小请求（chat/completions, max_tokens=1），回填延迟/模型回显/能力位；
 * 失败回填附件 D.3 的人话错误。
 */
@Composable
fun ApiGuideHost(
    state: ApiGuideState,
    runtime: AiRuntime?,
    onEvent: (ApiGuideEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attemptKey = "${state.baseUrlInput}|${state.modelInput}|${state.apiKeyInput.hashCode()}"
    LaunchedEffect(state.step, attemptKey) {
        if (state.step != ApiGuideState.Step.STEP2) return@LaunchedEffect
        if (state.connectivity != null) return@LaunchedEffect
        if (state.testing) return@LaunchedEffect
        onEvent(ApiGuideEvent.TestConnection)   // 进入测试中态
        val rt = runtime
        val result: ConnectivityResult = if (rt == null) {
            ConnectivityResult(false, error = AiError.UNKNOWN)
        } else {
            runCatching {
                rt.testConnectionRaw(
                    baseUrl = state.baseUrlInput,
                    apiKey = state.apiKeyInput,
                    model = state.modelInput,
                    timeoutSeconds = 20,
                )
            }.getOrElse { t -> ConnectivityResult(false, error = HttpErrorMapper.fromThrowable(t)) }
        }
        onEvent(
            ApiGuideEvent.TestResult(
                ApiGuideState.ConnectivityUi(
                    success = result.success,
                    latencyMs = result.latencyMs,
                    modelEcho = result.modelEcho,
                    error = result.error,
                ),
                caps = result.capabilities,
            ),
        )
    }

    // 模型列表拉取（GET /models）
    LaunchedEffect(state.modelsLoading) {
        if (!state.modelsLoading) return@LaunchedEffect
        val rt = runtime
        if (rt == null) {
            onEvent(ApiGuideEvent.ModelsResult(emptyList(), "AI 运行环境未就绪"))
            return@LaunchedEffect
        }
        if (state.baseUrlInput.isBlank() || state.apiKeyInput.isBlank()) {
            onEvent(ApiGuideEvent.ModelsResult(emptyList(), "请先填写 Base URL 与 API Key"))
            return@LaunchedEffect
        }
        val result = runCatching {
            rt.listModelsRaw(state.baseUrlInput, state.apiKeyInput, timeoutSeconds = 30)
        }
        onEvent(
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) ApiGuideEvent.ModelsResult(emptyList(), "该端点没有返回可用模型，可手动填写")
                    else ApiGuideEvent.ModelsResult(list)
                },
                onFailure = { t ->
                    ApiGuideEvent.ModelsResult(emptyList(), t.message?.take(120) ?: "拉取失败，请检查端点与 Key")
                },
            ),
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
            .clickable(onClick = onClose),
    )
    Box(modifier = Modifier.fillMaxSize().padding(top = 140.dp), contentAlignment = Alignment.BottomCenter) {
        ApiGuideSheet(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
