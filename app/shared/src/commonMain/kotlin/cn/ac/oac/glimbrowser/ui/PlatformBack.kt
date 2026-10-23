package cn.ac.oac.glimbrowser.ui

import androidx.compose.runtime.Composable

/**
 * 平台返回手势/按键桥接（108 用户反馈：返回/前进顺畅 + 适配手势）。
 *
 *  - Android：系统返回手势/键 → [onBack]（内核历史可退则退内核，否则退到首页/关闭浮层——由调用方语义决定）
 *  - iOS：CMP `ui-backhandler`（导航控制器边缘手势在无 nav 栈时由 CMP 归一为 BackHandler 触发；
 *    同时 PlatformEngineView 内挂 UIKit 边缘滑动手势直连 goBack()，见 iosMain）
 *  - 桌面：Alt+← 由 UI 层快捷键处理（106 ShortcutMap 归一化；此处 BackHandler 无桌面语义 → no-op）
 *  - js/wasm：no-op（浏览器自身有 history back）
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
