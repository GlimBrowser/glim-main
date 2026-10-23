package cn.ac.oac.glimbrowser

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * GlimBrowser 规则/插件 CDN 占位服务。
 *
 * 对应文档：PRD V1.1 §2.3「规则云 CDN（仅广告规则 / 插件库更新）※ 不承载任何 AI 数据链路」。
 * Phase 0 仅提供健康检查与占位路由，实际规则分发在 Phase 4（adfilter）之后接入。
 * 红线（PRD V1.1 §3.2.5）：本服务永不承载 AI 请求、页面正文、API Key。
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("GlimBrowser rule CDN placeholder — no AI data path here (PRD V1.1 §2.3).")
        }

        get("/healthz") {
            call.respondText("ok")
        }
    }
}
