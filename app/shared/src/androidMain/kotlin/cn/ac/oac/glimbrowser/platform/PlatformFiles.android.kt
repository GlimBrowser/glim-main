package cn.ac.oac.glimbrowser.platform

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import cn.ac.oac.glimbrowser.securekey.appContextForOpen
import java.io.File

actual fun saveTextFile(fileName: String, content: String): String? {
    val ctx = appContextForOpen ?: return null
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            "下载/$fileName"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
            val f = File(dir, fileName)
            f.writeText(content)
            f.absolutePath
        }
    }.getOrNull()
}
