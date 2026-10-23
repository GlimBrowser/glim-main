package cn.ac.oac.glimbrowser.platform

import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun saveTextFile(fileName: String, content: String): String? {
    val path = NSHomeDirectory() + "/Downloads/" + fileName
    val ok = (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    return if (ok) path else null
}
