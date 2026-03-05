# Code Review & Dev Tooling Setup

## Type
Claude Code task

## Summary
Review all code in the repo and set up proper dev tooling. The project started as an experiment and is working, but needs the standard dev infrastructure a Java/Fabric project should have.

## Context
- Source is in `src/main/java/dev/danny/bluemapstructures/`
- 8 Java files, ~800 LOC total, ~50 lines for the core algorithm
- Build: `./gradlew build`
- Existing "tests" are standalone classes run via custom Gradle tasks (`testPositions`, `testVsMC`), not a test framework
- Java 21, Fabric mod targeting MC 1.21.1, Gradle 8.12, Fabric Loom 1.9
- BlueMapAPI is `compileOnly`

## Code Review Findings

The codebase is in good shape. Clean architecture, correct algorithms, sensible separation of concerns. No major bugs or structural issues. Specific notes:

**Strengths**: Clear single-responsibility classes, verified algorithm (tested against MC's ChunkRandom), thread-safe biome checking via BiomeSource, proper Fabric lifecycle handling, graceful config migration.

**Minor cleanup opportunities** (address during implementation):
- Magic region seed constants (`341873128712L`, `132897987541L`) lack comments explaining they match MC internals
- `.gitignore` is minimal (only `/build` and `/.gradle`) — should add IDE files, `.DS_Store`, etc.
- Test classes live in `src/main/java` instead of `src/test/java`

**Not worth changing**: The large switch statements in `StructureType` (displayName, iconFile, biomeTagIds) are fine — they're clear and maintainable at this scale. No need to refactor.

## Implementation Plan

### Phase 1: Spotless Formatting

Add Spotless plugin with google-java-format (Google style, 2-space indent).

**build.gradle changes:**
```groovy
plugins {
    id "com.diffplug.spotless" version "8.3.0"
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        importOrder()
    }
}
```

**Steps:**
1. Add Spotless plugin to `build.gradle`
2. Run `./gradlew spotlessApply` to reformat all files
3. Commit the formatting changes separately (so the reformat is one clean commit)
4. Wire `spotlessCheck` into the `check` task so `./gradlew build` catches formatting drift

**Why not Checkstyle or SpotBugs?** Checkstyle needs non-trivial XML config for rules Spotless already covers. SpotBugs catches enterprise-pattern bugs (null deref, concurrency) that aren't the risk surface here — our bugs are algorithm logic errors that static analysis can't find. Neither earns its keep on an 800-LOC solo project.

### Phase 2: JUnit 5 + Test Migration

Add JUnit 5 and convert the existing standalone test classes into proper tests.

**Key insight:** Fabric Loom automatically puts the deobfuscated Minecraft JAR on `testRuntimeClasspath`. So `TestAgainstMinecraft` (which uses `ChunkRandom`, `CheckedRandom`, `SpreadType`) works with plain JUnit 5 — no `fabric-loader-junit` needed.

**build.gradle changes:**
```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.3'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
    }
}
```

**Steps:**
1. Add JUnit 5 dependencies and test task config
2. Create `src/test/java/dev/danny/bluemapstructures/`
3. Convert `TestPositions.java` → `StructureLocatorTest.java`:
   - Test each structure type finds results within radius
   - Test fortress/bastion never overlap at same position
   - Pin known-good positions for seed 12345 as regression guards
4. Convert `TestAgainstMinecraft.java` → `ChunkRandomVerificationTest.java`:
   - Parameterized test across region grid
   - Assert our RNG output matches MC's `ChunkRandom.setRegionSeed()` exactly
5. Delete `TestPositions.java` and `TestAgainstMinecraft.java` from `src/main/java/`
6. Remove the `testPositions` and `testVsMC` JavaExec tasks from `build.gradle`
7. Verify `./gradlew test` passes

### Phase 3: Minor Cleanup

1. **Comment magic numbers** in `StructureLocator.java` — add a one-line comment explaining the region seed constants match Minecraft's internal values
2. **Expand `.gitignore`** — add IDE files (`.idea/`, `*.iml`, `.vscode/`), OS files (`.DS_Store`, `Thumbs.db`), and runtime files (`run/`)
3. **Add `.editorconfig`** — basic editor consistency (charset, indent, trailing whitespace, final newline)

### Phase 4: Developer Documentation

Create focused reference docs in `docs/` for humans and AI agents working on the codebase. Distill from existing archive docs (`PLAN.md`, `RESEARCH.md`) rather than duplicating. Keep them evergreen and concise.

**Files to create:**

1. **`docs/architecture.md`** — How the mod is structured. Data flow from server start to markers on map. Why we implemented the algorithm ourselves instead of using SeedFinding libs. Why BiomeSource instead of `world.getBiome()`. The dimension matching approach and its limitations.

2. **`docs/structure-algorithm.md`** — How the position algorithm works. The region grid system, region seed calculation (with the magic constants explained), linear vs triangular spread, the Nether fortress/bastion shared grid and weight roll, the stronghold concentric rings algorithm. Reference to the structure set parameters table. This is the doc you'd read before touching `StructureLocator.java` or `StrongholdLocator.java`.

3. **`docs/testing.md`** — How to run tests, what each test covers, how to add new tests. How the MC classpath works in tests (Loom provides it automatically). How to verify algorithm changes against known-good positions.

4. **`docs/dev-setup.md`** — How to build, format, run checks. The Spotless workflow (`spotlessApply` vs `spotlessCheck`). What `./gradlew build` does (compile + spotlessCheck + test). How to run just formatting or just tests.

**Not creating** (overkill for this project): BlueMap API usage docs (their docs are online and sufficient), Javadoc generation, CONTRIBUTING.md, CHANGELOG.

### Build Workflow Summary

After all phases, the Gradle task graph looks like:

```
./gradlew build          → compile + spotlessCheck + test + jar (the full pipeline)
./gradlew check          → spotlessCheck + test (verify without building JAR)
./gradlew test           → run JUnit tests only
./gradlew spotlessCheck  → verify formatting only
./gradlew spotlessApply  → auto-fix formatting
```

No separate check script needed — Gradle's `check` task is the idiomatic equivalent. `build` depends on `check`, so everything runs automatically.
