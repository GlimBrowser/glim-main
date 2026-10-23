package cn.ac.oac.glimbrowser.securekey

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android 密钥保险库（000 §2 红线7：Android KeyStore → androidx.security **加密偏好**
 * （MasterKey 经 Android Keystore 硬件加密），PRD V1.2 §2.4.2 KeyStore 模式）。
 *
 * 由 androidApp 启动时 [initSecureKeyStore](context) 注入（与 DriverFactory 同模式）。
 * 上下文未注入时回退内存实现并在 [describeBackend] **如实标注非安全存储**（PRD §3.6.2 不虚报）。
 */
class AndroidKeyStore private constructor(private val prefs: SharedPreferences) : SecureKeyStore {

    override fun put(ref: String, plaintext: String): Boolean =
        prefs.edit().putString(ref, plaintext).commit()

    override fun get(ref: String): String? = prefs.getString(ref, null)

    override fun contains(ref: String): Boolean = prefs.contains(ref)

    override fun remove(ref: String) {
        prefs.edit().remove(ref).apply()
    }

    override fun describeBackend(): String = "AndroidKeyStore/EncryptedSharedPreferences（安全存储 OK）"

    companion object {
        private var cachedContext: Context? = null

        fun init(context: Context) {
            cachedContext = context.applicationContext
        }

        internal fun createOrNull(): AndroidKeyStore? {
            val ctx = cachedContext ?: return null
            return runCatching {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = EncryptedSharedPreferences.create(
                    ctx,
                    "glim_secure_keys",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                AndroidKeyStore(prefs)
            }.getOrNull()
        }
    }
}

/** 供系统级操作（打开链接等）使用的应用上下文。 */
var appContextForOpen: Context? = null

/** androidApp Application.onCreate 调用。 */
fun initSecureKeyStore(context: Context) {
    AndroidKeyStore.init(context)
    appContextForOpen = context.applicationContext
}

actual fun createSecureKeyStore(): SecureKeyStore =
    AndroidKeyStore.createOrNull() ?: FallbackInMemory
