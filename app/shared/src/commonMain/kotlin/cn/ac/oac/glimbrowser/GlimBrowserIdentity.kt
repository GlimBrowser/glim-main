package cn.ac.oac.glimbrowser

/**
 * GlimBrowser 工程级常量（Phase 0）。
 * 包名/应用 ID 唯一真源：PRD V1.3（工程标识）+ docs/progess/000 §1 命名说明。
 */
object GlimBrowserIdentity {
    /** 工程根包名，全平台一致（Android applicationId / 各端 namespace 共用）。 */
    const val moduleName: String = "cn.ac.oac.glimbrowser"

    /** 产品对外名（原型文档中文名「隙光浏览器」，UI 文案以原型为准）。 */
    const val productName: String = "GlimBrowser"
}
