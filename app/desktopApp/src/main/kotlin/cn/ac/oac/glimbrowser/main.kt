package cn.ac.oac.glimbrowser

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.skia.Image
import java.io.File

fun main() {
    // 闪退诊断：把未捕获异常写入文件，便于用户回传（macOS: ~/GlimBrowser-crash.log）
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        runCatching {
            val log = File(System.getProperty("user.home"), "GlimBrowser-crash.log")
            log.appendText("\n=== ${java.time.LocalDateTime.now()} thread=${t.name} ===\n")
            log.appendText(e.stackTraceToString())
        }
        e.printStackTrace()
    }
    desktopMain()
}

private fun desktopMain() = application {
    // 窗口/任务栏图标（资源里打包了 icon.png；缺失时退回默认，不崩）
    val icon = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes()
                ?: File("src/main/resources/icon.png").takeIf { it.exists() }?.readBytes()
            bytes?.let { BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap()) }
        }.getOrNull()
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "GlimBrowser",
        icon = icon,
    ) {
        App()
    }
}