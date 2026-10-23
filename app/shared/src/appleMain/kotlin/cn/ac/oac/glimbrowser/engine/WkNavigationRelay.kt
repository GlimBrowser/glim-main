package cn.ac.oac.glimbrowser.engine

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import platform.darwin.NSObject
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView

/**
 * WKNavigationDelegate 桥（iOS/macOS 共用）—— 把 WKWebView 导航事件转发给 [WkWebViewEngine]。
 *
 * 实现方式（K/N ObjC 互操作约束，实测踩坑记录）：
 *  - 继承 [platform.darwin.NSObject]（K/N 2.4 的 NSObject **不在** platform.Foundation/platform.objc，
 *    而在 platform.darwin —— 与 NSObjectProtocol 同包，编译器报"Unresolved NSObject"时按此定位）；
 *  - 继承 NSObject 即自带 NSObjectProtocol 全部默认实现（含 conformsToProtocol 的
 *    objcnames.classes.Protocol 前向声明类型 —— 该类型用户代码无法 import，直接继承绕开）；
 *  - ObjC 签名冲突（多个 webView(_:didXNavigation:) 同 JVM 签名）的成员全部标 [ObjCSignatureOverride]。
 *
 * 对应 PRD V1.2 §2.4.2：平台层只做"事件桥接"，不含业务判断。
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class WkNavigationRelay(
    private val engine: WkWebViewEngine,
) : NSObject(), WKNavigationDelegateProtocol {

    // ===== WKNavigationDelegate 导航事件（冲突组：全部标 @ObjCSignatureOverride） =====

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        val url = webView.URL?.absoluteString ?: ""
        engine.handle(WkWebViewEngine.Event.Progress(url, 0f))
        engine.handle(WkWebViewEngine.Event.State(url, webView.title, webView.canGoBack, webView.canGoForward))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val url = webView.URL?.absoluteString ?: ""
        engine.handle(WkWebViewEngine.Event.Progress(url, 1f))
        engine.handle(WkWebViewEngine.Event.Finished(url, webView.title))
        engine.handle(WkWebViewEngine.Event.State(url, webView.title, webView.canGoBack, webView.canGoForward))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
        engine.handle(WkWebViewEngine.Event.Title(webView.title))
    }

    /**
     * iOS 边缘右滑返回手势目标（108 用户反馈"适配手势"）：UIScreenEdgePanGestureRecognizer 的 action。
     * 平台层只执行"有历史则后退"（业务判断=canGoBack，PRD §2.3 平台只执行）。
     */
    @ObjCAction
    fun onEdgeSwipeBack() {
        val wv = engine.webView ?: return
        if (wv.canGoBack) wv.goBack()
    }

    /** 响应类型不可直接显示（zip/apk/附件）→ 取消导航，改用应用内下载队列。 */
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: platform.WebKit.WKNavigationResponse,
        decisionHandler: (platform.WebKit.WKNavigationResponsePolicy) -> Unit,
    ) {
        if (!decidePolicyForNavigationResponse.canShowMIMEType) {
            val url = decidePolicyForNavigationResponse.response.URL?.absoluteString
            decisionHandler(platform.WebKit.WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
            if (url != null) engine.download(url)
            return
        }
        decisionHandler(platform.WebKit.WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        // Phase 3：放行全部导航（Phase 4 拦截在此按规则决策，PRD §3.3.5）
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
    }
}