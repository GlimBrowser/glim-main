package cn.ac.oac.glimbrowser.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Android：系统 SQLite（PRD §5.2 本地数据；Context 注入见 PlatformDatabase 初始化）。 */
actual fun createDatabaseDriver(): SqlDriver {
    val ctx = requireNotNull(databaseContext) {
        "调用方须先调用 initAndroidDatabaseContext(context)（androidApp 启动时）"
    }
    return AndroidSqliteDriver(GlimBrowserDatabase.Schema, ctx, "glimbrowser.db")
}

/** 平台上下文注入点（androidApp Application/MainActivity 设置；避免 shared 依赖具体 Activity）。 */
var databaseContext: Context? = null

/** androidApp 启动时调用。 */
fun initAndroidDatabaseContext(context: Context) {
    databaseContext = context.applicationContext
}
