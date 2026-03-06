# Chunkbase Verification Tool

Standalone Bun + Playwright tool for extracting structure positions from Chunkbase. Part of the `mc-bluemap-structures` project but independent of the Java mod.

## Stack
- Runtime: Bun
- Browser automation: Playwright (Chromium)
- Language: TypeScript

## Key files
- `extract.ts` — CLI extraction script
- `data/` — Output JSON files (gitignored)

## Reference
- `docs/chunkbase-extraction-findings.md` — Full Chunkbase investigation and gotchas
- `docs/tasks-todo/task-4-verify-against-server.md` — Parent task with phased plan
