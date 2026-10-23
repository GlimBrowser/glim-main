package cn.ac.oac.glimbrowser.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createAiHttpClient(): HttpClient = HttpClient(Js) { installAiPlugins() }
