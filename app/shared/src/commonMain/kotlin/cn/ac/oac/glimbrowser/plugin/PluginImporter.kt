package cn.ac.oac.glimbrowser.plugin

import cn.ac.oac.glimbrowser.ai.createAiHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * 用户脚本导入（按 URL 拉取 .user.js → 解析头部元数据 → 生成安装记录）。
 *
 * 只做真实可验证的部分：抓取文本、解析 @name/@version/@match/@grant、映射权限；
 * 脚本执行沙箱仍未实现（见 109 文档"未完成"），导入后仅登记与授权。
 */
object PluginImporter {

    data class Parsed(
        val name: String,
        val version: String,
        val matches: List<String>,
        val permissions: Set<Permission>,
        val description: String,
    )

    /** 抓取并解析脚本元数据；失败返回 null（网络/格式问题，不伪造）。 */
    suspend fun fetchAndParse(url: String): Parsed? = fetchAndParseWithSource(url)?.first

    /** 抓取：返回（元数据, 脚本正文），正文用于落库后执行。 */
    suspend fun fetchAndParseWithSource(url: String): Pair<Parsed, String>? {
        if (!url.startsWith("http")) return null
        val client = createAiHttpClient()
        return try {
            val text = client.get(url).bodyAsText()
            parse(text) to text
        } catch (t: Throwable) {
            null
        } finally {
            client.close()
        }
    }

    /** 解析 ==UserScript== 头部（缺省时给出保守默认，不授予额外权限）。 */
    fun parse(script: String): Parsed {
        val header = script.substringAfter("==UserScript==", "").substringBefore("==/UserScript==")
        fun values(key: String): List<String> = header.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("@$key") }
            .map { it.removePrefix("@$key").trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val name = values("name").firstOrNull() ?: "未命名脚本"
        val version = values("version").firstOrNull() ?: "1.0"
        val matches = values("match").ifEmpty { values("include") }
        val grants = values("grant")
        val permissions = grants.mapNotNull { grantToPermission(it) }.toSet()
        val description = values("description").firstOrNull() ?: ""
        return Parsed(name, version, matches, permissions, description)
    }

    private fun grantToPermission(grant: String): Permission? = when {
        grant.contains("GM_setClipboard") || grant.contains("clipboard") -> Permission.CLIPBOARD
        grant.contains("GM_xmlhttpRequest") || grant.contains("fetch") -> Permission.NETWORK
        grant.contains("GM_notification") -> Permission.NOTIFY
        grant.contains("GM_setValue") || grant.contains("storage") -> Permission.STORAGE
        grant.contains("GM_addStyle") || grant.contains("GM_addElement") -> Permission.MODIFY_STYLE
        grant == "none" -> null
        else -> Permission.READ_PAGE_TEXT
    }
}
