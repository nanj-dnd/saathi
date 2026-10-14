import Anthropic from "@anthropic-ai/sdk";
import { buildUserText, SYSTEM_PROMPT } from "./prompt.ts";
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
  transcript: string,
): Promise<Guide> {
  const client = new Anthropic({ apiKey: cfg.apiKey, maxRetries: 1, timeout: 20_000 });

  const content: Anthropic.Beta.BetaContentBlockParam[] = [];
  if (req.screenshot_b64) {
    content.push({
      type: "image",
      source: { type: "base64", media_type: "image/jpeg", data: req.screenshot_b64 },
    });
  }
  content.push({ type: "text", text: buildUserText(req, transcript) });

  const response = await client.beta.messages.create({
    model: cfg.model,
    max_tokens: 4000,
    system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
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
  return guide;
}

const SORRY: Record<TurnRequest["language"], string> = {
  "hi-IN": "माफ़ कीजिए, मैं अभी समझ नहीं पाया। फिर से बोलिए।",
  "ta-IN": "மன்னிக்கவும், எனக்குப் புரியவில்லை. மீண்டும் சொல்லுங்கள்.",
  "te-IN": "క్షమించండి, నాకు అర్థం కాలేదు. మళ్ళీ చెప్పండి.",
  "kn-IN": "ಕ್ಷಮಿಸಿ, ನನಗೆ ಅರ್ಥವಾಗಲಿಲ್ಲ. ಮತ್ತೆ ಹೇಳಿ.",
  "ml-IN": "ക്ഷമിക്കണം, എനിക്ക് മനസ്സിലായില്ല. വീണ്ടും പറയൂ.",
};

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
