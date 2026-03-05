# Dev Setup

## Prerequisites

- Java 21
- No IDE-specific setup needed — the project uses Gradle wrapper

## Build Commands

```bash
./gradlew build          # full pipeline: compile + format check + tests + JAR
./gradlew check          # compile + format check + tests (no JAR)
./gradlew test           # run JUnit tests only
./gradlew spotlessCheck  # verify formatting only
./gradlew spotlessApply  # auto-fix formatting
```

The built JAR is at `build/libs/bluemap-structures-<version>.jar`.

`build` depends on `check`, which depends on both `spotlessCheck` and `test`. So running `build` always verifies formatting and runs tests before producing the JAR.

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
build.gradle              — Build config, dependencies, plugins
gradle.properties         — Version numbers (MC, Fabric, mod version, etc.)
settings.gradle           — Project name
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
| Minecraft 1.21.11 | compile | Game classes (mapped via Yarn) |
| Fabric Loader | runtime | Mod loading |
| Fabric API | runtime | Lifecycle events, registry access |
| BlueMapAPI 2.7.2 | compileOnly | Marker creation (optional at runtime) |
| JUnit Jupiter 5.10.3 | test | Test framework |

Versions are defined in `gradle.properties`.

## Configuration

The mod creates `config/bluemap-structures.json` on the server with:
- `radiusBlocks` — How far from origin to search (default 5000)
- `enabledStructures` — Per-structure-type toggle (all enabled by default)

The config auto-migrates when new structure types are added.
