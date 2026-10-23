package cn.ac.oac.glimbrowser

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.ac.oac.glimbrowser.logic.BrowserController
import cn.ac.oac.glimbrowser.ui.screen.BrowserShell
import cn.ac.oac.glimbrowser.ui.screen.HomeClock
import cn.ac.oac.glimbrowser.ui.theme.GlimTheme

/**
 * GlimBrowser 根组件（Phase 2：浏览核心 UI 冒烟壳）。
 *
 * 结构：GlimTheme（昼/夜令牌） → BrowserController（状态持有） → BoxWithConstraints 实测宽度 dp
 *      → BrowserShell（按 deriveAdaptiveLayout 渲染 Compact/Expanded，PRD V1.3 §3.5.1）。
 *
 * 时钟为占位固定值；Phase 3 接平台时钟与真实容器。
 */
@Composable
fun App() {
    // 主题模式来自真实状态（护眼/夜间可切换并持久化）；未初始化前跟随系统
    var themeMode by remember { mutableStateOf<cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode>(cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.SYSTEM) }
    GlimTheme(themeMode = themeMode) {
        // 本地仓库（真持久化；Web 等未接驱动 → 降级 null，不阻断 UI）
        val repository = remember { runCatching { cn.ac.oac.glimbrowser.data.openRepository() }.getOrNull() }
        // 平台安全区（红线7；未初始化时降级内存并如实标注）
        val keyStore = remember {
            runCatching { cn.ac.oac.glimbrowser.securekey.createSecureKeyStore() }
                .getOrElse { cn.ac.oac.glimbrowser.securekey.FallbackInMemory }
        }
        val controller = remember { BrowserController(repository = repository, keyStore = keyStore) }
        // AI 运行时（BYOK 直连；真实 HttpClient 引擎注入）
        val aiRuntime = remember { cn.ac.oac.glimbrowser.ai.AiRuntime(keyStore) }
        // Phase 3：注入平台引擎工厂（Android WebView / iOS WKWebView 真实内核；桌面等平台 null → 诚实占位）
        CompositionLocalProvider(cn.ac.oac.glimbrowser.engine.localBrowserEngineFactory provides cn.ac.oac.glimbrowser.engine.PlatformEngineFactory()) {
        // 去广告执行层注入（PRD §3.3.5：Android 请求级 / iOS 内容级消费同一引擎）
        CompositionLocalProvider(cn.ac.oac.glimbrowser.engine.localAdFilter provides controller.state.adFilter) {
        CompositionLocalProvider(
            cn.ac.oac.glimbrowser.ai.localAiRuntime provides aiRuntime,
            cn.ac.oac.glimbrowser.ai.localRepository provides repository,
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // 安全区：避免状态栏/底部手势条与界面元素重叠（edge-to-edge 仍保留背景延伸）
                .safeDrawingPadding(),
        ) {
            // 状态里的主题模式同步到主题（真实持久化恢复后生效）
            LaunchedEffect(controller.state.themeMode) { themeMode = controller.state.themeMode }
            // 系统栏图标明暗跟随主题（edge-to-edge 必须）
            LaunchedEffect(themeMode) {
                val dark = when (themeMode) {
                    cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.DARK -> true
                    cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.LIGHT,
                    cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.EYE_CARE -> false
                    cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.SYSTEM ->
                        cn.ac.oac.glimbrowser.ui.theme.ThemeState.isDark
                }
                cn.ac.oac.glimbrowser.platform.applySystemBarTheme(dark)
            }
            val widthDp = maxWidth.value.toInt()  // BoxWithConstraintsScope 直接暴露 maxWidth: Dp，转 Int 供纯逻辑推导
            BrowserShell(
                state = controller.state,
                dispatch = controller::dispatch,
                widthDp = widthDp,
                now = run {  // 真实时间（108：去静态占位）
                    val (t, d) = cn.ac.oac.glimbrowser.platform.platformNowText()
                    HomeClock(time = t, date = d)
                },
            )
        }
        }
        }
        }
    }
}
