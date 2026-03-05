# Code Review & Dev Tooling Setup

## Type
Claude Code task

## Summary
Review all code in the repo and set up proper dev tooling. The project started as an experiment and is working, but needs the standard dev infrastructure a Java/Fabric project should have.

## Goals
- Review all source files for code quality, potential bugs, and cleanup opportunities
- Add linting (e.g. Checkstyle or SpotBugs)
- Add formatting (e.g. Spotless with google-java-format)
- Evaluate test setup — currently has `TestPositions.java` and `TestAgainstMinecraft.java` as standalone classes with custom Gradle tasks, not a proper test framework
- Consider adding JUnit 5 and converting existing tests
- Review Gradle build config for best practices
- Any other cleanup needed in source, resources, or project config

## Context
- Source is in `src/main/java/dev/danny/bluemapstructures/`
- 8 Java files, ~50 lines for core algorithm
- Build: `./gradlew build`
- Existing "tests" are standalone classes run via custom Gradle tasks (`testPositions`, `testVsMC`), not a test framework
- Java 21, Fabric mod targeting MC 1.21.1
- BlueMapAPI is `compileOnly`
