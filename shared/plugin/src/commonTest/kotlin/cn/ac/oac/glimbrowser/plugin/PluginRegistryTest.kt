package cn.ac.oac.glimbrowser.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 插件注册表单测（PRD §3.4 安装/权限逐项授权/启停/吊销/执行许可）。
 */
class PluginRegistryTest {

    private fun meta(
        id: String = "ad-killer",
        perms: Set<Permission> = setOf(Permission.MODIFY_STYLE),
        fp: String = "fp-1",
    ) = UserscriptMeta(
        id = id, name = "广告清理", version = "1.0", fingerprint = fp,
        matches = listOf("*.example.com"), permissions = perms,
    )

    @Test
    fun installWithFullGrantEnables() {
        val r = PluginRegistry()
        val m = r.install(meta(perms = setOf(Permission.MODIFY_STYLE)), granted = setOf(Permission.MODIFY_STYLE))
        assertTrue(m.enabled)
        assertTrue(Permission.MODIFY_STYLE in r.grantedPermissions(m.id))
    }

    @Test
    fun installWithoutRequiredPermissionStaysDisabled() {
        // 权限不足 → 安装但不启用（防越权运行，PRD §3.4）
        val r = PluginRegistry()
        val m = r.install(meta(perms = setOf(Permission.NETWORK)), granted = setOf(Permission.MODIFY_STYLE))
        assertFalse(m.enabled)
        assertFalse(r.mayExecute(m, host = "example.com"))
    }

    @Test
    fun enableDisableToggle() {
        val r = PluginRegistry()
        r.install(meta(), granted = setOf(Permission.MODIFY_STYLE))
        r.setEnabled("ad-killer", false)
        assertFalse(r.get("ad-killer")!!.enabled)
        r.setEnabled("ad-killer", true)
        assertTrue(r.get("ad-killer")!!.enabled)
        // 不存在的 id → null
        assertNull(r.setEnabled("nope", true))
    }

    @Test
    fun revokePermissionAutoDisables() {
        // PRD §3.4 授予后可关闭；权限收缩后不足以支撑声明 → 自动停用
        val r = PluginRegistry()
        val m = r.install(meta(perms = setOf(Permission.MODIFY_STYLE, Permission.NETWORK)), granted = setOf(Permission.MODIFY_STYLE, Permission.NETWORK))
        assertTrue(m.enabled)
        r.revokePermission(m.id, Permission.NETWORK)
        assertFalse(r.get(m.id)!!.enabled)
        assertTrue(Permission.NETWORK !in r.grantedPermissions(m.id))
    }

    @Test
    fun remoteRevocationAppliesAndBlocksReEnable() {
        // PRD §3.4/§10 远程吊销
        val r = PluginRegistry()
        val m = r.install(meta(fp = "bad-fp"), granted = setOf(Permission.MODIFY_STYLE))
        assertTrue(m.enabled)
        val changed = r.applyRevocation(listOf("bad-fp"))
        assertEquals(1, changed.size)
        assertTrue(r.get(m.id)!!.revoked)
        assertFalse(r.get(m.id)!!.enabled)
        // 吊销后不可重新启用
        r.setEnabled(m.id, true)
        assertFalse(r.get(m.id)!!.enabled)
        // 吊销后执行被拒
        assertFalse(r.mayExecute(r.get(m.id)!!, host = "example.com"))
    }

    @Test
    fun installRejectedWhenFingerprintAlreadyRevoked() {
        val r = PluginRegistry()
        r.applyRevocation(listOf("bad-fp"))
        val m = r.install(meta(fp = "bad-fp"), granted = setOf(Permission.MODIFY_STYLE))
        assertTrue(m.revoked)
        assertFalse(m.enabled)
    }

    @Test
    fun mayExecuteRequiresScopeMatch() {
        val r = PluginRegistry()
        val m = r.install(meta(perms = setOf(Permission.MODIFY_STYLE)), granted = setOf(Permission.MODIFY_STYLE))
        assertTrue(r.mayExecute(m, host = "www.example.com"))
        assertFalse(r.mayExecute(m, host = "other.org"))
        assertFalse(r.mayExecute(m, host = ""))
    }

    @Test
    fun uninstallRemovesGrantRecords() {
        val r = PluginRegistry()
        val m = r.install(meta(), granted = setOf(Permission.MODIFY_STYLE))
        r.uninstall(m.id)
        assertNull(r.get(m.id))
        assertTrue(r.grantedPermissions(m.id).isEmpty())
    }
}
