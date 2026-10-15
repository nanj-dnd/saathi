import Anthropic from "@anthropic-ai/sdk";
import { issueToken, underDailyLimit, verifyToken } from "./auth.ts";
import { demoGuide } from "./demo.ts";
import { readShared, shareable, sharedKey, writeShared } from "./cache.ts";
import { askGuide, mockGuide } from "./guide.ts";
import { SpendGuard, today } from "./spend.ts";
import recipeBundle from "./recipe-bundle.json";
import { BadRequest, parseTurnRequest, type Guide, type Source, type TurnResponse } from "./schema.ts";

// Speech is handled on the phone (Android SpeechRecognizer + TextToSpeech), so a turn is one
// model call: screen + what the senior said in, the next step out.

export interface Env {
  ANTHROPIC_API_KEY: string;
  TOKEN_SECRET: string;
  GUIDE_MODEL: string;
  GUIDE_EFFORT: string;
  DAILY_TURN_LIMIT: string;
  /** Model calls per day across all phones; a ceiling on the Anthropic bill. */
  GLOBAL_DAILY_LIMIT: string;
  SPEND: DurableObjectNamespace<SpendGuard>;
  /** "1" = scripted Settings walkthrough for recording demos (src/demo.ts). Local only. */
  DEMO?: string;
  USAGE: KVNamespace;
  /** Answers shared between phones (src/cache.ts). Optional: without it every turn asks the model. */
  GUIDE_CACHE?: KVNamespace;
}

export { SpendGuard };

function underGlobalLimit(env: Env): Promise<boolean> {
  const guard = env.SPEND.get(env.SPEND.idFromName("global"));
  return guard.take(today(), Number(env.GLOBAL_DAILY_LIMIT) || 3000);
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

const RECIPES = JSON.stringify(recipeBundle);

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/recipes") {
      // Scripted guides from shared/recipes, so a recipe can be fixed without an app update.
      return new Response(RECIPES, {
        headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=3600" },
      });
    }
    if (request.method !== "POST") return json({ error: "not found" }, 404);

    if (url.pathname === "/register") {
      return json({ token: await issueToken(env.TOKEN_SECRET) });
    }

    if (url.pathname !== "/turn") return json({ error: "not found" }, 404);

    const deviceId = await verifyToken(env.TOKEN_SECRET, request.headers.get("Authorization"));
    if (!deviceId) return json({ error: "unauthorised" }, 401);
    // The daily limit guards model spend, so it is counted only for turns that reach the model.
    const allowed = () => underDailyLimit(env.USAGE, deviceId, Number(env.DAILY_TURN_LIMIT) || 300);

    try {
      const req = parseTurnRequest(await request.json());
      const t0 = Date.now();
      let guide: Guide;
      let source: Source;
      if (env.DEMO === "1") {
        guide = demoGuide(req);
        source = "demo";
      } else if (!env.ANTHROPIC_API_KEY) {
        guide = mockGuide(req);
        source = "mock";
      } else {
        const cache = env.GUIDE_CACHE;
        const key = cache ? await sharedKey(req) : null;
        const hit = cache && key ? await readShared(cache, key, req) : null;
        if (hit) {
          guide = hit;
          source = "cache";
        } else {
          if (!(await allowed())) return json({ error: "daily limit reached" }, 429);
          // Everyone shares this ceiling, so the senior hears "busy, try later", not "your limit".
          if (!(await underGlobalLimit(env))) return json({ error: "busy" }, 503);
          guide = await askGuide(
            {
              apiKey: env.ANTHROPIC_API_KEY,
              model: env.GUIDE_MODEL || "claude-haiku-5-5",
              effort: (env.GUIDE_EFFORT as "low" | "medium" | "high") || "low",
            },
            req,
          );
          source = "model";
          if (cache && key && shareable(guide)) ctx.waitUntil(writeShared(cache, key, guide));
        }
      }
      const timings = { guide: Date.now() - t0 };

      console.log(JSON.stringify({ deviceId, app: req.app, source, status: guide.status, danger: guide.danger, timings }));
      const body: TurnResponse = { guide, timings_ms: timings, source };
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
