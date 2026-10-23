package cn.ac.oac.glimbrowser.ai

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cn.ac.oac.glimbrowser.data.GlimRepository

/** AI 运行时（null=未初始化/Web 等非首发形态）。 */
val localAiRuntime: ProvidableCompositionLocal<AiRuntime?> = staticCompositionLocalOf { null }

/** 本地仓库（档案/历史/书签；null=无持久化）。 */
val localRepository: ProvidableCompositionLocal<GlimRepository?> = staticCompositionLocalOf { null }
