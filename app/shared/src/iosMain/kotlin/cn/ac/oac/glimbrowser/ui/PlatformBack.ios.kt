package cn.ac.oac.glimbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

/** iOS：CMP ui-backhandler（配合 iosMain 的边缘滑动手势双通道，108）。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
