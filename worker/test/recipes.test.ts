import { describe, expect, it } from "vitest";
import { buildBundle } from "../scripts/bundle-recipes.ts";
import bundle from "../src/recipe-bundle.json";
import { RECIPE_LANGUAGES } from "../src/schema.ts";

// The full behaviour of recipes is tested on the phone side (android RecipeEngineTest), which
// runs them. Here: the bundle the Worker serves is current and every recipe is complete.

const langs: string[] = [...RECIPE_LANGUAGES];

describe("recipe bundle", () => {
  it("matches shared/recipes (run `npm run recipes` after editing a recipe)", () => {
    expect(JSON.parse(JSON.stringify(buildBundle()))).toEqual(bundle);
  });

  it("has every common line in every language", () => {
    for (const name of ["open", "drawer", "find"]) {
      const line = (bundle.common.lines as Record<string, Record<string, string>>)[name];
      for (const l of langs) expect(line?.[l], `${name} ${l}`).toBeTruthy();
    }
  });

  it("has unique ids and goals, and every step speaks every language", () => {
    const ids = bundle.recipes.map((r) => r.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(bundle.recipes.map((r) => r.goal)).size).toBe(ids.length);
    for (const r of bundle.recipes) {
      const stepIds = new Set(r.steps.map((s) => s.id));
      for (const s of r.steps as Array<{ id: string; say: Record<string, string>; after?: { step: string } }>) {
        for (const l of langs) expect(s.say[l], `${r.id}/${s.id} ${l}`).toBeTruthy();
        if (s.after) expect(stepIds.has(s.after.step), `${r.id}/${s.id} after`).toBe(true);
      }
    }
  });

  it("leaves out the test phrases", () => {
    expect(bundle.recipes.some((r) => "examples" in r)).toBe(false);
  });
});
