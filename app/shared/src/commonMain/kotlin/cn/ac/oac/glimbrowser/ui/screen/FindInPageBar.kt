package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens

/**
 * 页内查找条（真实：Android 走内核 findAllAsync 高亮并回传命中数；iOS 用脚本统计命中并滚动到首个）。
 */
@Composable
fun FindInPageBar(
    query: String,
    matches: Int,
    index: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(GlimTokens.RadiusPill)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("在页面中查找", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    inner()
                }
            },
        )
        Text(
            if (matches > 0) "${index.coerceAtLeast(1)}/$matches" else if (query.isBlank()) "" else "0/0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text("‹", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onPrev).padding(horizontal = 6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onNext).padding(horizontal = 6.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("关闭", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
    }
}
