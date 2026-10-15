import { nodeIdsFromTree, type Guide, type TurnRequest } from "./schema.ts";

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
    "g1",
    req.language,
    req.app,
    req.goal.trim().toLowerCase(),
    screenSignature(req.tree, req.screen_w, req.screen_h),
    req.history
      .slice(-2)
      .map((h) => `${h.label}|${h.status}`)
      .join(";"),
  ];
  return `guide:${await sha256(parts.join("\u0001"))}`;
}

/** Plain steps only, and nothing that might carry someone's number, price or address. */
export function shareable(g: Guide): boolean {
  return (
    g.say !== "" &&
    g.danger === "none" &&
    g.target_kind !== "point" &&
    (g.status === "next_step" || g.status === "done") &&
    !/\d{3,}/.test(g.say + " " + g.label)
  );
}

export async function readShared(kv: KVNamespace, key: string, req: TurnRequest): Promise<Guide | null> {
  const raw = await kv.get(key);
  if (!raw) return null;
  try {
    const g = JSON.parse(raw) as Guide;
    if (g.target_kind === "node" && !nodeIdsFromTree(req.tree).has(g.node_id)) return null;
    return g;
  } catch {
    return null;
  }
}

export function writeShared(kv: KVNamespace, key: string, guide: Guide): Promise<void> {
  return kv.put(key, JSON.stringify(guide), { expirationTtl: SHARED_TTL_S });
}
