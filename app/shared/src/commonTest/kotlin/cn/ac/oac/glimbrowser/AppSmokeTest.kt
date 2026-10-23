package cn.ac.oac.glimbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 0 冒烟：commonTest 契约占位，确保 commonTest 源集本身可被各平台继承编译/运行。
 * 真正业务单测从 Phase 1（SizeClass 判定）/ Phase 4（规则引擎）/ Phase 5（json_object 降级链）开始填充。
 */
class AppSmokeTest {
    @Test
    fun smokeBaseline() {
        assertEquals("cn.ac.oac.glimbrowser", GlimBrowserIdentity.moduleName)
    }
}
