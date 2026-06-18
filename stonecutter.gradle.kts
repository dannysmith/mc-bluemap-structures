plugins {
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless") version "8.3.0"
}

stonecutter active "1.21.11"

repositories {
    mavenCentral()
}

// Spotless runs once at the repo root (where the shared `src/` lives) rather than per-version
// subproject — each version's projectDir is `versions/<id>`, which spotless rejects as outside.
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        importOrder()
    }
}

// NOTE: run spotless on its own (`./gradlew spotlessApply` / `spotlessCheck`), not combined with
// `build`/`test` in one invocation. Spotless reads src/, which Stonecutter's per-version source
// processing also writes, and Gradle rejects that overlap inside a single task graph. Spotless is
// deliberately NOT wired into `check`, so a plain `./gradlew build` never pulls it in.

// `./gradlew chiseledBuild` builds every declared version and collects their jars into
// build/libs/<mod version>/. A plain `./gradlew build` also builds all versions but leaves
// each jar under versions/<id>/build/libs/.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds and collects every Minecraft version's jar."
    dependsOn(subprojects.map { "${it.path}:buildAndCollect" })
}

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    // The source is written once in Mojang mappings (loomx.applyMojangMappings() is used for
    // every target), so 1.21.11 and 26.2 compile from the same tree. Add `replacements {}` or
    // `//? if` blocks here only where the Mojmap API genuinely differs between versions.
}
