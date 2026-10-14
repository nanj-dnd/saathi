// Replays saved screens against a running Worker and prints what Saathi would say.
//   npm run dev            (in another terminal)
//   npm run fixtures [-- name] [--lang ta-IN] [--audio]
// A fixture is fixtures/<name>.json (a TurnRequest minus screenshot) plus an optional fixtures/<name>.jpg.
// Capture new ones on a device with the app's debug "Save fixture" action (see android/README).

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const WORKER = process.env.WORKER_URL ?? "http://localhost:8787";
const args = process.argv.slice(2);
const langIdx = args.indexOf("--lang");
const lang = langIdx >= 0 ? args[langIdx + 1] : undefined;
const wantAudio = args.includes("--audio");
const only = args.find((a, i) => !a.startsWith("--") && args[i - 1] !== "--lang");

const dir = "fixtures";
const names = readdirSync(dir)
  .filter((f) => f.endsWith(".json"))
  .map((f) => f.slice(0, -5))
  .filter((n) => !only || n === only);

const { token } = (await (await fetch(`${WORKER}/register`, { method: "POST" })).json()) as { token: string };
mkdirSync("out", { recursive: true });

for (const name of names) {
  const req = JSON.parse(readFileSync(join(dir, `${name}.json`), "utf8"));
  const jpg = join(dir, `${name}.jpg`);
  if (existsSync(jpg)) req.screenshot_b64 = readFileSync(jpg).toString("base64");
  if (lang) req.language = lang;
  req.want_audio = wantAudio;

  const res = await fetch(`${WORKER}/turn`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  const body = (await res.json()) as Record<string, any>;
  if (!res.ok) {
    console.log(`✗ ${name} ${res.status}`, body);
    continue;
  }
  const g = body.guide;
  const target =
    g.target_kind === "node" ? g.node_id : g.target_kind === "point" ? `(${g.x.toFixed(2)},${g.y.toFixed(2)})` : "-";
  console.log(`\n● ${name} [${req.language}] ${JSON.stringify(body.timings_ms)}`);
  console.log(`  goal:   ${g.goal}`);
  console.log(`  say:    ${g.say}`);
  console.log(`  target: ${target} "${g.label}"  status=${g.status} danger=${g.danger}`);
  console.log(`  expect: ${g.expect}`);
  if (body.audio_b64) {
    writeFileSync(join("out", `${name}.${req.language}.wav`), Buffer.from(body.audio_b64, "base64"));
  }
}
