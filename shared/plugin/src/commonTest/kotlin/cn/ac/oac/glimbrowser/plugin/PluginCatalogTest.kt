package cn.ac.oac.glimbrowser.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 订阅清单单测（PRD §3.4 订阅更新：版本比对 + 权限变化须重新授权）。
 */
class PluginCatalogTest {

    private fun catalog() = PluginCatalog(
        entries = listOf(
            PluginCatalog.CatalogEntry(
                id = "ad-killer", name = "广告清理", version = "2.0",
                scriptUrl = "https://cdn.example.com/ad-killer.user.js",
                fingerprint = "fp-2",
                matches = listOf("*.example.com"),
                permissions = listOf(Permission.MODIFY_STYLE, Permission.NETWORK),   // 更新后新增 NETWORK 权限
            ),
        ),
        revokedFingerprints = listOf("old-bad-fp"),
    )

    @Test
    fun detectsVersionUpdate() {
        val installed = UserscriptMeta(id = "ad-killer", name = "广告清理", version = "1.0", permissions = setOf(Permission.MODIFY_STYLE))
        assertTrue(catalog().hasUpdate(installed))
        val same = installed.copy(version = "2.0")
        assertFalse(catalog().hasUpdate(same))
        assertFalse(catalog().hasUpdate(installed.copy(id = "unknown")))
    }

    @Test
    fun detectsPermissionChange_requiresRegrant() {
        // 红线：更新若引入新权限 → 必须重新逐项授权（PRD §3.4），UI 据此卡确认页
        val installed = UserscriptMeta(id = "ad-killer", name = "广告清理", version = "1.0", permissions = setOf(Permission.MODIFY_STYLE))
        assertTrue(catalog().permissionsChanged(installed))
        val unchanged = installed.copy(permissions = setOf(Permission.MODIFY_STYLE, Permission.NETWORK))
        assertFalse(catalog().permissionsChanged(unchanged))
    }

    @Test
    fun revokedFingerprintsCarried() {
        assertTrue(catalog().revokedFingerprints.contains("old-bad-fp"))
    }
}
