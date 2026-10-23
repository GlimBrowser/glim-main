package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Web(JS)：内存占位（IndexedDB/OPFS 持久化驱动后续；PRD §2.2.1 Web 非首发形态）。
 * 注意：内存驱动需先建表 —— 本 actual 建库即建表。
 */
actual fun createDatabaseDriver(): SqlDriver {
    // js 无内置 sqldelight 驱动在依赖中（Phase 6 未引入 sqljs），返回 NO-OP 会崩 → 用 error 占位？为保接口一致，
    // 这里抛明确错误：Web 形态数据层未启用（与 Phase 3 js PlatformEngineView 同类"非首发形态"诚实占位）。
    throw UnsupportedOperationException("网页版暂不支持本地数据存储")
}
