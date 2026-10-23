package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.network.AiCapabilities
import cn.ac.oac.glimbrowser.network.AiError
import cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent
import cn.ac.oac.glimbrowser.ui.screen.applyTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 三步引导：校验 → 测试中 → 结果回填 → 能力确认 → 完成。 */
class ApiGuideActionsTest {

    private fun filled() = ApiGuideState(
        step = ApiGuideState.Step.STEP1,
        baseUrlInput = "https://api.example.com/v1",
        apiKeyInput = "sk-test",
        modelInput = "gpt-4o-mini",
    )

    @Test
    fun validateMovesToStep2() {
        val s = ApiGuideEvent.ValidateStep1.applyTo(filled())
        assertEquals(ApiGuideState.Step.STEP2, s.step)
    }

    @Test
    fun rejectsHttpEndpoint() {
        val s = ApiGuideEvent.ValidateStep1.applyTo(filled().copy(baseUrlInput = "http://api.example.com"))
        assertEquals(ApiGuideState.Step.STEP1, s.step)
        assertNotNull(s.validationError)
    }

    @Test
    fun testResultSuccessThenProceedToStep3AndDone() {
        var s = ApiGuideEvent.TestConnection.applyTo(filled())
        assertTrue(s.testing)
        s = ApiGuideEvent.TestResult(
            ApiGuideState.ConnectivityUi(success = true, latencyMs = 230, modelEcho = "gpt-4o-mini"),
            AiCapabilities(jsonObject = true, stream = true),
        ).applyTo(s)
        assertFalse(s.testing)
        assertTrue(s.canSave)
        s = ApiGuideEvent.NextStep2.applyTo(s)
        assertEquals(ApiGuideState.Step.STEP3, s.step)
        s = ApiGuideEvent.Finish.applyTo(s)
        assertEquals(ApiGuideState.Step.DONE, s.step)
    }

    @Test
    fun editingInputClearsPreviousResult() {
        var s = ApiGuideEvent.TestResult(
            ApiGuideState.ConnectivityUi(success = true, latencyMs = 100),
        ).applyTo(ApiGuideEvent.TestConnection.applyTo(filled()))
        assertNotNull(s.connectivity)
        s = ApiGuideEvent.BaseUrlChanged("https://other.example.com/v1").applyTo(s)
        assertEquals(null, s.connectivity)
        assertEquals(null, s.capabilities)
    }

    @Test
    fun revalidatingResetsToFreshTest() {
        var s = ApiGuideEvent.TestResult(
            ApiGuideState.ConnectivityUi(success = true, latencyMs = 100),
            AiCapabilities(jsonObject = true, stream = true),
        ).applyTo(ApiGuideEvent.TestConnection.applyTo(filled()))
        s = ApiGuideEvent.ValidateStep1.applyTo(s)
        assertEquals(null, s.connectivity)
        assertEquals(ApiGuideState.Step.STEP2, s.step)
    }

    @Test
    fun skipTestGoesToStep3AndAllowsSave() {
        val s = ApiGuideEvent.SkipTest.applyTo(filled())
        assertEquals(ApiGuideState.Step.STEP3, s.step)
        assertTrue(s.skippedTest)
        assertTrue(s.canSave)
        assertEquals(ApiGuideState.Step.DONE, ApiGuideEvent.Finish.applyTo(s).step)
    }

    @Test
    fun skipTestStillRequiresFields() {
        val s = ApiGuideEvent.SkipTest.applyTo(filled().copy(apiKeyInput = ""))
        assertEquals(ApiGuideState.Step.STEP1, s.step)
        assertNotNull(s.validationError)
        assertFalse(s.canSave)
    }

    @Test
    fun modelsLoadAndSelect() {
        var s = ApiGuideEvent.FetchModels.applyTo(filled())
        assertTrue(s.modelsLoading)
        s = ApiGuideEvent.ModelsResult(listOf("gpt-4o-mini", "gpt-4o")).applyTo(s)
        assertEquals(2, s.models.size)
        assertFalse(s.modelsLoading)
        val picked = ApiGuideEvent.ModelChanged(s.models.last()).applyTo(s)
        assertEquals("gpt-4o", picked.modelInput)
    }

    @Test
    fun modelsErrorKeepsManualInputUsable() {
        val s = ApiGuideEvent.ModelsResult(emptyList(), "端点不支持 /models").applyTo(
            ApiGuideEvent.FetchModels.applyTo(filled()),
        )
        assertEquals("端点不支持 /models", s.modelsError)
        assertTrue(s.models.isEmpty())
        assertTrue(s.modelInput.isNotBlank())   // 手输不受影响
    }

    @Test
    fun testResultFailureBlocksNextStep() {
        var s = ApiGuideEvent.TestConnection.applyTo(filled())
        s = ApiGuideEvent.TestResult(
            ApiGuideState.ConnectivityUi(success = false, error = AiError.AUTH_INVALID),
        ).applyTo(s)
        assertFalse(s.canSave)
        val after = ApiGuideEvent.NextStep2.applyTo(s)
        assertEquals(ApiGuideState.Step.STEP2, after.step)   // 失败不允许进入能力确认
        // 返回修改可回到 STEP1
        assertEquals(ApiGuideState.Step.STEP1, ApiGuideEvent.Back.applyTo(s).step)
    }
}
