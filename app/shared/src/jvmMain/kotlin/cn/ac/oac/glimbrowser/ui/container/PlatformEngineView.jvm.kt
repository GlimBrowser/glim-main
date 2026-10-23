package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.engine.BrowserEngine

/**
 * 桌面（JVM/CMP Desktop）actual —— Phase 3 占位。
 *
 * PRD V1.3 §2.2.1 + 附录 B：桌面容器 = macOS WKWebView / Windows WebView2（JNI/JNA 桥接，接口封装在
 * 平台层）。CMP Desktop 无内置网页视图，需引入 JCEF/JavaFX WebView 等额外依赖 —— 属 PRD V1.3 §9
 * 「1.1 桌面 Alpha」（D1 迭代）范围，Phase 3 不在本模块引入重量级依赖。
 *
 * 当前诚实占位：渲染目标 URL + 明确标注"桌面容器在 Phase 6 桌面专项接入"，不假装能上网。
 * （与 common 的 NotConnectedPlaceholder 区分：这里是"引擎已注入但渲染层未实现"的提示。）
 */
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "桌面版网页渲染尚未开放\n\n请先在 Android 或 iOS 上使用完整浏览功能。\n\n当前地址：${engine.currentUrl() ?: "尚未打开页面"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
