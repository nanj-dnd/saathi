import FIXED from "../../shared/fixed-lines.json";
import Anthropic from "@anthropic-ai/sdk";
import { buildUserText, systemPromptFor } from "./prompt.ts";
import {
  GUIDE_JSON_SCHEMA,
  normaliseGuide,
  nodeIdsFromTree,
  type Guide,
  type TurnRequest,
} from "./schema.ts";

type Effort = "low" | "medium" | "high";

export interface GuideConfig {
  apiKey: string;
  model: string;
  effort: Effort;
}

// Haiku has no server-side refusal fallback; the Opus/Sonnet/Fable lines do.
const supportsFallbacks = (model: string) => !model.startsWith("claude-haiku");

export async function askGuide(
  cfg: GuideConfig,
  req: TurnRequest,
): Promise<Guide> {
  if (!req.tree.trim() && !req.screenshot_b64) return unreadableGuide(req);
  const client = new Anthropic({ apiKey: cfg.apiKey, maxRetries: 1, timeout: 20_000 });

  const content: Anthropic.Beta.BetaContentBlockParam[] = [];
  if (req.screenshot_b64) {
    content.push({
      type: "image",
      source: { type: "base64", media_type: "image/jpeg", data: req.screenshot_b64 },
    });
  }
  content.push({ type: "text", text: buildUserText(req) });

  const response = await client.beta.messages.create({
    model: cfg.model,
    max_tokens: 4000,
    system: [{ type: "text", text: systemPromptFor(req), cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content }],
    output_config: {
      effort: cfg.effort,
      format: { type: "json_schema", schema: GUIDE_JSON_SCHEMA as unknown as Record<string, unknown> },
    },
    ...(supportsFallbacks(cfg.model)
      ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" as const }
      : {}),
  });

  if (response.stop_reason === "refusal") {
    return safeFallback(req);
  }

  const text = response.content
    .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
    .map((b) => b.text)
    .join("");

  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return safeFallback(req);
  }
  const guide = normaliseGuide(raw, nodeIdsFromTree(req.tree));
  if (!guide.say) return safeFallback(req);
  // Enforced server-side as well as in the iOS prompt. Existing Android behavior is unchanged.
  if (req.platform === "ios") return { ...guide, action: "", action_arg: "", target_kind: "none", node_id: "", x: -1, y: -1 };
  const groups = new Map<string, NonNullable<TurnRequest["installed_apps"]>>();
  for (const app of req.installed_apps ?? []) {
    const name = app.label.trim().normalize("NFC").toLowerCase();
    groups.set(name, [...(groups.get(name) ?? []), app]);
  }
  const task = [req.utterance, req.goal].filter(Boolean).join(" ").normalize("NFC").toLowerCase();
  for (const [name, apps] of groups) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const mentioned = new RegExp(`(^|[^\\p{L}\\p{N}\\p{M}])${escaped}(?=$|[^\\p{L}\\p{N}\\p{M}])`, "u").test(task);
    if (apps.length > 1 && mentioned && !apps.some((app) => app.package === req.app || task.includes(app.package)) &&
        guide.danger === "none" && guide.status !== "user_must_do") return chooseAppGuide(req);
  }
  if (guide.target_kind === "point" && !req.screenshot_b64) return safeFallback(req);
  if (guide.action === "open_app" && req.installed_apps?.length) {
    const label = guide.label.trim().normalize("NFC").toLowerCase();
    const matches = req.installed_apps.filter((app) => app.label.trim().normalize("NFC").toLowerCase() === label);
    if (!req.installed_apps.some((app) => app.package === guide.action_arg)) {
      if (matches.length !== 1) return safeFallback(req);
      return { ...guide, action_arg: matches[0].package };
    }
  }
  return guide;
}

const APP_CHOICE = lines("app_choice");

/** A fixed line in every language (shared/fixed-lines.json), English where one is somehow missing. */
function lines(key: keyof typeof FIXED): Record<string, string> {
  const table = FIXED[key] as Record<string, string>;
  return new Proxy(table, { get: (t, tag: string) => t[tag] ?? t["en-US"] ?? t["en-IN"] });
}

function chooseAppGuide(req: TurnRequest): Guide {
  return { ...safeFallback(req), say: APP_CHOICE[req.language], goal: req.goal || req.utterance || "",
    status: "next_step", expect: "the senior opens the intended app" };
}

const UNREADABLE = lines("unreadable");

export function unreadableGuide(req: TurnRequest): Guide {
  return { ...safeFallback(req), say: req.platform === "ios" ? SORRY[req.language] : UNREADABLE[req.language], status: "cannot_help" };
}

const SORRY = lines("sorry");

export function safeFallback(req: TurnRequest): Guide {
  return {
    say: SORRY[req.language],
    goal: req.goal,
    target_kind: "none",
    node_id: "",
    x: -1,
    y: -1,
    label: "",
    status: "need_info",
    danger: "none",
    expect: "",
  };
}

/**
 * Dev-only stand-in used when ANTHROPIC_API_KEY is unset, so the phone flow can be exercised
 * end to end on an emulator. Points at the first clickable element with visible text.
 */
export function mockGuide(req: TurnRequest): Guide {
  const line = req.tree.split("\n").find((l) => /\bclick\b/.test(l) && /"[^"]+"/.test(l));
  const m = line ? /^\s*(n\d+)\s+\S+\s+"([^"]*)"/.exec(line) : null;
  return {
    say: m ? `[mock] "${m[2]}" दबाइए` : "[mock] कुछ दिखाई नहीं दिया",
    goal: req.goal || "mock goal",
    target_kind: m ? "node" : "none",
    node_id: m ? m[1] : "",
    x: -1,
    y: -1,
    label: m ? m[2] : "",
    status: "next_step",
    danger: "none",
    expect: "",
  };
}
