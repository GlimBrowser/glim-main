package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.network.AiCapabilities
import cn.ac.oac.glimbrowser.network.AiError

/**
 * API 三步引导状态机（附件 D.4 交互说明；PRD V1.1 §3.2.1 配置项 + §3.2.4 轻/重入口）。
 *
 * ```
 * WELCOME(未配置入口) → STEP1(填 Base URL/Key/Model) → STEP2(测试连接) → STEP3(能力确认/保存)
 *      ↑ 关闭(轻入口：侧边把手点出，55% 高度) ← 任何步骤可取消
 * ```
 * 纯逻辑可单测（PRD V1.2 §2.3 原则 1）；持久化/SecureKeyStore 接入在 Phase 6。
 */
data class ApiGuideState(
    val step: Step = Step.WELCOME,
    /** 步骤一输入（附件 D.4.1）。 */
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val modelInput: String = "",
    /** 测试连接结果（附件 D.2.3/D.3）。 */
    val connectivity: ConnectivityUi? = null,
    val testing: Boolean = false,
    /** 能力探测（附件 D.2.4；不支持不阻断保存，PRD §3.2.6）。 */
    val capabilities: AiCapabilities? = null,
    /** 校验错误（附件 D.4.1 行内提示）。 */
    val validationError: String? = null,
    /** 从 /models 拉到的可用模型（真实列表，可为空）。 */
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    /** 用户选择"跳过测试直接保存"。 */
    val skippedTest: Boolean = false,
) {
    enum class Step { WELCOME, STEP1, STEP2, STEP3, DONE }

    /** 连通性 UI 态（对应 network.ConnectivityResult 的展示映射）。 */
    data class ConnectivityUi(
        val success: Boolean,
        val latencyMs: Long? = null,
        val modelEcho: String? = null,
        val error: AiError? = null,
    )

    /** 步骤一是否可"测试连接"（附件 D.4.1：Base URL + Key + Model 均非空）。 */
    val canTest: Boolean
        get() = baseUrlInput.isNotBlank() && apiKeyInput.isNotBlank() && modelInput.isNotBlank()

    /** 完整配置（保存前校验，附件 D.4.3）；允许跳过测试直接保存。 */
    val canSave: Boolean
        get() = modelInput.isNotBlank() && (connectivity?.success == true || skippedTest)
}

/**
 * 三步引导操作（纯函数迁移；测试连接的网络调用由 UI 层 dispatch 到 network 层后回填 connectivity）。
 */
object ApiGuideActions {

    fun open(state: ApiGuideState): ApiGuideState = state.copy(step = ApiGuideState.Step.WELCOME)

    fun close(state: ApiGuideState): ApiGuideState = state.copy(step = ApiGuideState.Step.WELCOME)

    fun nextFromWelcome(state: ApiGuideState): ApiGuideState =
        state.copy(step = ApiGuideState.Step.STEP1, validationError = null)

    fun back(state: ApiGuideState): ApiGuideState = when (state.step) {
        ApiGuideState.Step.STEP1 -> state.copy(step = ApiGuideState.Step.WELCOME)
        ApiGuideState.Step.STEP2 -> state.copy(step = ApiGuideState.Step.STEP1)
        ApiGuideState.Step.STEP3 -> state.copy(step = ApiGuideState.Step.STEP2)
        else -> state
    }

    fun onBaseUrlChange(state: ApiGuideState, v: String): ApiGuideState =
        state.copy(baseUrlInput = v.trim(), validationError = null, connectivity = null, capabilities = null)

    fun onApiKeyChange(state: ApiGuideState, v: String): ApiGuideState =
        state.copy(apiKeyInput = v.trim(), validationError = null, connectivity = null, capabilities = null)

    fun onModelChange(state: ApiGuideState, v: String): ApiGuideState =
        state.copy(modelInput = v.trim(), validationError = null, connectivity = null, capabilities = null)

    /** 步骤一校验（附件 D.4.1：非空 + https；http 仅 localhost 需二次确认——校验层先拦）。 */
    fun validateStep1(state: ApiGuideState, allowInsecure: Boolean = false): ApiGuideState {
        val url = state.baseUrlInput
        return when {
            url.isBlank() || state.apiKeyInput.isBlank() || state.modelInput.isBlank() ->
                state.copy(validationError = "Base URL、API Key、Model 均需填写。")
            !url.startsWith("https://") && !allowInsecure && !(url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) ->
                state.copy(validationError = "需 HTTPS（本地 http 端点需二次确认）。")
            // 每次进入 STEP2 都重新测试（清掉上一次结果，避免展示过期状态）
            else -> state.copy(
                step = ApiGuideState.Step.STEP2,
                validationError = null,
                connectivity = null,
                capabilities = null,
                testing = false,
            )
        }
    }

    /** 测试中态（UI 发起网络测试前后调用）。 */
    fun beginTest(state: ApiGuideState): ApiGuideState =
        state.copy(step = ApiGuideState.Step.STEP2, testing = true, connectivity = null)

    /** 回填测试结果（network 层回调）。 */
    fun onTestResult(state: ApiGuideState, result: ApiGuideState.ConnectivityUi, caps: AiCapabilities?): ApiGuideState =
        state.copy(testing = false, connectivity = result, capabilities = caps)

    /** 开始拉取模型列表。 */
    fun beginFetchModels(state: ApiGuideState): ApiGuideState =
        state.copy(modelsLoading = true, modelsError = null)

    /** 模型列表回填（成功/失败都回到可编辑态）。 */
    fun onModelsResult(state: ApiGuideState, models: List<String>, error: String?): ApiGuideState =
        state.copy(modelsLoading = false, models = models, modelsError = error)

    /** 跳过测试直接保存：仍要求三项都填、且不是明文 http（本机除外），再进能力确认。 */
    fun skipTest(state: ApiGuideState, allowInsecure: Boolean = false): ApiGuideState {
        val url = state.baseUrlInput
        val error = when {
            url.isBlank() || state.apiKeyInput.isBlank() || state.modelInput.isBlank() ->
                "Base URL、API Key、Model 均需填写后才能保存。"
            !url.startsWith("https://") && !allowInsecure &&
                !(url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) ->
                "需 HTTPS（本地 http 端点需二次确认）。"
            else -> null
        }
        return if (error != null) state.copy(validationError = error)
        else state.copy(
            step = ApiGuideState.Step.STEP3,
            skippedTest = true,
            testing = false,
            connectivity = null,
            validationError = null,
        )
    }

    /** STEP2 测试成功 → STEP3（能力确认，附件 D.4.2）。 */
    fun nextFromStep2(state: ApiGuideState): ApiGuideState =
        if (state.connectivity?.success == true) state.copy(step = ApiGuideState.Step.STEP3) else state

    /** 步骤三（能力确认）→ 保存完成。 */
    fun finish(state: ApiGuideState): ApiGuideState =
        state.copy(step = ApiGuideState.Step.DONE)
}
