package cn.ac.oac.glimbrowser.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun saveTextFile(fileName: String, content: String): String? {
    val dir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    val path = "$dir/$fileName"
    val ok = (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    return if (ok) path else null
}
