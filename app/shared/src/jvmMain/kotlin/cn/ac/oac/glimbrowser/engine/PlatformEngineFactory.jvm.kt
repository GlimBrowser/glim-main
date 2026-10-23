package cn.ac.oac.glimbrowser.engine

/** 桌面 JVM：容器渲染层属 PRD §9 桌面 Alpha（Phase 6 · WBS D1）——当前不注入引擎（诚实占位路径）。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = null
