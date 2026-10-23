package cn.ac.oac.glimbrowser.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createAiHttpClient(): HttpClient = HttpClient(CIO) { installAiPlugins() }
