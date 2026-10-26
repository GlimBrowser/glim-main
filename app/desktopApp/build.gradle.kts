import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "cn.ac.oac.glimbrowser.MainKt"

        // JCEF（Chromium）在 macOS 需要访问 AWT 内部类
        jvmArgs += listOf(
            "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.awt.image=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.java2d=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "GlimBrowser"
            packageVersion = "1.0.0"
            description = "GlimBrowser"
            // JavaFX（java.net.http / jdk.swing.interop）与 JCEF 都需要标准模块，
            // 默认 jlink 裁掉了它们 → NoClassDefFoundError（alpha.14 崩溃）
            includeAllModules = true
            vendor = "GlimBrowser"

            macOS {
                // macOS 应用图标（.icns，由 docs/icons.png 生成）
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "cn.ac.oac.glimbrowser"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "GlimBrowser"
                upgradeUuid = "6D6F3E2A-8C41-4F0B-9A57-3B2C1D4E5F60"
            }
            linux {
                iconFile.set(project.file("icons/icon-1024.png"))
            }
        }
    }
}