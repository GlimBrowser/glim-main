package cn.ac.oac.glimbrowser.engine

/**
 * 平台"用系统打开链接"能力（下载/外部打开）。
 * iOS=UIApplication.openURL；macOS=NSWorkspace.openURL；Android=Intent.ACTION_VIEW；桌面=java.awt.Desktop；Web=window.open。
 * 无实现平台为 no-op（不伪造行为）。
 */
expect fun openExternally(url: String): Boolean
