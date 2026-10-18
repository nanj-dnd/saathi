// Wire types shared with the Android client (net/Wire.kt mirrors these).

export const LANGUAGES = {
  "hi-IN": "Hindi",
  "en-IN": "English (simple Indian English)",
  "ta-IN": "Tamil",
  "te-IN": "Telugu",
  "kn-IN": "Kannada",
  "ml-IN": "Malayalam",
} as const;
export type Language = keyof typeof LANGUAGES;

export interface PastStep {
  say: string;
  label: string;
  status: GuideStatus;
  app: string;
}

export interface TurnRequest {
  language: Language;
  /** What the senior said, transcribed on the phone. Absent on auto-advance turns (screen changed, no speech). */
  utterance?: string;
  /** What the senior is trying to do, as understood on an earlier turn. Empty on the first turn. */
  goal: string;
  /** Foreground app package, e.g. com.grofers.customerapp */
  app: string;
  /** Short per-app hints the device ships in assets/playbooks. */
  playbook: string;
  /** Base64 JPEG. Absent when the window is FLAG_SECURE (screenshot came back blank). */
  screenshot_b64?: string;
  screen_w: number;
  screen_h: number;
  /** Compact node tree, one node per line: `n12 Button "Add" [x1,y1,x2,y2] click` */
  tree: string;
  /** True when the device could not capture because the screen is secure (PIN/OTP). */
  secure_screen: boolean;
  history: PastStep[];
}

export type GuideStatus =
  | "next_step"
  | "done"
  | "need_info"
  | "user_must_do"
  | "cannot_help";

export type Danger = "none" | "scam_suspected" | "sensitive_screen";

/**
 * The only things Saathi may do itself, never a tap: scroll the main list down, open an app, or open
 * an app's install page in the Play Store. "" is none. The phone checks it can before doing it.
 */
export type GuideAction = "" | "scroll_down" | "open_app" | "open_store_page";
const ACTIONS = new Set<GuideAction>(["scroll_down", "open_app", "open_store_page"]);
/** An Android package name: com.whatsapp, in.swiggy.android. */
const PACKAGE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;

export interface Guide {
  say: string;
  goal: string;
  target_kind: "node" | "point" | "none";
  node_id: string;
  x: number;
  y: number;
  label: string;
  status: GuideStatus;
  danger: Danger;
  expect: string;
  /** Something Saathi does for the senior instead of asking them to; see [GuideAction]. */
  action?: GuideAction;
  /** The package for open_app / open_store_page. */
  action_arg?: string;
}

/** Who answered: the model, the shared cache, or a dev stand-in. The phone adds its own sources. */
export type Source = "model" | "cache" | "demo" | "mock";

export interface TurnResponse {
  guide: Guide;
  timings_ms: Record<string, number>;
  source: Source;
}

// Structured-output schema. No nullable fields: "none" targets use node_id "" and x/y -1.
export const GUIDE_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: [
    "say",
    "goal",
    "target_kind",
    "node_id",
    "x",
    "y",
    "label",
    "status",
    "danger",
    "expect",
    "action",
    "action_arg",
  ],
  properties: {
    say: {
      type: "string",
      description:
        "What to speak to the senior, in their language. One instruction, under 15 words.",
    },
    goal: {
      type: "string",
      description:
        "The senior's overall task in short English, e.g. 'order 1 litre milk on Blinkit'. Carry forward the previous goal unless they changed it.",
    },
    target_kind: { type: "string", enum: ["node", "point", "none"] },
    node_id: {
      type: "string",
      description: "Node id from the tree (e.g. n12) when target_kind is node, else empty.",
    },
    x: {
      type: "number",
      description: "0..1 fraction of screenshot width when target_kind is point, else -1.",
    },
    y: {
      type: "number",
      description: "0..1 fraction of screenshot height when target_kind is point, else -1.",
    },
    label: {
      type: "string",
      description: "Short on-screen label of the target as written on the screen, or empty.",
    },
    status: {
      type: "string",
      enum: ["next_step", "done", "need_info", "user_must_do", "cannot_help"],
    },
    danger: { type: "string", enum: ["none", "scam_suspected", "sensitive_screen"] },
    expect: {
      type: "string",
      description: "In English, what the screen should show after the senior does this step.",
    },
    action: {
      type: "string",
      enum: ["none", "scroll_down", "open_app", "open_store_page"],
      description: "Something Saathi does itself instead of asking the senior. Usually none.",
    },
    action_arg: {
      type: "string",
      description: "Android package name for open_app or open_store_page (e.g. com.whatsapp), else empty.",
    },
  },
} as const;

