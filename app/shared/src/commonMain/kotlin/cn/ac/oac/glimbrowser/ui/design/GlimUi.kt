package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 组件库（对齐 V2.0 Safari-inspired 设计：毛玻璃、胶囊、纸墨令牌）。
 *
 * Compose 无法做真实背景模糊（backdrop-filter），因此用「半透明底色 + 细边框 + 轻阴影」逼近；
 * 昼/夜由 MaterialTheme 的 colorScheme 自动切换，透明度取自 [GlimTokens]。
 */

/** 玻璃面：半透明底 + 细边框（+ 可选顶/底分隔线）。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    strong: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = GlimTokens.RadiusCard,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surface
    val alpha = if (strong) GlimTokens.GlassAlphaStrong else GlimTokens.GlassAlphaMedium
    Box(
        modifier = modifier
            .clip(shape)
            .background(base.copy(alpha = alpha))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape),
    ) { content() }
}

/** 顶部工具栏（统一 chrome：左右图标区 + 中间地址栏）。 */
@Composable
fun UnifiedToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { center() }
        trailing()
    }
}

/** 工具栏图标按钮（32dp；按下缩放由 Compose 交互态自然呈现）。 */
@Composable
fun GlimIconButton(
    icon: GlimIconName,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
    size: Int = 32,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(GlimTokens.RadiusSm)
            .background(
                when {
                    accent -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlimIcon(
            name = icon,
            size = (size - 10).dp,
            color = when {
                accent -> MaterialTheme.colorScheme.onPrimary
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            },
        )
    }
}

/** 按钮四级层次：primary / secondary / ghost / danger。 */
enum class GlimButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun GlimButton(
    text: String,
    onClick: () -> Unit,
    variant: GlimButtonVariant = GlimButtonVariant.PRIMARY,
    enabled: Boolean = true,
    pill: Boolean = true,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = if (pill) GlimTokens.RadiusPill else GlimTokens.RadiusSm
    val bg = when (variant) {
        GlimButtonVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        GlimButtonVariant.SECONDARY -> MaterialTheme.colorScheme.surfaceVariant
        GlimButtonVariant.GHOST -> Color.Transparent
        GlimButtonVariant.DANGER -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }
    val fg = when (variant) {
        GlimButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        GlimButtonVariant.SECONDARY -> MaterialTheme.colorScheme.onSurface
        GlimButtonVariant.GHOST -> MaterialTheme.colorScheme.onSurfaceVariant
        GlimButtonVariant.DANGER -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 13.dp else 18.dp, vertical = if (small) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = if (enabled) fg else fg.copy(alpha = 0.4f))
    }
}

/** 徽标（jade/amber/danger/neutral）。 */
enum class GlimBadgeTone { JADE, AMBER, DANGER, NEUTRAL }

@Composable
fun GlimBadge(text: String, tone: GlimBadgeTone = GlimBadgeTone.JADE, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        GlimBadgeTone.JADE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.primary
        GlimBadgeTone.AMBER -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) to MaterialTheme.colorScheme.tertiary
        GlimBadgeTone.DANGER -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f) to MaterialTheme.colorScheme.error
        GlimBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier.clip(GlimTokens.RadiusPill).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = fg) }
}

/** 开关（44×26 胶囊，滑块白点）。 */
@Composable
fun GlimToggle(
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(GlimTokens.RadiusPill)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    on -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(enabled = enabled) { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(GlimTokens.RadiusPill)
                .background(if (on) Color.White else MaterialTheme.colorScheme.surface),
        )
    }
}

/** 设置行（标题 + 说明 + 右侧控件）。 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.width(10.dp))
        control()
    }
}

/** 分段控件。 */
@Composable
fun GlimSegmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier.clip(GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .clip(GlimTokens.RadiusPill)
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** 输入框（带标签 + 聚焦描边）。 */
@Composable
fun GlimInput(
    label: String,
    value: String,
    placeholder: String = "",
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(GlimTokens.RadiusSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), GlimTokens.RadiusSm)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    inner()
                }
            },
        )
    }
}

/** 地址栏胶囊（统一工具栏中间件）。 */
@Composable
fun UrlPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlimTokens.RadiusPill)
            .background(if (focused) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                GlimTokens.RadiusPill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 站点字母瓦片（favicon 占位）。 */
@Composable
fun FavTile(letter: String, modifier: Modifier = Modifier, size: Int = 38, tint: Color? = null) {
    val c = tint ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(c.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = c)
    }
}

/** 骨架屏条。 */
@Composable
fun SkeletonLine(widthFraction: Float = 1f, height: Int = 12, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(GlimTokens.RadiusXs)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ),
            ),
    )
}

/** 玻璃卡片。 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassSurface(modifier = modifier, strong = false) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

/** AI 要点卡（左侧玉青竖线 + 原文出处）。 */
@Composable
fun KeyPointCard(text: String, source: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (source.isNullOrBlank()) 34.dp else 52.dp)
                .clip(GlimTokens.RadiusPill)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (!source.isNullOrBlank()) {
                Text(
                    "SRC ▸ $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 结论卡。 */
@Composable
fun ConclusionCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .clip(GlimTokens.RadiusSm)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Text("结论 — $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 能力位卡（连接成功 + 能力徽标）。 */
@Composable
fun CapabilityCard(title: String, capabilities: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(GlimTokens.RadiusSm)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), GlimTokens.RadiusSm)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            capabilities.forEach { (name, ok) ->
                GlimBadge(
                    if (ok) "$name ✓" else "$name ✗",
                    if (ok) GlimBadgeTone.JADE else GlimBadgeTone.NEUTRAL,
                )
            }
        }
    }
}

/** 错误卡（错误码 + 人话）。 */
@Composable
fun GlimErrorCard(code: String, message: String, danger: Boolean = true, modifier: Modifier = Modifier) {
    val fg = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(GlimTokens.RadiusSm)
            .background(fg.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Text(code, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = fg.copy(alpha = 0.8f))
        Spacer(Modifier.height(3.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

/** 步进器（三步引导）。 */
@Composable
fun GlimStepper(current: Int, total: Int = 3, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val done = i < current
            val active = i == current
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(
                        when {
                            done || active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "$i",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        done || active -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
            if (i < total) {
                Box(
                    modifier = Modifier.width(22.dp).height(1.5.dp)
                        .background(if (i < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
            }
        }
    }
}
