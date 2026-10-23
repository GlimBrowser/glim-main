package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** 桌面(jvm)：文件数据库（用户数据目录，PRD §5.2 本地数据）。 */
actual fun createDatabaseDriver(): SqlDriver {
    val dir = File(System.getProperty("user.home"), ".glimbrowser").apply { mkdirs() }
    val dbFile = File(dir, "glimbrowser.db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())
    if (!dbFile.exists() || dbFile.length() == 0L) {
        GlimBrowserDatabase.Schema.create(driver)
    }
    return driver
}
