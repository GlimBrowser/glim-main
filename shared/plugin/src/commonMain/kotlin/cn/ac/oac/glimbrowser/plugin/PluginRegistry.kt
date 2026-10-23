package cn.ac.oac.glimbrowser.plugin

/**
 * 插件注册表（PRD V1.0 §3.4 + V1.1 §3.4 插件中心：安装/启停/卸载/吊销/订阅更新；纯 Kotlin 可单测）。
 *
 * 状态机（对齐 PRD §3.4 描述）：
 * ```
 * 发现(CDN/导入) → 安装(权限逐项授权) → 启用/停用(用户可关) → 订阅更新(指纹比对) ─┐
 *                        ↑                                                            │
 *                        └────────── 远程吊销(指纹黑名单命中 → revoked，禁执行) ←──────┘
 * ```
 * 红线：
 *  - 权限**安装时逐项授权**，运行期不静默扩权（PRD §3.4 + 000 §2）
 *  - 吊销即时生效、不回滚用户数据（PRD §10 风险表"脚本恶意化"对策）
 *  - 执行前必须同时满足：enabled && !revoked && 作用域匹配 && 全部 required 权限已授予
 */
class PluginRegistry {

    /** 已安装脚本（id → meta）。 */
    private val installed = LinkedHashMap<String, UserscriptMeta>()

    /** 远程吊销指纹黑名单（CDN 下发；PRD §3.4）。 */
    private val revokedFingerprints: MutableSet<String> = mutableSetOf()

    /** 已授予权限（id → granted set；逐项授权/可撤回，PRD §3.4）。 */
    private val grantedPermissions = HashMap<String, Set<Permission>>()

    fun snapshot(): List<UserscriptMeta> = installed.values.toList()

    fun get(id: String): UserscriptMeta? = installed[id]

    /**
     * 安装（导入/订阅）。**安装即要求显式授予的权限集合**（PRD §3.4 逐项授权；
     * 安装页把 [UserscriptMeta.permissions] 逐项展示后由用户勾选）。
     *
     * @param granted 用户实际勾选的权限（可为权限集子集 —— 未授权的权限运行时不可用）
     * @return 安装后的 meta（enabled=true 当且仅当 granted ⊇ meta.permissions 的 required 部分）
     */
    fun install(meta: UserscriptMeta, granted: Set<Permission>): UserscriptMeta {
        // 吊销指纹拒绝安装（PRD §3.4）
        val revoked = meta.fingerprint in revokedFingerprints
        val effective = meta.copy(
            enabled = !revoked && granted.containsAll(meta.permissions),
            revoked = revoked,
            installedAt = if (meta.installedAt != 0L) meta.installedAt else currentMillis(),
        )
        installed[meta.id] = effective
        grantedPermissions[meta.id] = granted
        return effective
    }

    /** 启停（PRD §3.4：用户可关闭每个脚本）。 */
    fun setEnabled(id: String, enabled: Boolean): UserscriptMeta? {
        val cur = installed[id] ?: return null
        // 已吊销的脚本不允许重新启用（PRD §3.4 吊销机制）
        if (cur.revoked) return cur
        val next = cur.copy(enabled = enabled)
        installed[id] = next
        return next
    }

    /** 卸载（移除本地安装与授权记录）。 */
    fun uninstall(id: String) {
        installed.remove(id)
        grantedPermissions.remove(id)
    }

    /** 撤销单个权限（PRD §3.4"授予后可关闭"）。 */
    fun revokePermission(id: String, permission: Permission): UserscriptMeta? {
        val cur = installed[id] ?: return null
        grantedPermissions[id] = (grantedPermissions[id] ?: emptySet()) - permission
        // 权限收缩后若脚本仍声称需要该权限 → 自动停用（防越权运行）
        val stillGranted = grantedPermissions.getValue(id)
        // 权限收缩 → 若剩余权限不足以覆盖脚本声明的所需权限，自动停用（防越权继续运行，PRD §3.4）
        val next = cur.copy(enabled = cur.enabled && stillGranted.containsAll(cur.permissions))
        installed[id] = next
        return next
    }

    /** 远程吊销批量下发（CDN 黑名单；命中即刻禁用并标记，PRD §3.4）。 */
    fun applyRevocation(fingerprints: Collection<String>): List<UserscriptMeta> {
        revokedFingerprints.addAll(fingerprints)
        val changed = ArrayList<UserscriptMeta>()
        for (id in installed.keys.toList()) {
            val cur = installed[id] ?: continue
            if (cur.fingerprint.isNotEmpty() && cur.fingerprint in revokedFingerprints && !cur.revoked) {
                val next = cur.copy(revoked = true, enabled = false)
                installed[id] = next
                changed.add(next)
            }
        }
        return changed
    }

    /**
     * 运行许可判定（执行沙箱调用；全部条件必须满足，PRD §3.4 + §10）。
     */
    fun mayExecute(meta: UserscriptMeta, host: String): Boolean =
        meta.enabled && !meta.revoked &&
            ScopeMatcher.matchesAny(meta.matches, host) &&
            grantedPermissions[meta.id].orEmpty().containsAll(meta.permissions) &&
            meta.fingerprint !in revokedFingerprints

    /** 已授予权限快照（授权页展示）。 */
    fun grantedPermissions(id: String): Set<Permission> = grantedPermissions[id] ?: emptySet()

    private var currentMillis: () -> Long = { 0L }
    fun setClock(clock: () -> Long) { currentMillis = clock }
}
