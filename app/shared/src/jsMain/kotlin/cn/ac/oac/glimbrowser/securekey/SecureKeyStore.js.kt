package cn.ac.oac.glimbrowser.securekey

/**
 * Web(JS) —— 非首发形态（PRD §2.2.1）。WebCrypto+IndexedDB 方案后续；
 * 当前为内存降级并如实标注（与 Phase 6 DriverFactory 同类诚实占位，但 Key 接口保持可用避免崩链）。
 */
actual fun createSecureKeyStore(): SecureKeyStore = FallbackInMemory
