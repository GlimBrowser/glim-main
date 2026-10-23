package cn.ac.oac.glimbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler

/** Android 系统返回手势/键（CMP ui-backhandler）。 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
