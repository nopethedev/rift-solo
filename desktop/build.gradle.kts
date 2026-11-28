import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Rift Solo for Windows, macOS and Linux: the same engines, cache, taste and sync as the phone (:core), a desktop UI,
// Lavaplayer for audio (pure Java + its bundled natives) and Discord "Listening to" presence.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.mp)
    alias(libs.plugins.kotlin.serialization)
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val appVersion = "0.4.0"

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil.compose.mp)
    implementation(libs.coil.network)
    implementation(libs.json)
    implementation(libs.lavaplayer)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "com.nopethedev.riftsolo.desktop.MainKt"
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Rift Solo"
            // Installers need MAJOR >= 1 (macOS refuses 0.x); the app shows [appVersion].
            packageVersion = "1." + appVersion.substringAfter('.')
            description = "Music player: search through Lavalink nodes, play straight from the source"
            vendor = "nopethedev"
            licenseFile.set(rootProject.file("LICENSE"))
            // java.net.http and jdk.unsupported are used by OkHttp/NewPipe and Lavaplayer; java.sql by Lavaplayer's deps.
            modules("java.net.http", "jdk.unsupported", "java.sql", "java.naming", "java.management", "jdk.crypto.ec")
            linux { packageName = "rift-solo"; shortcut = true; menuGroup = "AudioVideo" }
            windows { menu = true; shortcut = true; dirChooser = true; upgradeUuid = "8c0f3f3a-2e8b-4f0e-9b1e-5d7a3b9f6c21" }
            macOS { bundleID = "com.nopethedev.riftsolo" }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

tasks.named<ProcessResources>("processResources") {
    // Discord application id for Rich Presence: -PdiscordAppId=... or the DISCORD_APP_ID env (CI secret).
    val discordAppId = (findProperty("discordAppId") as String?) ?: System.getenv("DISCORD_APP_ID") ?: ""
    inputs.property("discordAppId", discordAppId)
    filesMatching("rift-solo.properties") { expand("version" to appVersion, "discordAppId" to discordAppId) }
}
