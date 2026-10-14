import Anthropic from "@anthropic-ai/sdk";
import { issueToken, underDailyLimit, verifyToken } from "./auth.ts";
import { demoGuide } from "./demo.ts";
import { askGuide, mockGuide } from "./guide.ts";
import { BadRequest, parseTurnRequest, type TurnResponse } from "./schema.ts";

// Speech is handled on the phone (Android SpeechRecognizer + TextToSpeech), so a turn is one
// model call: screen + what the senior said in, the next step out.

export interface Env {
  ANTHROPIC_API_KEY: string;
  TOKEN_SECRET: string;
  GUIDE_MODEL: string;
  GUIDE_EFFORT: string;
  DAILY_TURN_LIMIT: string;
  /** "1" = scripted Settings walkthrough for recording demos (src/demo.ts). Local only. */
  DEMO?: string;
  USAGE: KVNamespace;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "POST") return json({ error: "not found" }, 404);

    if (url.pathname === "/register") {
      return json({ token: await issueToken(env.TOKEN_SECRET) });
    }

    if (url.pathname !== "/turn") return json({ error: "not found" }, 404);

    const deviceId = await verifyToken(env.TOKEN_SECRET, request.headers.get("Authorization"));
    if (!deviceId) return json({ error: "unauthorised" }, 401);
    if (!(await underDailyLimit(env.USAGE, deviceId, Number(env.DAILY_TURN_LIMIT) || 300))) {
      return json({ error: "daily limit reached" }, 429);
    }

    try {
      const req = parseTurnRequest(await request.json());
      const t0 = Date.now();
      const guide = env.DEMO === "1"
        ? demoGuide(req)
        : !env.ANTHROPIC_API_KEY
          ? mockGuide(req)
          : await askGuide(
            {
              apiKey: env.ANTHROPIC_API_KEY,
              model: env.GUIDE_MODEL || "claude-haiku-5-5",
              effort: (env.GUIDE_EFFORT as "low" | "medium" | "high") || "low",
            },
            req,
          );
      const timings = { guide: Date.now() - t0 };

      console.log(JSON.stringify({ deviceId, app: req.app, status: guide.status, danger: guide.danger, timings }));
      const body: TurnResponse = { guide, timings_ms: timings };
      return json(body);
    } catch (err) {
      if (err instanceof BadRequest) return json({ error: err.message }, 400);
      if (err instanceof Anthropic.RateLimitError) return json({ error: "busy" }, 503);
      if (err instanceof Anthropic.APIError) {
        console.error(`anthropic ${err.status}: ${err.message}`);
        return json({ error: "guide" }, 502);
      }
      if (err instanceof SyntaxError) return json({ error: "invalid json" }, 400);
      throw err;
    }
  },
} satisfies ExportedHandler<Env>;
