// Everything that isn't Android: engines, cache, taste profile, library, sync. Shared by the Android and desktop apps.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.newpipe.extractor)
    // Android ships org.json itself; the desktop app adds the real jar.
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach { if (project.hasProperty("liveNodes")) systemProperty("liveNodes", "true") }
