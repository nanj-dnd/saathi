import Anthropic from "@anthropic-ai/sdk";
import { issueToken, verifyToken } from "./auth.ts";
import { demoGuide } from "./demo.ts";
import { readShared, shareable, sharedKey, writeShared } from "./cache.ts";
import { askGuide, mockGuide, unreadableGuide } from "./guide.ts";
import { SpendGuard, today } from "./spend.ts";
import { SpeechStore } from "./speech-store.ts";
import recipeBundle from "./recipe-bundle.json";
import { BadSpeakRequest, DEFAULT_SPEAKER, SPEECH_MODEL, parseSpeakRequest, readSpeech, speechKey, synthesize, UpstreamError } from "./speech.ts";
import { BadRequest, parseTurnRequest, type Guide, type Source, type TurnResponse } from "./schema.ts";

// Listening happens on the phone (Android SpeechRecognizer), so a turn is one model call: screen +
// what the senior said in, the next step out. Speaking can use /speak (ElevenLabs natural voice,
// cached per line); the phone falls back to its own text-to-speech when that is unavailable.

export interface Env {
  ANTHROPIC_API_KEY: string;
  TOKEN_SECRET: string;
  GUIDE_MODEL: string;
  GUIDE_EFFORT: string;
  DAILY_TURN_LIMIT: string;
  /** Model calls per day across all phones; a ceiling on the Anthropic bill. */
  GLOBAL_DAILY_LIMIT: string;
  SPEND: DurableObjectNamespace<SpendGuard>;
  /** The natural voice's shared cache (src/speech-store.ts). */
  SPEECH: DurableObjectNamespace<SpeechStore>;
  /** "1" = scripted Settings walkthrough for recording demos (src/demo.ts). Local only. */
  DEMO?: string;
  USAGE: KVNamespace;
  /** Answers shared between phones (src/cache.ts). Optional: without it every turn asks the model. */
  GUIDE_CACHE?: KVNamespace;
  /** ElevenLabs key for /speak. Without it /speak answers 501 and phones use their own voice. */
  ELEVENLABS_API_KEY?: string;
  /** ElevenLabs voice ID; defaults to Eric, Bob's calm male voice. */
  ELEVENLABS_VOICE_ID?: string;
  ELEVENLABS_MODEL_ID?: string;
  /** Uncached /speak lines per day across all phones; a ceiling on the ElevenLabs bill. */
  SPEECH_DAILY_LIMIT?: string;
  /** Uncached /speak lines per phone per day. */
  DEVICE_SPEECH_LIMIT?: string;
}

export { SpendGuard, SpeechStore };

/** Takes one call from a daily budget kept by the SpendGuard (see its take()). */
function under(env: Env, counter: string, limit: number): Promise<boolean> {
  return env.SPEND.get(env.SPEND.idFromName("global")).take(today(), limit, counter);
}

const underGlobalLimit = (env: Env) => under(env, "day", Number(env.GLOBAL_DAILY_LIMIT) || 3000);

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
    if (request.method === "GET" && url.pathname === "/ping") {
      // Phones call this when the senior taps Help, so the connection is open before speech is needed.
      return new Response(null, { status: 204 });
    }
    if (request.method !== "POST") return json({ error: "not found" }, 404);

    if (url.pathname === "/register") {
      return json({ token: await issueToken(env.TOKEN_SECRET) });
    }

    if (url.pathname !== "/turn" && url.pathname !== "/speak") return json({ error: "not found" }, 404);

    const deviceId = await verifyToken(env.TOKEN_SECRET, request.headers.get("Authorization"));
    if (!deviceId) return json({ error: "unauthorised" }, 401);
    if (url.pathname === "/speak") return speak(request, env, ctx, deviceId);
    // The daily limit guards model spend, so it is counted only for turns that reach the model.
    const allowed = () => under(env, `turns-${deviceId}`, Number(env.DAILY_TURN_LIMIT) || 300);

    try {
      const req = parseTurnRequest(await request.json());
      const t0 = Date.now();
      let guide: Guide;
      let source: Source;
      if (env.DEMO === "1") {
        guide = demoGuide(req);
        source = "demo";
      } else if (!req.tree.trim() && !req.screenshot_b64) {
        guide = unreadableGuide(req);
        source = "local";
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
          if (cache && key && shareable(guide)) ctx.waitUntil(writeShared(cache, key, guide).catch((e) => console.error(`answer not cached: ${e.message}`)));
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

/** One line of speech as MP3: from the shared cache, or made by ElevenLabs and cached for everyone. */
async function speak(request: Request, env: Env, ctx: ExecutionContext, deviceId: string): Promise<Response> {
  if (!env.ELEVENLABS_API_KEY) return json({ error: "no voice configured" }, 501);
  let req;
  try {
    req = parseSpeakRequest(await request.json());
  } catch (err) {
    if (err instanceof BadSpeakRequest || err instanceof SyntaxError) return json({ error: (err as Error).message }, 400);
    throw err;
  }
  const speaker = env.ELEVENLABS_VOICE_ID || DEFAULT_SPEAKER;
  const model = env.ELEVENLABS_MODEL_ID || SPEECH_MODEL;
  const audio = (body: ArrayBuffer, source: string) =>
    new Response(body, { headers: { "Content-Type": "audio/mpeg", "X-Saathi-Source": source } });

  const key = await speechKey(req, speaker, model);
  const store = env.SPEECH.get(env.SPEECH.idFromName("clips"));
  // Lines cached in KV before the store existed are still served from there.
  const hit = (await store.read(key)) ?? (env.GUIDE_CACHE ? await readSpeech(env.GUIDE_CACHE, key) : null);
  if (hit) return audio(hit, "cache");

  // Only new lines cost money, so only they count against the limits.
  if (!(await under(env, `speech-${deviceId}`, Number(env.DEVICE_SPEECH_LIMIT) || 400))) return json({ error: "daily limit reached" }, 429);
  if (!(await under(env, "speech", Number(env.SPEECH_DAILY_LIMIT) || 4000))) return json({ error: "busy" }, 503);
  try {
    const t0 = Date.now();
    const body = await synthesize(env.ELEVENLABS_API_KEY, req, speaker, model);
    console.log(JSON.stringify({ deviceId, speech: req.language, chars: req.text.length, ms: Date.now() - t0 }));
    ctx.waitUntil(store.write(key, body).catch((e) => console.error(`speech not cached: ${e.message}`)));
    return audio(body, "elevenlabs");
  } catch (err) {
    if (err instanceof UpstreamError) {
      console.error(err.message);
      // Out of ElevenLabs credits (402) or a bad key (401/403): there is no voice until someone fixes the
      // account, so tell phones "none" (501) and they stop asking for a while instead of every line.
      if (err.status === 402 || err.status === 401 || err.status === 403) return json({ error: "no voice available" }, 501);
      return json({ error: "voice" }, err.status === 429 ? 503 : 502);
    }
    throw err;
  }
}
