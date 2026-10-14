import Anthropic from "@anthropic-ai/sdk";
import { issueToken, underDailyLimit, verifyToken } from "./auth.ts";
import { askGuide, mockGuide, safeFallback } from "./guide.ts";
import { speak, transcribe, UpstreamError } from "./sarvam.ts";
import { BadRequest, parseTurnRequest, type TurnResponse } from "./schema.ts";

export interface Env {
  ANTHROPIC_API_KEY: string;
  SARVAM_API_KEY: string;
  TOKEN_SECRET: string;
  GUIDE_MODEL: string;
  GUIDE_EFFORT: string;
  DAILY_TURN_LIMIT: string;
  TTS_PACE: string;
  USAGE: KVNamespace;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

function decodeB64(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

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
      const timings: Record<string, number> = {};
      const t0 = Date.now();

      let transcript = "";
      if (req.audio_b64 && env.SARVAM_API_KEY) {
        transcript = await transcribe(env.SARVAM_API_KEY, decodeB64(req.audio_b64), req.language);
        timings.stt = Date.now() - t0;
      }

      const t1 = Date.now();
      // A spoken turn with an empty transcript means we heard nothing usable; ask again
      // instead of guessing from the screen alone.
      const guide =
        !env.ANTHROPIC_API_KEY
          ? mockGuide(req)
          : req.audio_b64 && env.SARVAM_API_KEY && !transcript && !req.goal
            ? safeFallback(req)
            : await askGuide(
              {
                apiKey: env.ANTHROPIC_API_KEY,
                model: env.GUIDE_MODEL || "claude-opus-5-5",
                effort: (env.GUIDE_EFFORT as "low" | "medium" | "high") || "low",
              },
              req,
              transcript,
            );
      timings.guide = Date.now() - t1;

      let audio: string | null = null;
      // Without a Sarvam key the phone falls back to Android's own TextToSpeech.
      if (req.want_audio && guide.say && env.SARVAM_API_KEY) {
        const t2 = Date.now();
        audio = await speak(env.SARVAM_API_KEY, guide.say, req.language, Number(env.TTS_PACE) || 0.85);
        timings.tts = Date.now() - t2;
      }
      timings.total = Date.now() - t0;

      console.log(JSON.stringify({ deviceId, app: req.app, status: guide.status, danger: guide.danger, timings }));
      const body: TurnResponse = { transcript, guide, audio_b64: audio, timings_ms: timings };
      return json(body);
    } catch (err) {
      if (err instanceof BadRequest) return json({ error: err.message }, 400);
      if (err instanceof UpstreamError) {
        console.error(err.message);
        return json({ error: err.service }, 502);
      }
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
