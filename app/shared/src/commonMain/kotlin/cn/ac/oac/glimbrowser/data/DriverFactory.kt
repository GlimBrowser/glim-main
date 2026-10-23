package cn.ac.oac.glimbrowser.data

import app.cash.sqldelight.db.SqlDriver

/**
 * 平台数据库驱动工厂（PRD V1.2 §2.4.2 平台接口模式；附录 B：SQLDelight 六端同构 schema）。
 * 各端 actual：
 *  - Android → AndroidSqliteDriver（系统 SQLite）
 *  - iOS/macOS → NativeSqliteDriver
 *  - Desktop(jvm) → JdbcSqliteDriver 文件库（~/.glimbrowser/glimbrowser.db）
 *  - Web → 未启用（PRD §2.2.1 Web 非首发形态；持久化留 web 专项）—— 诚实占位
 */
expect fun createDatabaseDriver(): SqlDriver

/** 建库（schema 由各端工厂按需创建一次）。 */
fun openRepository(): GlimRepository = GlimRepository(createDatabaseDriver())
