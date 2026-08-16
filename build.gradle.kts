// Plugins are declared per-module rather than here with `apply false`, because a root
// `plugins { ... apply false }` block still resolves every plugin artifact — including the
// Android Gradle Plugin — which would break `-Pdeepuniverse.jvmOnly=true` builds of :core
// on machines without access to the Google Maven repository.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
