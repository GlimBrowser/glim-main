package cn.ac.oac.glimbrowser.adfilter

/**
 * 去广告开关状态机（PRD V1.0 §5.1 + V1.1 §3.3.3 站点级开关 P0 + §3.3.3 误杀闭环"本页暂停拦截"）。
 *
 * ```
 * 全局开关(默认 ON)  ←—— PRD V1.1 §3.3.2「默认值红线：L1–L3 首启即启用，零配置」
 *    ├── 站点白名单 → 该域名降级为"仅追踪器拦截"或"全关"（用户手动添加；不接受合作方预置）
 *    └── 规则集开关 → 各规则文件独立启用（Phase 4 仅暴露默认 L1 集）
 * ```
 *
 * 纯 Kotlin 可单测；Phase 6 持久化（PRD §5.1 状态机不变）。
 */
data class FilterSettings(
    /** 全局开关（默认 ON —— PRD V1.1 §3.3.3 承诺级红线）。 */
    val globalEnabled: Boolean = true,
    /** 站点域名 → 该站拦截级别。 */
    val siteModes: Map<String, SiteMode> = emptyMap(),
    /** L1–L3 分层默认全开（PRD §3.3.2 默认值红线）。 */
    val layerEnabled: Map<Layer, Boolean> = Layer.entries.associateWith { it.defaultOn },
    /** 无痕模式是否仍启用拦截（默认启用 —— 无痕只影响历史/缓存，不影响净化）。 */
    val incognitoStillFilters: Boolean = true,
) {
    enum class SiteMode {
        /** 正常（跟随全局）。 */
        NORMAL,
        /** 降级：仅追踪器拦截（PRD §5.1）。 */
        TRACKERS_ONLY,
        /** 全关（PRD §3.3.3"本页暂停拦截"按钮落点）。 */
        DISABLED,
    }

    /** 规则分层（PRD §3.3.2 L1–L4）。 */
    enum class Layer(val defaultOn: Boolean, val title: String) {
        /** L1 规则过滤（EasyList 兼容 + 自维护）。 */
        L1(true, "规则过滤"),
        /** L2 注入净化（document_start 脚本去容器/浮层）。 */
        L2(true, "注入净化"),
        /** L3 站点精修（高频站点专用 CSS/JS）。 */
        L3(true, "站点精修"),
        /** L4 用户规则（按需启用）。 */
        L4(false, "用户规则"),
    }

    /**
     * 该域名当前的有效拦截级别（考虑全局开关 + 站点模式）。
     */
    fun effectiveModeFor(host: String): SiteMode = when {
        !globalEnabled -> SiteMode.DISABLED
        else -> siteModes[host] ?: SiteMode.NORMAL
    }

    /**
     * 是否应执行网络拦截（L1 层）。
     * @param host 请求发起页域名
     * @param isTrackerRequest 是否追踪器类请求（站点降级 TRACKERS_ONLY 时仅追踪器继续拦截）
     */
    fun shouldBlockNetwork(host: String, isTrackerRequest: Boolean): Boolean = when (effectiveModeFor(host)) {
        SiteMode.DISABLED -> false
        SiteMode.TRACKERS_ONLY -> isTrackerRequest
        SiteMode.NORMAL -> globalEnabled && layerEnabled[Layer.L1] == true
    }

    /** 化妆隐藏是否启用（L1/L3 关闭则不隐藏）。 */
    fun shouldCosmeticHide(host: String): Boolean =
        effectiveModeFor(host) == SiteMode.NORMAL &&
            (layerEnabled[Layer.L1] == true || layerEnabled[Layer.L3] == true)

    // ===== 操作（纯函数迁移） =====

    fun withGlobalEnabled(on: Boolean): FilterSettings = copy(globalEnabled = on)

    fun withSiteMode(host: String, mode: SiteMode): FilterSettings =
        copy(siteModes = if (mode == SiteMode.NORMAL) siteModes - host else siteModes + (host to mode))

    /** 误杀闭环：本页暂停拦截（PRD §3.3.3 流程 B 第 2 步）。 */
    fun pauseSite(host: String): FilterSettings = withSiteMode(host, SiteMode.DISABLED)

    /** 恢复该站点拦截（用户撤销/白名单移除）。 */
    fun resumeSite(host: String): FilterSettings = withSiteMode(host, SiteMode.NORMAL)

    fun withLayer(layer: Layer, enabled: Boolean): FilterSettings =
        copy(layerEnabled = layerEnabled + (layer to enabled))
}
