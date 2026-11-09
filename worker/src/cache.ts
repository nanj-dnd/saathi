import { nodeIdsFromTree, normaliseGuide, type Guide, type TurnRequest } from "./schema.ts";

// Answers shared between phones. When a hundred seniors make their text bigger on the same
// Settings screen, the model only needs to see it once. Phones also keep their own cache
// (android brain/TurnCache.kt); this one covers the first time a phone meets a screen.
//
// What may be shared is deliberately narrow, because the stored answer was written for someone
// else's screen:
// - only apps whose screens are the same for everyone (Settings, shopping catalogues), never
//   chats, payments, government or banking apps;
// - only turns where the senior said nothing (the words are theirs, and change the answer);
// - only when the screen's text matches exactly, so any text the answer repeats was already on
//   the new screen too;
// - never sensitive or scam-looking screens, warnings, questions, or answers containing numbers.

export const SHARED_CACHE_APPS = new Set([
  "com.android.settings",
  "com.grofers.customerapp",
  "com.zeptoconsumerapp",
  "in.swiggy.android",
  "com.application.zomato",
]);

export const SHARED_TTL_S = 7 * 24 * 60 * 60;
const MIN_LABELLED = 5;
const GRID = 50;
const LINE = /^(n\d+) (\S+) "(.*)" \[(-?\d+),(-?\d+),(-?\d+),(-?\d+)\](.*)$/;

const SENSITIVE =
  /\botp\b|one[- ]time|upi pin|\bpin\b|\bcvv\b|password|passcode|card number|ओटीपी|पिन|पासवर्ड|ஓடிபி|ఓటీపీ|ಒಟಿಪಿ|ഒടിപി/i;
const SCAM =
  /anydesk|teamviewer|quicksupport|rustdesk|airdroid|screen ?shar|digital arrest|\bkyc\b|lottery|jackpot|you have won|prize money|lucky draw|\bcbi\b|narcotics|customs duty|collect request|request money|केवाईसी|लॉटरी|इनाम|डिजिटल अरेस्ट/i;

interface Line {
  id: string;
  role: string;
  label: string;
  box: [number, number, number, number];
  flags: string;
}

function parse(tree: string): Line[] {
  const out: Line[] = [];
  for (const raw of tree.split("\n")) {
    const m = LINE.exec(raw.trim());
    if (!m) continue;
    out.push({ id: m[1], role: m[2], label: m[3], box: [+m[4], +m[5], +m[6], +m[7]], flags: m[8].trim() });
  }
  return out;
}

/** True when the tree has enough text to identify the screen; otherwise the model read the picture. */
export function informative(tree: string): boolean {
  return parse(tree).filter((l) => l.label.trim() !== "").length >= MIN_LABELLED;
}

/** The tree with bounds rounded to a coarse grid, so small layout jitter and screen sizes match. */
export function screenSignature(tree: string, w: number, h: number): string {
  const sw = Math.max(1, w);
  const sh = Math.max(1, h);
  return parse(tree)
    .map((l) => {
      const [a, b, c, d] = l.box;
      const q = [a / sw, b / sh, c / sw, d / sh].map((v) => Math.floor(v * GRID)).join(",");
      return `${l.id} ${l.role} ${l.label} ${q} ${l.flags.split(" ").filter(Boolean).sort().join(",")}`;
    })
    .join("\n");
}

async function sha256(s: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** The shared-cache key for this turn, or null if its answer must not be shared. */
export async function sharedKey(req: TurnRequest): Promise<string | null> {
  if (req.utterance) return null;
  if (!SHARED_CACHE_APPS.has(req.app)) return null;
  if (req.secure_screen || !informative(req.tree)) return null;
  if (SENSITIVE.test(req.tree) || SCAM.test(req.tree)) return null;
  const parts = [
    "g4-bob",
    req.platform ?? "android",
    req.language,
    req.app,
    req.goal,
    req.playbook,
    req.app_name ?? "",
    (req.installed_apps ?? []).map((app) => [app.package, app.label]),
    screenSignature(req.tree, req.screen_w, req.screen_h),
    req.history.slice(-8).map((h) => [h.say, h.label, h.status, h.app]),
  ];
  return `guide:${await sha256(JSON.stringify(parts))}`;
}

/** Plain steps only, and nothing that might carry someone's number, price or address. */
export function shareable(g: Guide): boolean {
  return (
    typeof g.say === "string" && g.say.trim() !== "" &&
    typeof g.label === "string" && typeof g.goal === "string" && typeof g.expect === "string" &&
    g.danger === "none" &&
    (g.target_kind === "node" || g.target_kind === "none") &&
    (g.status === "next_step" || g.status === "done") &&
    !/\p{Nd}/u.test(g.say + " " + g.label)
  );
}

export async function readShared(kv: KVNamespace, key: string, req: TurnRequest): Promise<Guide | null> {
  const raw = await kv.get(key);
  if (!raw) return null;
  try {
    const g = JSON.parse(raw) as Guide;
    if (!shareable(g)) return null;
    if (g.target_kind === "node" && !nodeIdsFromTree(req.tree).has(g.node_id)) return null;
    if (g.target_kind === "node" && g.label && !parse(req.tree).some((n) => n.id === g.node_id && n.label.includes(g.label))) return null;
    return normaliseGuide(g, nodeIdsFromTree(req.tree));
  } catch {
    return null;
  }
}

export function writeShared(kv: KVNamespace, key: string, guide: Guide): Promise<void> {
  return kv.put(key, JSON.stringify(guide), { expirationTtl: SHARED_TTL_S });
}
