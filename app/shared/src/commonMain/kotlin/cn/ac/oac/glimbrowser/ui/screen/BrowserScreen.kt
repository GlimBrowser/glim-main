package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.model.BrowserTab
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 浏览壳内容区（地址栏 + 进度条 + 页面占位）—— PRD V1.0 §3.1.2 页面加载与渲染 + 原型 §02 screen "browse"。
 *
 * Phase 2 范围（容器未接，页面区域为占位）：
 *  - 地址栏：锁标 + 域名 + 护盾计数 + ✨（原型 .addr / 注释 02）
 *  - 加载进度：顶部细线（PRD §3.1.2「顶部细线进度条」，loading 时显示）
 *  - 页面正文：Phase 2 占位说明（Phase 3 由 BrowserEngine 嵌入真实 WebView）
 *
 * @param tab 当前标签（地址栏展示其 url 域名 / loading / progress）
 * @param shieldCount 护盾计数（原型 .badge-shield 🛡 12；Phase 4 前为静态占位 0，Phase 4 接净化统计）
 * @param addressBarClick 点地址栏 → 聚焦编辑态（切到输入态视图）
 * @param onShieldClick 护盾点击 → 净化报告（PRD §3.3.3 误杀闭环入口之一）
 * @param onAiClick ✨点击 → AI 抽屉（Phase 5 接 Agent；未配置时进三步引导）
 */
@Composable
fun BrowserScreen(
    tab: BrowserTab?,
    shieldCount: Int,
    onAddressBarClick: () -> Unit,
    onShieldClick: () -> Unit,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 后退/前进（引擎池消费；108 手势/按钮真实导航）。 */
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    /** 错误卡"重试"（dispatch RetryPage）。 */
    onRetry: () -> Unit = {},
    /** 收藏当前页（真实落库；☆/★ 态来自仓库）。 */
    bookmarked: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    /** 无痕模式指示（真实状态）。 */
    incognito: Boolean = false,
    /** 页面主体槽（Phase 3 起由外壳传入 BrowserContainer/平台内核视图；默认 Phase 2 占位）。 */
    body: @Composable (Modifier) -> Unit = { m -> PagePlaceholder(tab = tab, modifier = m) },
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部加载进度细线（PRD §3.1.2；仅 loading 时占位，Phase 3 由容器 progress 驱动）
        if (tab != null && tab.isLoading) {
            LinearProgressIndicator(
                progress = { tab.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        // 统一工具栏（Safari 风格）：← → ↻ + 地址胶囊 + 🛡 ✦ ⋯
        cn.ac.oac.glimbrowser.ui.design.UnifiedToolbar(
            leading = {
                cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.BACK, onBack, enabled = tab?.canGoBack == true)
                Spacer(Modifier.width(2.dp))
                cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.FORWARD, onForward, enabled = tab?.canGoForward == true)
                Spacer(Modifier.width(2.dp))
                cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.RELOAD, onRetry, enabled = tab != null)
            },
            trailing = {
                cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.SHIELD, onShieldClick)
                Spacer(Modifier.width(2.dp))
                cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.SPARKLE, onAiClick, accent = true)
            },
            center = {
                cn.ac.oac.glimbrowser.ui.design.UrlPill(
                    text = tab?.faviconDomain?.ifEmpty { tab.url }?.ifEmpty { "搜索或输入网址" } ?: "搜索或输入网址",
                    onClick = onAddressBarClick,
                    leading = {
                        cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                            name = if (incognito) cn.ac.oac.glimbrowser.ui.design.GlimIconName.INCOGNITO
                                   else cn.ac.oac.glimbrowser.ui.design.GlimIconName.LOCK,
                            size = 14.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailing = {
                        if (shieldCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                                    name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.SHIELD,
                                    size = 12.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(3.dp))
                                cn.ac.oac.glimbrowser.ui.design.GlimBadge("$shieldCount")
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        cn.ac.oac.glimbrowser.ui.design.GlimIcon(
                            name = if (bookmarked) cn.ac.oac.glimbrowser.ui.design.GlimIconName.STAR_FILLED
                                   else cn.ac.oac.glimbrowser.ui.design.GlimIconName.STAR,
                            size = 18.dp,
                            color = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.clip(GlimTokens.RadiusSm).clickable(onClick = onBookmarkClick).padding(horizontal = 3.dp),
                        )
                    },
                )
            },
        )

        // 页面内容区 + 错误卡覆盖（"网页访问报错"，108）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            body(Modifier.fillMaxSize())
            val err = tab?.lastError
            if (err != null) {
                ErrorCard(message = err, onRetry = onRetry, onDismiss = onRetry)
            }
        }
    }
}

/** 地址栏（原型 .addr）。 */


/** 护盾徽标（原型 .badge-shield；暂停态琥珀色由 Phase 4 误杀闭环接入，Phase 2 恒玉青）。 */
@Composable
private fun ShieldBadge(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        cn.ac.oac.glimbrowser.ui.design.GlimIcon(
            name = cn.ac.oac.glimbrowser.ui.design.GlimIconName.SHIELD,
            size = 11.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 锁标（极简，纯 Compose；Phase 3 接真实 https 判定）。 */
@Composable
private fun LockGlyph(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 6.dp)
                .border(1.4.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
        )
        Box(
            modifier = Modifier
                .width(9.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 页面内容占位（Phase 3 替换为真实 WebView 容器；Phase 2 明确标注状态，避免误以为能上网）。 */
@Composable
private fun PagePlaceholder(tab: BrowserTab?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tab?.title ?: "关于 GlimBrowser",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = buildString {
                append("当前页面暂未渲染。\n\n")
                append("地址：${tab?.url ?: TabHomeUrl}\n\n")
                append("请检查网络连接后重试。如果问题持续，可能是这个平台暂不支持网页渲染。")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TabHomeUrl = "glim://newtab"


/** 后退/前进箭头按钮（108：真实导航能力回填驱动）。 */


/** 页面错误卡（"网页访问报错"：说明 + 重试/返回，108）。 */
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\u26a0\ufe0f \u7f51\u9875\u8bbf\u95ee\u62a5\u9519", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.primary).clickable(onClick = onRetry).padding(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("\u91cd\u8bd5", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                Box(
                    modifier = Modifier.clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("\u8fd4\u56de", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
