/// <reference types="node" />
// Fills the Worker's speech cache with every fixed line Bob can say, in every language, so the
// natural voice plays at once on every phone instead of waiting on ElevenLabs the first time a line is
// heard. Lines already cached cost nothing, so it is safe to run again after recipes change.
//   node --experimental-strip-types scripts/prewarm-speech.ts [https://saathi-worker.example.workers.dev]
//
// Lines: every recipe step without a {label}/{screen} fill-in; the shared "open/drawer/find" lines and
// the app's "opening / not on this phone / already here" lines for every recipe's app name; and the
// spoken lines in the Android strings (wait, scrolling, network…). All at normal speed.

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { buildBundle } from "./bundle-recipes.ts";

const BASE = (process.argv.slice(2).find((a) => a.startsWith("http")) ?? "https://saathi-worker.nanj-dnd.workers.dev").replace(/\/$/, "");
const LANGS = ["hi-IN", "en-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN"] as const;
type Lang = (typeof LANGS)[number];
/** /speak allows 400 new lines per phone per day; a fresh registration is used before that. */
const PER_TOKEN = 350;
/** Lines in flight at once; PREWARM_CONCURRENCY overrides. ElevenLabs's 429s are retried. */
const CONCURRENCY = Number(process.env.PREWARM_CONCURRENCY) || 3;

const root = join(import.meta.dirname, "../..");
const bundle = buildBundle();

function androidLines(): Map<Lang, string[]> {
  const dirs: Record<Lang, string> = { "en-IN": "values", "hi-IN": "values-hi", "ta-IN": "values-ta", "te-IN": "values-te", "kn-IN": "values-kn", "ml-IN": "values-ml" };
  const out = new Map<Lang, string[]>();
  for (const lang of LANGS) {
    const xml = readFileSync(join(root, "android/app/src/main/res", dirs[lang], "strings.xml"), "utf8");
    const lines = [...xml.matchAll(/<string name="(say_[a-z_]+|voice_sample)">([^<]*)<\/string>/g)]
      .map((m) => m[2].replace(/\\'/g, "'").replace(/\\"/g, '"').trim())
      .filter((t) => t && !t.includes("%"));
    out.set(lang, lines);
  }
  return out;
}

/** The "{app}" line maps defined in RecipeEngine.kt (OPENING, NOT_INSTALLED, ALREADY). */
function engineMaps(): Record<string, string>[] {
  const kt = readFileSync(join(root, "android/app/src/main/java/app/saathi/recipes/RecipeEngine.kt"), "utf8");
  return ["OPENING", "NOT_INSTALLED", "ALREADY"].map((name) => {
    const block = kt.slice(kt.indexOf(`val ${name} = mapOf(`)).split("\n)")[0];
    return Object.fromEntries([...block.matchAll(/"([a-z]{2}-IN)" to "((?:[^"\\]|\\.)*)"/g)].map((m) => [m[1], m[2].replace(/\\'/g, "'")]));
  });
}

function allLines(): { text: string; language: Lang }[] {
  const seen = new Set<string>();
  const out: { text: string; language: Lang }[] = [];
  const add = (language: Lang, text: string | undefined) => {
    if (!text || /[{}]/.test(text)) return;
    const key = `${language}\n${text}`;
    if (seen.has(key)) return;
    seen.add(key);
    out.push({ text, language });
  };
  const apps = [...new Set(bundle.recipes.filter((r) => !r.apps.includes("*")).map((r) => r.app_label as string))];
  const appLines = [...Object.values(bundle.common.lines as Record<string, Record<string, string>>), ...engineMaps()];
  for (const lang of LANGS) {
    for (const r of bundle.recipes) for (const s of r.steps) add(lang, (s.say as Record<string, string>)[lang]);
    for (const line of appLines) for (const app of apps) add(lang, line[lang]?.replaceAll("{app}", app));
    for (const t of androidLines().get(lang) ?? []) add(lang, t);
  }
  return out;
}

async function register(): Promise<string> {
  const res = await fetch(`${BASE}/register`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
  if (!res.ok) throw new Error(`register ${res.status}`);
  return ((await res.json()) as { token: string }).token;
}

async function main() {
  const lines = allLines();
  const chars = lines.reduce((n, l) => n + l.text.length, 0);
  console.log(`${lines.length} lines to check (${chars} characters at most)`);
  if (process.argv.includes("--count")) return;
  let made = 0;
  const counts = { cache: 0, elevenlabs: 0, failed: 0 };
  let next = 0;

  // Each worker has its own registration: the Worker keeps one usage counter per phone, and its
  // storage takes about one write a second per counter, so parallel lines must not share one.
  interface Lane { token: string; sinceToken: number }

  async function speakOne(lane: Lane, line: { text: string; language: Lang }, attempt = 1): Promise<void> {
    if (lane.sinceToken >= PER_TOKEN) {
      lane.token = await register();
      lane.sinceToken = 0;
    }
    const token = lane.token;
    const res = await fetch(`${BASE}/speak`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ text: line.text, language: line.language, pace: 1 }),
    });
    await res.arrayBuffer();
    if (res.ok) {
      const source = res.headers.get("X-Saathi-Source");
      if (source === "elevenlabs") {
        counts.elevenlabs++;
        lane.sinceToken++;
        made += line.text.length;
      } else counts.cache++;
      return;
    }
    if (res.status === 429) {
      lane.token = await register();
      lane.sinceToken = 0;
    }
    if (attempt < 3 && (res.status === 429 || res.status >= 500)) {
      await new Promise((r) => setTimeout(r, 1500 * attempt));
      return speakOne(lane, line, attempt + 1);
    }
    counts.failed++;
    console.error(`failed ${res.status}: [${line.language}] ${line.text.slice(0, 60)}`);
  }

  async function worker() {
    const lane: Lane = { token: await register(), sinceToken: 0 };
    while (next < lines.length) {
      const line = lines[next++];
      await speakOne(lane, line);
      const done = counts.cache + counts.elevenlabs + counts.failed;
      if (done % 100 === 0) console.log(`${done}/${lines.length}  new ${counts.elevenlabs}  cached ${counts.cache}  failed ${counts.failed}`);
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  console.log(`done: ${counts.elevenlabs} new (${made} characters), ${counts.cache} already cached, ${counts.failed} failed`);
}

await main();
