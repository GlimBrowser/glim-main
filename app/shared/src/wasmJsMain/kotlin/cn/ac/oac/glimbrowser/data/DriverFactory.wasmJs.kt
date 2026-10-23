package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver

/** Web(WasmJs)：同 JS —— 未启用（PRD §2.2.1 非首发形态）。 */
actual fun createDatabaseDriver(): SqlDriver {
    throw UnsupportedOperationException("网页版暂不支持本地数据存储")
}
