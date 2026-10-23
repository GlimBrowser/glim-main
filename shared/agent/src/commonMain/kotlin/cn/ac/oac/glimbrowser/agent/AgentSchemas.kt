package cn.ac.oac.glimbrowser.agent

/**
 * Agent 工具 JSON Schema 常量（附件 C 各工具 schema；注入 system 的 schemaHint，附件 C.1.2）。
 * 只保留 required 字段描述（精简提示词；完整 JSON Schema 文本见附件 C 原文）。
 */
object AgentSchemas {

    /** 附件 C.2.2 page_summary Schema（required: schema_version,title,reading_time_min,key_points,conclusion）。 */
    val PAGE_SUMMARY_SCHEMA: String = """
{"schema_version":"1.0","title":"string(<=120)","reading_time_min":int(1..600),
 "key_points":[{"text":"string(<=300)","anchor_quote":"string(<=160,原文摘录,可选)"}](3..10),
 "conclusion":"string(<=500)","tone":"news|tutorial|opinion|academic|commercial|forum|other"}
只输出一个合法 JSON 对象，不要输出 markdown 代码块。
""".trimIndent()

    /** page_summary 必填键（附件 C.2.2 required）。 */
    val PAGE_SUMMARY_REQUIRED: Set<String> = setOf("schema_version", "title", "reading_time_min", "key_points", "conclusion")

    /** 附件 C.3.2 page_translate。 */
    val PAGE_TRANSLATE_SCHEMA: String = """
{"schema_version":"1.0","src_lang":"xx-XX","tgt_lang":"xx-XX",
 "segments":[{"id":"string","text":"string(<=2000)","note":"string(<=100,可选)"}]}
""".trimIndent()

    val PAGE_TRANSLATE_REQUIRED: Set<String> = setOf("schema_version", "src_lang", "tgt_lang", "segments")

    /** 附件 C.6.2 table_extract。 */
    val TABLE_EXTRACT_SCHEMA: String = """
{"schema_version":"1.0","title":"string","columns":[{"name":"string","type":"string|number|date|bool|url|other","unit":"string"}],
 "rows":[["string|number|bool|null"]](<=200),"notes":"string"}
""".trimIndent()

    val TABLE_EXTRACT_REQUIRED: Set<String> = setOf("schema_version", "columns", "rows")

    /** 附件 C.4.1 selection_enhance.explain。 */
    val SELECTION_EXPLAIN_SCHEMA: String = """
{"schema_version":"1.0","term":"string(<=200)","explanation":"string(<=1500)",
 "category":"person|place|org|tech|term|slang|other","related":["string"](<=5)}
""".trimIndent()

    val SELECTION_EXPLAIN_REQUIRED: Set<String> = setOf("schema_version", "term", "explanation")

    /** 附件 C.4.2 selection_enhance.summarize。 */
    val SELECTION_SUMMARIZE_SCHEMA: String = """
{"schema_version":"1.0","key_points":["string(<=300)"](1..5)}
""".trimIndent()

    val SELECTION_SUMMARIZE_REQUIRED: Set<String> = setOf("schema_version", "key_points")

    /** 附件 C.4.3 selection_enhance.translate。 */
    val SELECTION_TRANSLATE_SCHEMA: String = """
{"schema_version":"1.0","src_lang":"xx","tgt_lang":"xx","text":"string(<=4000)","phonetic":"string(可选)"}
""".trimIndent()

    val SELECTION_TRANSLATE_REQUIRED: Set<String> = setOf("schema_version", "src_lang", "tgt_lang", "text")

    /** 按工具取 Schema/必填键（供 orchestrator 通用调用）。 */
    fun schemaFor(tool: AgentTool): Pair<String, Set<String>> = when (tool) {
        AgentTool.PAGE_SUMMARY -> PAGE_SUMMARY_SCHEMA to PAGE_SUMMARY_REQUIRED
        AgentTool.PAGE_TRANSLATE -> PAGE_TRANSLATE_SCHEMA to PAGE_TRANSLATE_REQUIRED
        AgentTool.SELECTION_EXPLAIN -> SELECTION_EXPLAIN_SCHEMA to SELECTION_EXPLAIN_REQUIRED
        AgentTool.SELECTION_SUMMARIZE -> SELECTION_SUMMARIZE_SCHEMA to SELECTION_SUMMARIZE_REQUIRED
        AgentTool.SELECTION_TRANSLATE -> SELECTION_TRANSLATE_SCHEMA to SELECTION_TRANSLATE_REQUIRED
        AgentTool.TABLE_EXTRACT -> TABLE_EXTRACT_SCHEMA to TABLE_EXTRACT_REQUIRED
        else -> "" to emptySet()
    }
}
