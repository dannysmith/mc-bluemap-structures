import { chromium, type Page } from "playwright";
import { parseArgs } from "util";
import { mkdirSync, writeFileSync } from "fs";
import { dirname } from "path";

// --- CLI Args ---

const { values: args } = parseArgs({
  args: process.argv.slice(2),
  options: {
    seed: { type: "string" },
    radius: { type: "string", default: "10000" },
    version: { type: "string", default: "java_1_21_9" },
    output: { type: "string" },
    headless: { type: "boolean", default: false },
  },
});

if (!args.seed) {
  console.error("Usage: bun run extract.ts --seed=<seed> [--radius=10000] [--version=java_1_21_9] [--output=path] [--headless]");
  process.exit(1);
}

const seed = args.seed;
const radius = parseInt(args.radius!, 10);
const version = args.version!;
const outputPath = args.output ?? `data/chunkbase-seed-${seed}.json`;
const headless = args.headless ?? false;

// --- POI Config ---

// Checkboxes to always skip (display text substrings)
const ALWAYS_SKIP = [
  "Cave", "Ravine", "Lava Pool", "Geode", "Apple",
  "Highlight biomes", "Grid Lines", "Terrain",
  "Slime Chunk", "Ore Veins", "Desert Well", "Fossil",
];

// Per-dimension: which checkbox texts to ENABLE
const DIMENSION_POIS: Record<string, string[]> = {
  overworld: [
    "Village", "Ancient City", "Stronghold", "Mansion", "Monument",
    "Outpost", "Ruined Portal", "Jungle Temple", "Desert Temple",
    "Witch Hut", "Treasure", "Shipwreck", "Igloo", "Ocean Ruins",
    "Trail Ruins", "Trial Chamber", "Dungeon",
  ],
  nether: [
    "Nether Fortress", "Bastion", "Ruined Portal",
  ],
  end: [
    "End City",
  ],
};

// Per-dimension: which checkbox texts to explicitly DISABLE
// (to avoid carrying over from a previous dimension scan)
const DIMENSION_DISABLE: Record<string, string[]> = {
  overworld: ["Nether Fortress", "Bastion", "End City", "End Gateway", "Nether Fossil"],
  nether: [
    "Village", "Ancient City", "Stronghold", "Mansion", "Monument",
    "Outpost", "Jungle Temple", "Desert Temple", "Witch Hut",
    "Treasure", "Shipwreck", "Igloo", "Ocean Ruins", "Trail Ruins",
    "Trial Chamber", "Dungeon", "End City", "End Gateway",
  ],
  end: [
    "Village", "Ancient City", "Stronghold", "Mansion", "Monument",
    "Outpost", "Jungle Temple", "Desert Temple", "Witch Hut",
    "Treasure", "Shipwreck", "Igloo", "Ocean Ruins", "Trail Ruins",
    "Trial Chamber", "Dungeon", "Nether Fortress", "Bastion", "Nether Fossil",
  ],
};

interface Structure {
  type: string;
  x: number;
  y: number | null;
  z: number;
  details: unknown;
}

// --- Main ---

async function main() {
  console.log(`Extracting structures for seed ${seed}, radius ${radius}, version ${version}`);
  console.log(`Output: ${outputPath}`);

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await context.newPage();

  const allStructures: Structure[] = [];

  for (const dimension of ["overworld", "nether", "end"] as const) {
    console.log(`\n=== Scanning ${dimension} ===`);
    const structures = await scanDimension(page, dimension);
    console.log(`  Collected ${structures.length} structures from ${dimension}`);
    allStructures.push(...structures);
  }

  await browser.close();

  // Write output
  const output = {
    seed,
    version,
    radius,
    extractedAt: new Date().toISOString(),
    structures: allStructures,
  };

  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, JSON.stringify(output, null, 2));

  console.log(`\nDone! Wrote ${allStructures.length} structures to ${outputPath}`);

  // Summary by type
  const counts: Record<string, number> = {};
  for (const s of allStructures) {
    counts[s.type] = (counts[s.type] ?? 0) + 1;
  }
  console.log("\nStructures by type:");
  for (const [type, count] of Object.entries(counts).sort((a, b) => b[1] - a[1])) {
    console.log(`  ${type}: ${count}`);
  }
}

