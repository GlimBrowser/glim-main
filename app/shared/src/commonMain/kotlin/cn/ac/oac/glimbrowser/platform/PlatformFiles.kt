package cn.ac.oac.glimbrowser.platform

/**
 * 保存文本到用户可访问位置（书签导出等）。
 * @return 保存后的路径/位置说明；失败返回 null（UI 显示失败原因，不伪造成功）。
 */
expect fun saveTextFile(fileName: String, content: String): String?
