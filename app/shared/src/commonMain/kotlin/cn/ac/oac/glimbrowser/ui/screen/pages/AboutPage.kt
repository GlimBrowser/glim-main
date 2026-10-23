package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 关于页：版本、产品定位、隐私承诺（真实说明，非占位）。 */
@Composable
fun AboutPage(onBack: () -> Unit) {
    PageScaffold(title = "关于", onBack = onBack) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("GlimBrowser 1.0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("轻量、干净、可离线的多标签浏览器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            SectionTitle("隐私")
            Text(
                "· 不托管 AI：摘要与问答直接连接你填写的服务，页面内容不经过我们的服务器\n" +
                    "· 密钥保存在本机安全区，默认不可见，也不会写入日志或统计\n" +
                    "· 无痕模式不记录历史、不落缓存\n" +
                    "· 去广告在本机完成，默认开启且永久免费",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            SectionTitle("数据")
            Text(
                "历史、书签、插件与设置保存在本机数据库；卸载应用会一并删除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            SectionTitle("开源组件")
            Text(
                "Kotlin、Compose Multiplatform、Ktor、kotlinx.serialization、SQLDelight、kotlinx-datetime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            SectionTitle("支持")
            Text(
                "遇到问题请附上操作步骤与页面地址（无需提供密钥）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
