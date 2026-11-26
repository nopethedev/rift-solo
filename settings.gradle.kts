pluginManagement {
    repositories { google(); maven("https://repo1.maven.org/maven2"); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); maven("https://repo1.maven.org/maven2"); mavenCentral()
        // NewPipe Extractor (audio extraction on the phone) is only published on JitPack.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") } }
    }
}
rootProject.name = "RiftSolo"
include(":app")
