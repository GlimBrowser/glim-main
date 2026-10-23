package cn.ac.oac.glimbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 平台初始化：系统栏同步 / 数据库上下文 / 密钥安全区（缺一不可，否则配置无法持久化）
        cn.ac.oac.glimbrowser.platform.currentActivity = this
        cn.ac.oac.glimbrowser.data.initAndroidDatabaseContext(this)
        cn.ac.oac.glimbrowser.securekey.initSecureKeyStore(this)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}