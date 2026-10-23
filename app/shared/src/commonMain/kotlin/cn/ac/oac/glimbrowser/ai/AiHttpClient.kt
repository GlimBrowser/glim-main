package cn.ac.oac.glimbrowser.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * AI HTTP 客户端工厂（各端注入引擎：Android/JVM=CIO、Apple=Darwin、Web=JS）。
 * 统一安装 ContentNegotiation(json) 与 SSE（对话类流式所需，附件 C.1.1）。
 * 红线（000 §2）：BYOK 直连——客户端不配置任何代理/中转端点。
 */
expect fun createAiHttpClient(): HttpClient

/** 共享默认配置（各端 actual 复用）。 */
internal val aiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

internal fun io.ktor.client.HttpClientConfig<*>.installAiPlugins() {
    install(ContentNegotiation) { json(aiJson) }
    install(SSE)
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}
