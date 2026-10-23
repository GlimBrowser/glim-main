package cn.ac.oac.glimbrowser.securekey

/**
 * 密钥保险库门面（PRD V1.2 §2.4.2 SecureKeyStore；V1.1 §3.2.1 密钥安全存储 P0；000 §2 红线 7）。
 *
 * 红线（000 §2）：
 *  - **API Key 仅入平台安全存储**（Android KeyStore/加密偏好、iOS/macOS Keychain、Windows DPAPI——本工程桌面 jvm 用系统 Keychain(JNI)/文件加密占位）
 *  - 默认掩码展示、查看需确认（附件 D.2.1 交互，展示层职责）
 *  - **不入日志、不入埋点、不入 DB 明文**（shared:data 只存 [secureRef] 引用）
 *
 * 设计（PRD §2.4.2"密钥存储：expect/actual → KeyStore / Keychain"）：
 *  - 本接口只暴露"存/取/删/存在"四操作 + 引用命名空间，**明文 Key 生命周期被压到调用栈内**（取用即用，不驻留静态字段）
 *  - 平台 actual 决定加密落地方式；jvm/desktop 占位实现明示限制（DPAPI/Keychain 属 D1 平台批）
 */
interface SecureKeyStore {

    /**
     * 写入/覆盖一条密钥。
     * @param ref 引用名（如 "ai:default"；[ai_profiles.api_key_ref] 落库即此值）
     * @param plaintext 明文（仅本调用期间存在；实现必须立即加密落地，不得缓存到字段/静态）
     * @return 是否成功
     */
    fun put(ref: String, plaintext: String): Boolean

    /**
     * 读取明文（调用方即时消费；**禁止**传入日志/序列化/埋点，000 §2 红线 7/8）。
     * @return 明文或 null（不存在/未解锁）
     */
    fun get(ref: String): String?

    /** 是否存在（不读明文）。 */
    fun contains(ref: String): Boolean

    /** 删除（撤销授权/退出配置时，附件 D 清除 Key）。 */
    fun remove(ref: String)

    /** 平台能力自述（诊断页"安全存储状态"，如实展示，PRD §3.6.2 不虚报）。 */
    fun describeBackend(): String

    companion object {
        /** AI 档案默认引用前缀（引导 DONE 写入 `ai:default`）。 */
        const val AI_PROFILE_REF_PREFIX = "ai:"
        fun aiProfileRef(profileId: String): String = AI_PROFILE_REF_PREFIX + profileId
    }
}

/**
 * 进程内兜底实现（无平台安全区时的降级，**仅测试/开发**）：
 * 平台 actual 优先；此类在文档中标注"非安全存储"，诊断页按 [describeBackend] 如实展示。
 * 用途：commonTest/桌面 D1 前的临时可用（PRD 上线门槛要求平台真安全区，见 107 §4）。
 */
class InMemoryKeyStore : SecureKeyStore {
    private val map = HashMap<String, String>()
    override fun put(ref: String, plaintext: String): Boolean {
        map[ref] = plaintext
        return true
    }
    override fun get(ref: String): String? = map[ref]
    override fun contains(ref: String): Boolean = map.containsKey(ref)
    override fun remove(ref: String) { map.remove(ref) }
    override fun describeBackend(): String = "in-memory（非安全存储，仅测试）"
}

/** 平台安全库工厂（expect/actual：iOS/macOS=Keychain、Android=KeyStore 加密、JVM=占位/D1 DPAPI）。 */
expect fun createSecureKeyStore(): SecureKeyStore


/** 共享的降级实现（describeBackend 明示非安全存储 —— PRD §3.6.2 不虚报；上线门槛见 107 §4）。 */
object FallbackInMemory : SecureKeyStore {
    private val map = HashMap<String, String>()
    override fun put(ref: String, plaintext: String): Boolean { map[ref] = plaintext; return true }
    override fun get(ref: String): String? = map[ref]
    override fun contains(ref: String): Boolean = map.containsKey(ref)
    override fun remove(ref: String) { map.remove(ref) }
    override fun describeBackend(): String = "in-memory（非安全存储 ⚠️，平台安全区未接入）"
}
