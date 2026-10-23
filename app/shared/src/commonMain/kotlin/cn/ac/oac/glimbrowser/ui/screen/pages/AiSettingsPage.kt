package cn.ac.oac.glimbrowser.ui.screen.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.ai.AiPreferences
import cn.ac.oac.glimbrowser.ui.design.CapabilityCard
import cn.ac.oac.glimbrowser.ui.design.GlimButton
import cn.ac.oac.glimbrowser.ui.design.GlimButtonVariant
import cn.ac.oac.glimbrowser.ui.design.GlimInput
import cn.ac.oac.glimbrowser.ui.design.GlimSegmented
import cn.ac.oac.glimbrowser.ui.design.GlimToggle
import cn.ac.oac.glimbrowser.ui.design.SettingRow

/**
 * AI 设置页：此前只有 Base URL / Key / Model 三项；这里补齐全部开关与参数（真实读写 + 持久化）。
 */
@Composable
fun AiSettingsPage(
    prefs: AiPreferences,
    onChange: (AiPreferences) -> Unit,
    configured: Boolean,
    profileModel: String,
    onReconfigure: () -> Unit,
    onTestConnection: () -> Unit,
    testResult: String?,
    onBack: () -> Unit,
) {
    var headers by remember(prefs.customHeaders) { mutableStateOf(prefs.customHeaders) }
    PageScaffold(
        title = "AI 设置",
        onBack = onBack,
        action = { GlimButton(if (configured) "重新配置" else "去配置", onReconfigure, variant = GlimButtonVariant.SECONDARY, small = true) },
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("总开关")
            SettingRow("启用 AI 功能", "关闭后摘要、问答、划词等入口都会提示未启用", control = {
                GlimToggle(prefs.enabled) { onChange(prefs.copy(enabled = it)) }
            })
            SettingRow("无痕模式下禁用 AI", "避免隐私模式下把页面内容发出去", control = {
                GlimToggle(prefs.disableInIncognito) { onChange(prefs.copy(disableInIncognito = it)) }
            })
            SettingRow("打开面板时自动摘要", "打开 AI 抽屉后立即对当前页生成摘要", control = {
                GlimToggle(prefs.autoSummaryOnOpen) { onChange(prefs.copy(autoSummaryOnOpen = it)) }
            })

            SectionTitle("输出协议")
            SettingRow("流式输出", "对话类逐字返回；关闭后一次性返回", control = {
                GlimToggle(prefs.streamEnabled) { onChange(prefs.copy(streamEnabled = it)) }
            })
            SettingRow("优先结构化输出（JSON）", "摘要/翻译/提取使用 json_object；关闭则退化为文本解析", control = {
                GlimToggle(prefs.preferJsonObject) { onChange(prefs.copy(preferJsonObject = it)) }
            })
            SettingRow("允许降级解析", "模型输出不合法时自动尝试剥围栏/括号提取；关闭则直接报错", control = {
                GlimToggle(prefs.allowDegrade) { onChange(prefs.copy(allowDegrade = it)) }
            })

            SectionTitle("请求参数")
            SettingRow("请求超时：${prefs.timeoutSeconds}s", "网络慢时可调大", control = {
                GlimSegmented(listOf("30s", "60s", "90s", "120s"),
                    selected = when (prefs.timeoutSeconds) {
                        30 -> 0; 60 -> 1; 90 -> 2; else -> 3
                    }) { i ->
                    onChange(prefs.copy(timeoutSeconds = listOf(30, 60, 90, 120)[i]))
                }
            })
            SettingRow("温度：${prefs.temperature}", "越低越稳定（摘要 0.2，问答 0.7）", control = {
                GlimSegmented(listOf("0.2", "0.3", "0.5", "0.7"), selected = when (prefs.temperaturePermille) {
                    2 -> 0; 3 -> 1; 5 -> 2; 7 -> 3; else -> 1
                }) { i -> onChange(prefs.copy(temperaturePermille = listOf(2, 3, 5, 7)[i])) }
            })
            SettingRow("最大输出：${prefs.maxTokens} tokens", "过小会截断摘要", control = {
                GlimSegmented(listOf("512", "1024", "2048"), selected = when (prefs.maxTokens) {
                    512 -> 0; 1024 -> 1; else -> 2
                }) { i -> onChange(prefs.copy(maxTokens = listOf(512, 1024, 2048)[i])) }
            })

            SectionTitle("内容")
            SettingRow("摘要语言", "自动 = 跟随页面", control = {
                GlimSegmented(listOf("自动", "中文", "English"),
                    selected = when (prefs.summaryLanguage) { "zh" -> 1; "en" -> 2; else -> 0 }) { i ->
                    onChange(prefs.copy(summaryLanguage = listOf("auto", "zh", "en")[i]))
                }
            })
            SettingRow("随请求发送页面地址", "默认关闭，最少信息原则", control = {
                GlimToggle(prefs.sendPageUrl) { onChange(prefs.copy(sendPageUrl = it)) }
            })

            SectionTitle("自定义请求头")
            GlimInput(
                label = "每行一条，格式 Key: Value",
                value = headers,
                placeholder = "X-Custom: value",
                onChange = { headers = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                GlimButton("保存请求头", {
                    onChange(prefs.copy(customHeaders = headers))
                }, variant = GlimButtonVariant.SECONDARY, small = true)
                Spacer(Modifier.width(8.dp))
                GlimButton("测试连接", onTestConnection, small = true)
            }
            testResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("当前档案")
            Text(
                if (configured) "模型：$profileModel" else "尚未配置端点与密钥",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            CapabilityCard(
                title = if (configured) "已连接配置" else "未配置",
                capabilities = listOf(
                    "结构化" to prefs.preferJsonObject,
                    "流式" to prefs.streamEnabled,
                    "降级" to prefs.allowDegrade,
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
