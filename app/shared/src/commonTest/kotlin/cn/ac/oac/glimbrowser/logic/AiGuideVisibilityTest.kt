package cn.ac.oac.glimbrowser.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AI 配置入口必须真正打开引导（WELCOME 也要可见）。 */
class AiGuideVisibilityTest {

    @Test
    fun aiEntryOpensGuideWhenUnconfigured() {
        val c = BrowserController()
        assertFalse(c.state.aiConfigured)
        c.dispatch(BrowserController.Event.AiEntry)
        assertTrue(c.state.apiGuideVisible)
        assertTrue(c.state.apiGuide.step == ApiGuideState.Step.WELCOME)
    }

    @Test
    fun closeGuideHidesOverlay() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.AiEntry)
        c.dispatch(BrowserController.Event.CloseApiGuide)
        assertFalse(c.state.apiGuideVisible)
    }

    @Test
    fun configuredEntryOpensDrawerNotGuide() {
        val c = BrowserController()
        // 直接走引导完成流程，置为已配置
        c.dispatch(BrowserController.Event.AiEntry)
        c.dispatch(BrowserController.Event.ApiGuide(cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.NextWelcome))
        listOf(
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.BaseUrlChanged("https://api.example.com/v1"),
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.ApiKeyChanged("sk-x"),
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.ModelChanged("gpt-4o-mini"),
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.ValidateStep1,
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.TestResult(ApiGuideState.ConnectivityUi(success = true)),
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.NextStep2,
            cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent.Finish,
        ).forEach { c.dispatch(BrowserController.Event.ApiGuide(it)) }
        assertTrue(c.state.aiConfigured)
        c.dispatch(BrowserController.Event.CloseApiGuide)
        c.dispatch(BrowserController.Event.AiEntry)
        assertTrue(c.state.aiDrawerOpen)
        assertFalse(c.state.apiGuideVisible)
    }
}
