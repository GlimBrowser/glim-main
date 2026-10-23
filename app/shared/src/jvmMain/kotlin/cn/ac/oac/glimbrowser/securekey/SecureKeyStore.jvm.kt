package cn.ac.oac.glimbrowser.securekey

/**
 * 桌面(jvm)密钥保险库 —— 占位（PRD 附件 E D1：Windows=DPAPI、macOS=Keychain 经 JNI 批接入）。
 * 上线门槛（107 §4）：桌面安全区未接入前 [describeBackend] **如实标注非安全存储**（红线7/§3.6.2 不虚报）。
 */
actual fun createSecureKeyStore(): SecureKeyStore = FallbackInMemory
