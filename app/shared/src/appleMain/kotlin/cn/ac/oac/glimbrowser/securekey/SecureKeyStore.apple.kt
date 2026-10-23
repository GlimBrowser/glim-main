package cn.ac.oac.glimbrowser.securekey

import platform.CoreFoundation.CFTypeRefVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert

import platform.Foundation.create
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef


import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * iOS/macOS 密钥保险库 —— **Keychain 真实实现**（000 §2 红线7；PRD V1.2 §2.4.2 Keychain；
 * PRD V1.3 §2.2.1 iOS/macOS 同源 → appleMain 共享）。
 *
 * 条目：kSecClassGenericPassword + service=cn.ac.oac.glimbrowser + account=ref。
 * 红线：明文仅在 put/get 调用栈内；[describeBackend] 如实标注后端（PRD §3.6.2 不虚报）。
 */
@OptIn(ExperimentalForeignApi::class)
object AppleKeyStore : SecureKeyStore {

    private const val SERVICE = "cn.ac.oac.glimbrowser"

    private fun cf(str: String): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, str, kCFStringEncodingUTF8)

    /** 构造 class/service/account 查询字典；调用方负责 CFRelease。 */
    private fun baseQuery(ref: String): platform.CoreFoundation.CFMutableDictionaryRef? {
        val q = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null) ?: return null
        CFDictionarySetValue(q, kSecClass, kSecClassGenericPassword)
        cf(SERVICE)?.let { CFDictionarySetValue(q, kSecAttrService, it) }
        cf(ref)?.let { CFDictionarySetValue(q, kSecAttrAccount, it) }
        return q
    }

    override fun put(ref: String, plaintext: String): Boolean {
        remove(ref)   // 覆盖语义
        val bytes = plaintext.encodeToByteArray()
        val nsData: platform.Foundation.NSData = bytes.usePinned { pinned ->
            // NSData.create(bytes: CPointer<ByteVar>, length) — klib 导出的 ObjC 工厂（ByteArray 地址桥接）
            platform.Foundation.NSData.create(pinned.addressOf(0), bytes.size.convert())
        } ?: return false
        val ok = memScoped {
            val q = baseQuery(ref) ?: return@memScoped false
            @Suppress("UNCHECKED_CAST")
            CFDictionarySetValue(q, kSecValueData, nsData as platform.CoreFoundation.CFTypeRef)
            val st = SecItemAdd(q, null)
            CFRelease(q)
            st.toInt() == errSecSuccess
        }
        return ok
    }

    override fun get(ref: String): String? {
        val cfData = memScoped {
            val q = baseQuery(ref) ?: return@memScoped null
            CFDictionarySetValue(q, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionarySetValue(q, kSecReturnData, kCFBooleanTrue ?: error("kCFBooleanTrue"))
            val outRef = alloc<CFTypeRefVar>()
            outRef.value = null
            val st = SecItemCopyMatching(q, outRef.ptr)
            val v = outRef.value
            CFRelease(q)
            if (st.toInt() != errSecSuccess) null else v
        } ?: return null
        // CFTypeRef -> CFDataRef 指针强转（CPointer 泛型擦除；SecItemCopyMatching kSecReturnData 返回的就是 CFData）
        @Suppress("UNCHECKED_CAST")
        val cf = requireNotNull(cfData) as platform.CoreFoundation.CFDataRef
        val len = CFDataGetLength(cf).toInt()
        if (len <= 0) return ""
        val src = CFDataGetBytePtr(cf) ?: return null
        val out = ByteArray(len)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, len.convert())
        }
        return out.decodeToString()
    }

    override fun contains(ref: String): Boolean = get(ref) != null

    override fun remove(ref: String) {
        val q = baseQuery(ref) ?: return
        SecItemDelete(q)
        CFRelease(q)
    }

    override fun describeBackend(): String = "Apple Keychain kSecClassGenericPassword（安全存储 OK）"
}

/** iOS/macOS：Keychain 真实现（appleMain 同源，PRD V1.3 §2.2.1）。 */
actual fun createSecureKeyStore(): SecureKeyStore = AppleKeyStore
