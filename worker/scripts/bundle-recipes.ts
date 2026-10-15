/// <reference types="node" />
// Packs shared/recipes/*.json into src/recipe-bundle.json, which GET /recipes serves to phones.
// Runs before `npm run dev` and `npm run deploy`; a test fails if the committed bundle is stale.
//   npm run recipes

import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export function buildBundle(dir = join(import.meta.dirname, "../../shared/recipes")) {
  const files = readdirSync(dir).filter((f) => f.endsWith(".json")).sort();
  const read = (f: string) => JSON.parse(readFileSync(join(dir, f), "utf8"));
  return {
    common: read("_common.json"),
    // Example phrases are for tests; phones don't need to download them.
    recipes: files.filter((f) => f !== "_common.json").map((f) => {
      const { examples: _, ...recipe } = read(f);
      return recipe;
    }),
  };
}

if (import.meta.filename === process.argv[1]) {
  const out = join(import.meta.dirname, "../src/recipe-bundle.json");
  const bundle = buildBundle();
  writeFileSync(out, JSON.stringify(bundle) + "\n");
  console.log(`${bundle.recipes.length} recipes -> src/recipe-bundle.json`);
}
