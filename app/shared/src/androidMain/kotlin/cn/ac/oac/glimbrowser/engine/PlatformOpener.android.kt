package cn.ac.oac.glimbrowser.engine

import android.content.Intent
import android.net.Uri

actual fun openExternally(url: String): Boolean = runCatching {
    val ctx = cn.ac.oac.glimbrowser.securekey.appContextForOpen
        ?: return@runCatching false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
    true
}.getOrDefault(false)
