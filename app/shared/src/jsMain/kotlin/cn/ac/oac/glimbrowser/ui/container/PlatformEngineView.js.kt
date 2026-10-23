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
 * Web(JS) actual —— Phase 3 占位。PRD §2.2.1 首发六形态不含 Web（网页里嵌 iframe 属 Phase 6 web 专项）。
 */
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "网页版暂不支持浏览页面\n\n请在 Android 或 iOS 客户端使用\n\n地址：${engine.currentUrl() ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