async function scanDimension(page: Page, dimension: string): Promise<Structure[]> {
  // Navigate to Chunkbase with seed + dimension
  const url = `https://www.chunkbase.com/apps/seed-map#seed=${seed}&platform=${version}&dimension=${dimension}&x=0&z=0&zoom=0.5`;
  console.log(`  Navigating to ${url}`);
  await page.goto(url);
  await page.waitForTimeout(10_000); // Wait for WASM + initial tile computation

  // Dismiss cookie consent
  await dismissCookieConsent(page);

  // Configure checkboxes for this dimension
  await configureCheckboxes(page, dimension);
  await page.waitForTimeout(10_000); // Wait for workers to recompute with new POIs

  // Disable zoom-level filtering so all POI types (including dungeons) get drawn at zoom 0.5
  await page.evaluate(() => {
    if ((window as any).CB3FinderAppConfig) {
      (window as any).CB3FinderAppConfig.filterPoisByZoomLevel = false;
    }
  });

  // Install collection hook
  await page.evaluate(() => {
    (window as any)._collected = new Map();
    (window as any)._origOnPoiDrawn = (window as any).CB3TooltipManager.onPoiDrawn;
    (window as any).CB3TooltipManager.onPoiDrawn = function (
      type: string, repr: string, coords: number[], details: any[],
      _canvasX: number, _canvasY: number, _iconW: number, _iconH: number, _clip: number[]
    ) {
      if (!(window as any)._collected.has(repr)) {
        (window as any)._collected.set(repr, {
          type,
          x: coords[0],
          y: coords[1],
          z: coords[2],
          details: details && details[2] ? details[2] : null,
        });
      }
      return (window as any)._origOnPoiDrawn.apply(this, arguments);
    };
  });

  // Scan grid
  const step = 3000;
  const positions: [number, number][] = [];
  for (let x = -radius; x <= radius; x += step) {
    for (let z = -radius; z <= radius; z += step) {
      positions.push([x, z]);
    }
  }

  console.log(`  Scanning ${positions.length} grid positions (step=${step})...`);

  for (let i = 0; i < positions.length; i++) {
    const [px, pz] = positions[i];

    await page.evaluate(([x, z]) => {
      const hash = window.location.hash;
      let newHash = hash.replace(/x=[^&]*/, `x=${x}`).replace(/z=[^&]*/, `z=${z}`);
      // If x= or z= weren't in the hash, add them
      if (!newHash.includes("x=")) newHash += `&x=${x}`;
      if (!newHash.includes("z=")) newHash += `&z=${z}`;
      window.location.hash = newHash;
      (window as any).CB3Router.applyCurrentUrl();
    }, [px, pz]);

    // Wait for worker tile computation
    await page.waitForTimeout(3000);

    // Force redraw to trigger onPoiDrawn for visible structures
    await page.evaluate(() => {
      (window as any).CB3FinderApp.trigger("redrawmap");
    });

    // Wait for paint cycle
    await page.waitForTimeout(2000);

    if ((i + 1) % 10 === 0 || i === positions.length - 1) {
      const count = await page.evaluate(() => (window as any)._collected.size);
      console.log(`  Progress: ${i + 1}/${positions.length} positions, ${count} structures collected`);
    }
  }

  // Collect results and restore hook
  const structures: Structure[] = await page.evaluate(() => {
    (window as any).CB3TooltipManager.onPoiDrawn = (window as any)._origOnPoiDrawn;
    const arr = Array.from((window as any)._collected.values());
    delete (window as any)._collected;
    delete (window as any)._origOnPoiDrawn;
    return arr as any;
  });

  return structures;
}

async function dismissCookieConsent(page: Page) {
  try {
    const acceptBtn = page.getByRole("button", { name: "Accept all" });
    if (await acceptBtn.isVisible({ timeout: 3000 })) {
      await acceptBtn.click();
      console.log("  Dismissed cookie consent");
      await page.waitForTimeout(500);
    }
  } catch {
    // No dialog, that's fine
  }
}

async function configureCheckboxes(page: Page, dimension: string) {
  const toEnable = DIMENSION_POIS[dimension] ?? [];
  const toDisable = DIMENSION_DISABLE[dimension] ?? [];
  const alwaysSkip = ALWAYS_SKIP;

  const checkboxReport = await page.evaluate(({ toEnable, toDisable, alwaysSkip }) => {
    const enabled: string[] = [];
    const disabled: string[] = [];
    const unchanged: string[] = [];
    const checkboxes = document.querySelectorAll('[role="checkbox"]');
    checkboxes.forEach((cb) => {
      const text = cb.textContent?.trim() ?? "";
      const isChecked = cb.getAttribute("aria-checked") === "true";

      // Should this be enabled?
      const shouldEnable = toEnable.some((s: string) => text.includes(s));
      // Should this be disabled?
      const shouldDisable = toDisable.some((s: string) => text.includes(s))
        || alwaysSkip.some((s: string) => text.includes(s));

      if (shouldEnable && !shouldDisable && !isChecked) {
        (cb as HTMLElement).click();
        enabled.push(text);
      } else if (shouldDisable && isChecked) {
        (cb as HTMLElement).click();
        disabled.push(text);
      } else {
        unchanged.push(`${text} (${isChecked ? "on" : "off"})`);
      }
    });
    return { enabled, disabled, unchanged };
  }, { toEnable, toDisable, alwaysSkip });

  console.log(`  Checkboxes enabled: [${checkboxReport.enabled.join(", ")}]`);
  if (checkboxReport.disabled.length) {
    console.log(`  Checkboxes disabled: [${checkboxReport.disabled.join(", ")}]`);
  }
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
