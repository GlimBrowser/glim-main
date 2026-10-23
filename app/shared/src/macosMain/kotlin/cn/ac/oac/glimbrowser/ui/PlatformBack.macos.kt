package cn.ac.oac.glimbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

/** macOS：CMP ui-backhandler（触控板边缘左滑/快捷键由平台归一；108）。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
