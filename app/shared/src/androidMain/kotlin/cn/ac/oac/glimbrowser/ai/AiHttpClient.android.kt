package cn.ac.oac.glimbrowser.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android 用 OkHttp 引擎（**不要用 CIO**）：
 * CIO 走 JSSE 的 `checkServerTrusted(chain, authType)` 两参重载，
 * 在带域名级 networkSecurityConfig 的 App 里会触发：
 * "Domain specific configurations require that hostname aware checkServerTrusted(...) is used"
 * → AI 配置页"测试连接"总是失败。OkHttp 使用平台宿主名校验，行为与浏览器一致。
 */
actual fun createAiHttpClient(): HttpClient = HttpClient(OkHttp) { installAiPlugins() }
