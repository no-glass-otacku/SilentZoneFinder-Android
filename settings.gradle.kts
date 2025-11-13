pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/releases/")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven ("https://jitpack.io" )
    }
}

rootProject.name = "SilentZoneFinder_Android"
include(":app")