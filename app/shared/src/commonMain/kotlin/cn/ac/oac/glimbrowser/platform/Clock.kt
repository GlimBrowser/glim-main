package cn.ac.oac.glimbrowser.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 首页时钟（真实时间，替换 Phase 1 静态占位 "9:41"；用户要求"不要静态数据"）。
 * 用 kotlinx-datetime 统一各端（免 5 份 actual；wasm 亦覆盖）。
 */
private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun platformNowText(): Pair<String, String> {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val mm = now.minute.toString().padStart(2, '0')
    val time = "${now.hour}:$mm"
    val date = "${now.monthNumber}月${now.dayOfMonth}日 · ${WEEK[now.dayOfWeek.ordinal]}"
    return time to date
}
