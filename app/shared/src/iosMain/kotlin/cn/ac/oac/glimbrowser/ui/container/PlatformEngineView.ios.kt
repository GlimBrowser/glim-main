package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.engine.WkNavigationRelay
import cn.ac.oac.glimbrowser.engine.WkWebViewEngine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS actual —— **真实 WKWebView 容器**（PRD V1.3 §2.2.1 iOS 首发平台；用户明确要求 iOS 不做占位）。
 *
 * 实现：
 *  - [UIKitView] 承载 [WKWebView]（CMP iOS 原生互操作，包 androidx.compose.ui.interop）
 *  - [WkNavigationRelay]（WKNavigationDelegate）把 didFinish/didStart/progress 事件回传
 *    [WkWebViewEngine.handle] → [BrowserEngine.Listener] → dispatch → 共享状态（PRD V1.2 §2.3 事件桥接）
 *  - ⏳ Phase 4：WKContentRuleList（PRD §3.3.5 iOS 内容级拦截）
 *  - ⏳ Phase 5：WKScriptMessageHandler JsBridge
 *
 * @param engine 必须是 [WkWebViewEngine]（common 由 localBrowserEngineFactory 注入）
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    val wkEngine = engine as? WkWebViewEngine
    if (wkEngine == null) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("网页视图初始化失败，请重试", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 每次进入创建 relay 并在 release 时断开（防泄漏）
    val relay = remember { WkNavigationRelay(wkEngine) }

    // 内容级拦截（PRD §3.3.5 iOS/macOS = WKContentRuleList；同源编译自 shared:adfilter，红线6）
    val adFilter = cn.ac.oac.glimbrowser.engine.localAdFilter.current

    UIKitView(
        factory = {
            // 池化复用：切回该标签时复用同一 WKWebView（保留滚动/历史/缓存/Cookie，108 修复）
            val reused = wkEngine.webView
            if (reused != null) {
                reused.removeFromSuperview()
                wkEngine.attach(reused)
                return@UIKitView reused
            }
            val config = WKWebViewConfiguration()
            // 无痕标签：ephemeral 数据存储（不落 Cookie/缓存，PRD §3.1.3）
            if (wkEngine.isPrivate) {
                config.websiteDataStore = platform.WebKit.WKWebsiteDataStore.nonPersistentDataStore()
            }
            WKWebView(frame = kotlinx.cinterop.cValue { }, configuration = config).apply {
                navigationDelegate = relay
                // 边缘右滑返回手势（108：iOS 原生导航手势；目标=relay.onEdgeSwipeBack 有历史才退）
                val edge = platform.UIKit.UIScreenEdgePanGestureRecognizer(target = relay, action = platform.Foundation.NSSelectorFromString("onEdgeSwipeBack"))
                edge.edges = 1UL   // UIRectEdge.left = 1（NS_OPTIONS 位掩码；K/N 未导出 UIRectEdgeLeft 常量，按 UIKit 值）
                addGestureRecognizer(edge)
                wkEngine.attach(this)
                // WKContentRuleList 编译注入（异步编译+移除旧规则；页面加载前生效依赖 async 完成，Phase 6 完整时序留真机验证）
                if (adFilter != null) {
                    installContentRuleList(this, adFilter.compileContentRuleListJson())
                }
            }
        },
        modifier = modifier,
        update = { _ -> },
        onRelease = { wv ->
            wv.navigationDelegate = null
            wkEngine.detach()
        },
    )
}

/**
 * 安装 WKContentRuleList（PRD §3.3.5 内容级拦截；与 shared:adfilter 同源 JSON，红线6）。
 * 异步编译（WKContentRuleListStore）→ 成功后清旧规则并加到 userContentController。
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun installContentRuleList(webView: WKWebView, json: String) {
    val store = platform.WebKit.WKContentRuleListStore.defaultStore() ?: return
    store.compileContentRuleListForIdentifier("glimbase", encodedContentRuleList = json) { ruleList, error ->
        if (ruleList != null) {
            val ucc = webView.configuration.userContentController
            ucc.removeAllContentRuleLists()
            ucc.addContentRuleList(ruleList)
        }
        // 失败（error）→ 不阻断浏览；规则失效如实留白（PRD §3.6.2 不虚报；真机验证项 108 遗留）
    }
}
