package cn.ac.oac.glimbrowser.platform

import java.io.File

actual fun saveTextFile(fileName: String, content: String): String? = runCatching {
    val dir = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
    val f = File(dir, fileName)
    f.writeText(content)
    f.absolutePath
}.getOrNull()
