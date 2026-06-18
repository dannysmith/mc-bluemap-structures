# Dev Setup

## Prerequisites

- Java 21 (for the MC 1.21.11 target) and Java 25 (for the MC 26.2 target). The Gradle toolchain
  with the foojay resolver auto-provisions whichever is missing.
- No IDE-specific setup needed — the project uses the Gradle wrapper.

This is a multi-version build via Stonecutter — see `multiversion.md`. One source tree builds both
Minecraft versions; commands below act on all versions unless noted.

## Build Commands

```bash
./gradlew chiseledBuild  # build + test every version, collect jars to build/libs/<version>/
./gradlew build          # build + test every version (jars under versions/<id>/build/libs/)
./gradlew check          # compile + tests, all versions (no JAR)
./gradlew test           # run JUnit tests only, all versions
./gradlew spotlessCheck  # verify formatting (repo-root task over src/)
./gradlew spotlessApply  # auto-fix formatting
```

`chiseledBuild` collects per-version jars as `build/libs/<version>/bluemap-structures-<version>+<mc>.jar`.

Formatting is **not** wired into the per-version `check` (Stonecutter's shared source root conflicts
with spotless's project-dir guard); run `spotlessApply`/`spotlessCheck` explicitly. They operate on
`src/` from the repo root.

## Formatting

Code formatting is enforced by [Spotless](https://github.com/diffplug/spotless) using [google-java-format](https://github.com/google/google-java-format) (Google style, 2-space indent).

**Typical workflow:**

1. Write code
2. Run `./gradlew spotlessApply` to auto-format
3. Commit

If you forget to format, `./gradlew build` will fail at the `spotlessCheck` step and tell you which files need formatting. Run `spotlessApply` and try again.

The formatting is non-configurable by design — google-java-format is opinionated and doesn't support custom rules. This eliminates style debates.

## Project Structure

```
build.gradle.kts          — Per-version build config (mappings, deps, toolchain, jar tasks)
settings.gradle.kts       — Declares the MC versions + Stonecutter/Loom plugins
stonecutter.gradle.kts    — Root build: active version, spotless, chiseledBuild
stonecutter.properties.toml — Per-version dependency strings + mod metadata
gradle.properties         — Gradle JVM options only
.editorconfig             — Editor defaults (charset, indent, whitespace)

src/main/java/            — Mod source code
src/main/resources/       — fabric.mod.json, structure icons
src/test/java/            — JUnit 5 tests

docs/                     — Developer documentation
  architecture.md         — Mod structure and design decisions
  structure-algorithm.md  — How the position algorithm works
  testing.md              — Test guide
  dev-setup.md            — This file
  tasks.md                — Task management
  tasks-todo/             — Pending tasks
  tasks-done/             — Completed tasks
  archive/                — Original research and planning docs
```

## Dependencies

| Dependency | Scope | Purpose |
|-----------|-------|---------|
| Minecraft 1.21.11 / 26.2 | compile | Game classes (Mojang mappings for both) |
| Fabric Loader | runtime | Mod loading |
| Fabric API | runtime | Lifecycle events, registry access |
| BlueMapAPI 2.7.2 / 2.8.0 | compileOnly | Marker creation (optional at runtime) |
| JUnit Jupiter 5.10.3 | test | Test framework |

Per-version dependency strings are defined in `stonecutter.properties.toml`.

## Configuration

The mod creates `config/bluemap-structures.json` on the server with:
- `radiusBlocks` — How far from origin to search (default 5000)
- `enabledStructures` — Per-structure-type toggle (all enabled by default)

The config auto-migrates when new structure types are added.
