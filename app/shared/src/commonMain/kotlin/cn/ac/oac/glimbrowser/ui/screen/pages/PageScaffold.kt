package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/** 子页面通用外框：返回 + 标题 + 右侧动作 + 内容区。 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 玻璃页头（组件库统一 chrome）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = GlimTokens.GlassAlphaStrong))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cn.ac.oac.glimbrowser.ui.design.GlimIconButton(cn.ac.oac.glimbrowser.ui.design.GlimIconName.BACK, onBack, size = 34)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

/** 搜索框（子页面顶部过滤）。 */
@Composable
fun PageSearchField(value: String, placeholder: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth().clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), GlimTokens.RadiusPill)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                inner()
            }
        },
    )
}

/** 列表行：标题 + 副标题 + 右侧操作。 */
@Composable
fun PageRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
        }
        actions?.invoke()
    }
}

/** 小按钮（页面动作）。 */
@Composable
fun PageChip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    cn.ac.oac.glimbrowser.ui.design.GlimButton(
        text = text,
        onClick = onClick,
        variant = if (primary) cn.ac.oac.glimbrowser.ui.design.GlimButtonVariant.PRIMARY
                  else cn.ac.oac.glimbrowser.ui.design.GlimButtonVariant.SECONDARY,
        small = true,
    )
}

/** 分组标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/** 空态。 */
@Composable
fun EmptyHint(text: String) {
    Spacer(modifier = Modifier.height(24.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 开关行。 */
@Composable
fun ToggleLine(label: String, on: Boolean, subtitle: String = "", onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Box(
            modifier = Modifier.clip(GlimTokens.RadiusPill)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChange(!on) }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(if (on) "开" else "关", style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
        }
    }
}