const STATUSES = new Set<GuideStatus>([
  "next_step",
  "done",
  "need_info",
  "user_must_do",
  "cannot_help",
]);

export function parseTurnRequest(body: unknown): TurnRequest {
  if (typeof body !== "object" || body === null) throw new BadRequest("body must be an object");
  const b = body as Record<string, unknown>;
  const str = (k: string, max: number): string => {
    const v = b[k] ?? "";
    if (typeof v !== "string") throw new BadRequest(`${k} must be a string`);
    if (v.length > max) throw new BadRequest(`${k} too long`);
    return v;
  };
  const optStr = (k: string, max: number): string | undefined =>
    b[k] === undefined || b[k] === null || b[k] === "" ? undefined : str(k, max);
  const num = (k: string): number => {
    const v = b[k];
    if (typeof v !== "number" || !Number.isFinite(v)) throw new BadRequest(`${k} must be a number`);
    return v;
  };

  const language = str("language", 8) as Language;
  if (!(language in LANGUAGES)) throw new BadRequest("unsupported language");

  const rawHistory = Array.isArray(b.history) ? b.history.slice(-8) : [];
  const history: PastStep[] = rawHistory.map((h) => {
    const s = (h ?? {}) as Record<string, unknown>;
    const status = String(s.status ?? "next_step") as GuideStatus;
    return {
      say: String(s.say ?? "").slice(0, 300),
      label: String(s.label ?? "").slice(0, 100),
      status: STATUSES.has(status) ? status : "next_step",
      app: String(s.app ?? "").slice(0, 120),
    };
  });

  return {
    language,
    utterance: optStr("utterance", 1000)?.trim(),
    goal: str("goal", 300),
    app: str("app", 120),
    playbook: str("playbook", 4000),
    screenshot_b64: optStr("screenshot_b64", 2_000_000),
    screen_w: num("screen_w"),
    screen_h: num("screen_h"),
    tree: str("tree", 60_000),
    secure_screen: b.secure_screen === true,
    history,
  };
}

/** Validates model output against the contract and normalises target fields. */
export function normaliseGuide(raw: unknown, nodeIds: Set<string>): Guide {
  const g = (raw ?? {}) as Record<string, unknown>;
  const status = String(g.status) as GuideStatus;
  const danger = String(g.danger) as Danger;
  let kind = String(g.target_kind) as Guide["target_kind"];
  let nodeId = String(g.node_id ?? "");
  let x = typeof g.x === "number" ? g.x : -1;
  let y = typeof g.y === "number" ? g.y : -1;

  if (kind === "node" && !nodeIds.has(nodeId)) kind = "none";
  if (kind === "point" && !(x >= 0 && x <= 1 && y >= 0 && y <= 1)) kind = "none";
  if (kind !== "node") nodeId = "";
  if (kind !== "point") {
    x = -1;
    y = -1;
  }

  // Actions are harmless moves, but still never on a warning or a screen that is the senior's alone,
  // never with something to point at (then the senior acts), and only with a real package name.
  let action = String(g.action ?? "none") as GuideAction;
  let actionArg = String(g.action_arg ?? "").trim();
  if (!ACTIONS.has(action)) action = "";
  const risky = danger === "scam_suspected" || danger === "sensitive_screen" || status === "user_must_do";
  if (action && (risky || kind !== "none")) action = "";
  if (action && action !== "scroll_down" && !PACKAGE.test(actionArg)) action = "";
  if (action !== "open_app" && action !== "open_store_page") actionArg = "";

  return {
    say: String(g.say ?? "").trim(),
    goal: String(g.goal ?? "").trim(),
    target_kind: kind,
    node_id: nodeId,
    x,
    y,
    label: String(g.label ?? ""),
    status: STATUSES.has(status) ? status : "next_step",
    danger: danger === "scam_suspected" || danger === "sensitive_screen" ? danger : "none",
    expect: String(g.expect ?? ""),
    action,
    action_arg: actionArg,
  };
}

export function nodeIdsFromTree(tree: string): Set<string> {
  const ids = new Set<string>();
  for (const line of tree.split("\n")) {
    const m = /^\s*(n\d+)\s/.exec(line);
    if (m) ids.add(m[1]);
  }
  return ids;
}

export class BadRequest extends Error {}
