package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** iOS/macOS：NativeSqliteDriver（PRD 附录 B：六端同构 schema）。 */
actual fun createDatabaseDriver(): SqlDriver {
    val driver = NativeSqliteDriver(GlimBrowserDatabase.Schema, "glimbrowser.db")
    return driver
}
