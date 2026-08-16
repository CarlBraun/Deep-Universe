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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Deep-Universe"

include(":core")

// The :app module needs the Android SDK and the Google Maven repo. On machines that
// have neither (CI sandboxes, headless build agents) the pure-Kotlin :core module can
// still be built and tested on its own:
//
//     gradle -Pdeepuniverse.jvmOnly=true :core:test
//
// Android Studio and any normal build include both modules.
if (providers.gradleProperty("deepuniverse.jvmOnly").orNull != "true") {
    include(":app")
}
