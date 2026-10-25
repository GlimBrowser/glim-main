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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "GlimBrowser"
            packageVersion = "1.0.0"
            description = "GlimBrowser"
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