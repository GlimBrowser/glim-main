package cn.ac.oac.glimbrowser.ui

import androidx.compose.runtime.Composable

/** 桌面 jvm：返回语义由快捷键（Alt+← / 106 ShortcutMap）承载；BackHandler no-op。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
