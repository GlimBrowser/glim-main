package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.network.AiCapabilities
import cn.ac.oac.glimbrowser.network.AiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * API 三步引导状态机单测（附件 D.4 交互流转 + D.4.1 校验 + D.2.4 能力不阻断保存）。
 */
class ApiGuideStateTest {

    @Test
    fun welcomeToStep1AndBack() {
        var s = ApiGuideState()
        s = ApiGuideActions.nextFromWelcome(s)
        assertEquals(ApiGuideState.Step.STEP1, s.step)
        s = ApiGuideActions.back(s)
        assertEquals(ApiGuideState.Step.WELCOME, s.step)
    }

    @Test
    fun step1ValidationRequiresAllFields() {
        var s = ApiGuideActions.nextFromWelcome(ApiGuideState())
        s = ApiGuideActions.onBaseUrlChange(s, "https://api.openai.com/v1")
        s = ApiGuideActions.validateStep1(s)
        assertNotNull(s.validationError)          // Key/Model 缺失
        assertEquals(ApiGuideState.Step.STEP1, s.step)
        s = ApiGuideActions.onApiKeyChange(s, "sk-test")
        s = ApiGuideActions.onModelChange(s, "gpt-4o-mini")
        s = ApiGuideActions.validateStep1(s)
        assertNull(s.validationError)
        assertEquals(ApiGuideState.Step.STEP2, s.step)
    }

    @Test
    fun httpsEnforced_exceptLocalhost() {
        var s = ApiGuideActions.nextFromWelcome(ApiGuideState())
        s = ApiGuideActions.onBaseUrlChange(s, "http://insecure.example/v1")
        s = ApiGuideActions.onApiKeyChange(s, "k").let { ApiGuideActions.onModelChange(it, "m") }
        s = ApiGuideActions.validateStep1(s)
        assertTrue(s.validationError!!.contains("HTTPS"))
        // localhost http 放行（Ollama 场景，附件 D.2.2）
        val local = s.copy(validationError = null)
        val s2 = ApiGuideActions.validateStep1(local)
        assertFalse(s2.validationError!!.contains("HTTPS") && s2.step == ApiGuideState.Step.STEP2)
    }

    @Test
    fun testLifecycle() {
        var s = ApiGuideActions.nextFromWelcome(ApiGuideState())
        s = ApiGuideActions.onBaseUrlChange(s, "https://api.openai.com/v1")
        s = ApiGuideActions.onApiKeyChange(s, "sk-test")
        s = ApiGuideActions.onModelChange(s, "gpt-4o-mini")
        s = ApiGuideActions.validateStep1(s)
        assertTrue(s.canTest)
        s = ApiGuideActions.beginTest(s)
        assertTrue(s.testing)
        s = ApiGuideActions.onTestResult(
            s,
            ApiGuideState.ConnectivityUi(success = true, latencyMs = 230),
            AiCapabilities(jsonObject = true, stream = true),
        )
        assertFalse(s.testing)
        assertEquals(230, s.connectivity?.latencyMs)
        assertTrue(s.canSave)
        s = ApiGuideActions.finish(s)
        assertEquals(ApiGuideState.Step.DONE, s.step)
    }

    @Test
    fun failedTestBlocksSave() {
        var s = ApiGuideActions.beginTest(ApiGuideState())
        s = ApiGuideActions.onTestResult(s, ApiGuideState.ConnectivityUi(success = false, error = AiError.AUTH_INVALID), null)
        assertFalse(s.canSave)                     // D.3：测试失败主按钮"返回修改"，不给完成
        assertEquals(AiError.AUTH_INVALID, s.connectivity?.error)
    }

    @Test
    fun capabilitiesDoNotBlockSave() {
        // D.2.4：能力位不阻断保存（PRD §3.2.6 降级链）
        var s = ApiGuideState(modelInput = "gpt-4o-mini")   // 已填模型（隔离"能力不阻断保存"的断言）
        s = ApiGuideActions.beginTest(s)
        s = ApiGuideActions.onTestResult(
            s,
            ApiGuideState.ConnectivityUi(success = true),
            AiCapabilities(jsonObject = false, stream = true),   // json_object 不支持但连接成功
        )
        assertTrue(s.canSave)
    }
}
