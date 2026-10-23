package cn.ac.oac.glimbrowser.securekey

/** Web(WasmJs) —— 同 JS 占位（非首发形态，PRD §2.2.1）。 */
actual fun createSecureKeyStore(): SecureKeyStore = FallbackInMemory
