package cn.ac.oac.glimbrowser.logic

/**
 * 搜索词 URL 百分号编码（common 纯 Kotlin 实现，不依赖平台 URL 类）。
 *
 * 用途：[AddressBarResolver] 默认 queryEncoder（构造/单测可注入替代实现）。
 * 规则：RFC 3986 —— 未保留字符 `A-Za-z0-9-_.~` 原样；其余按 UTF-8 字节逐个 `%XX`（大写）。
 * 空格编码为 `%20`（`SearchEngine` 模板用在 query 位置，非 form body 的 `+`）。
 */
fun String.encodeUrlQuery(): String {
    if (isEmpty()) return this
    val bytes = encodeToByteArray()
    val sb = StringBuilder(bytes.size * 3)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        val unreserved = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
            ch == '-' || ch == '.' || ch == '_' || ch == '~'
        if (unreserved) {
            sb.append(ch)
        } else {
            sb.append('%')
            sb.append(HEX[c shr 4])
            sb.append(HEX[c and 0x0F])
        }
    }
    return sb.toString()
}

private const val HEX: String = "0123456789ABCDEF"
