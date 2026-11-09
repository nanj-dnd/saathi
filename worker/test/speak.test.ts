import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { issueToken } from "../src/auth.ts";
import { DEFAULT_SPEAKER, SPEECH_MODEL, OUTPUT_FORMAT, parseSpeakRequest, speechKey, synthesize } from "../src/speech.ts";

const { default: worker, SpendGuard } = await import("../src/index.ts");

/** KV that keeps whatever it is given, binary included. */
class MemoryKV {
  data = new Map<string, unknown>();
  async get(k: string) {
    return this.data.get(k) ?? null;
  }
  async put(k: string, v: unknown) {
    this.data.set(k, v);
  }
}

class MemoryStorage {
  data = new Map<string, unknown>();
  async get<T>(k: string) {
    return this.data.get(k) as T | undefined;
  }
  async put(k: string, v: unknown) {
    this.data.set(k, v);
  }
  async list({ prefix = "" } = {}) {
    return new Map([...this.data].filter(([k]) => k.startsWith(prefix)));
  }
  async delete(keys: string[]) {
    keys.forEach((k) => this.data.delete(k));
    return keys.length;
  }
}

const MP3 = "ID3-fake-mp3";
let elevenlabs: ReturnType<typeof vi.fn>;
/** A SPEECH binding: one in-memory store, as "clips" is the only name used. */
function speechNamespace() {
  const clips = new Map<string, ArrayBuffer>();
  const store = { read: async (k: string) => clips.get(k) ?? null, write: async (k: string, v: ArrayBuffer) => void clips.set(k, v) };
  return { clips, idFromName: () => "clips", get: () => store };
}

let env: Record<string, unknown>;
let pending: Promise<unknown>[];
let token: string;
const ctx = () => ({ waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} }) as unknown as ExecutionContext;

const speakReq = (body: unknown, auth = token) =>
  new Request("https://w.test/speak", {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

beforeEach(async () => {
  pending = [];
  const storage = new MemoryStorage();
  const guard = new SpendGuard({ storage } as never, {} as never);
  env = {
    TOKEN_SECRET: "s",
    ELEVENLABS_API_KEY: "k",
    USAGE: new MemoryKV(),
    GUIDE_CACHE: new MemoryKV(),
    SPEND: { idFromName: () => "global", get: () => guard },
    SPEECH: speechNamespace(),
  };
  token = await issueToken("s");
  elevenlabs = vi.fn(async () => new Response(MP3, { headers: { "Content-Type": "audio/mpeg" } }));
  vi.stubGlobal("fetch", elevenlabs);
});

afterEach(() => vi.unstubAllGlobals());

describe("/speak", () => {
  it("returns ElevenLabs's audio as MP3 and serves the same line from the cache after that", async () => {
    const first = await worker.fetch(speakReq({ text: "'Display & touch' पर दबाइए।", language: "hi-IN" }), env as never, ctx());
    expect(first.status).toBe(200);
    expect(first.headers.get("Content-Type")).toBe("audio/mpeg");
    expect(first.headers.get("X-Saathi-Source")).toBe("elevenlabs");
    expect(new TextDecoder().decode(await first.arrayBuffer())).toBe(MP3);
    await Promise.all(pending);

    const [url, options] = elevenlabs.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`https://api.elevenlabs.io/v1/text-to-speech/${DEFAULT_SPEAKER}?output_format=${OUTPUT_FORMAT}`);
    expect(options.headers).toMatchObject({ "xi-api-key": "k", Accept: "audio/mpeg" });
    const sent = JSON.parse(options.body as string);
    expect(sent).toMatchObject({ language_code: "hi", model_id: SPEECH_MODEL, voice_settings: { speed: 1, stability: 0.5, similarity_boost: 0.75 } });

    const again = await worker.fetch(speakReq({ text: "'Display & touch' पर दबाइए।", language: "hi-IN" }), env as never, ctx());
    expect(again.headers.get("X-Saathi-Source")).toBe("cache");
    expect(elevenlabs).toHaveBeenCalledTimes(1);
  });

  it("still serves lines cached in KV before the speech store existed", async () => {
    const req = parseSpeakRequest({ text: "पुरानी लाइन", language: "hi-IN" });
    await (env.GUIDE_CACHE as MemoryKV).put(await speechKey(req, DEFAULT_SPEAKER), new TextEncoder().encode("old-mp3").buffer);
    const res = await worker.fetch(speakReq({ text: "पुरानी लाइन", language: "hi-IN" }), env as never, ctx());
    expect(res.headers.get("X-Saathi-Source")).toBe("cache");
    expect(new TextDecoder().decode(await res.arrayBuffer())).toBe("old-mp3");
    expect(elevenlabs).not.toHaveBeenCalled();
  });

  it("answers 501 without a ElevenLabs key, so the phone uses its own voice", async () => {
    delete env.ELEVENLABS_API_KEY;
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(501);
    expect(elevenlabs).not.toHaveBeenCalled();
  });

  it("needs a device token", async () => {
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }, "nope.bad"), env as never, ctx());
    expect(res.status).toBe(401);
  });

  it("stops making new lines at the daily ceiling but keeps serving cached ones", async () => {
    env.SPEECH_DAILY_LIMIT = "1";
    expect((await worker.fetch(speakReq({ text: "one", language: "en-IN" }), env as never, ctx())).status).toBe(200);
    await Promise.all(pending);
    expect((await worker.fetch(speakReq({ text: "two", language: "en-IN" }), env as never, ctx())).status).toBe(503);
    expect((await worker.fetch(speakReq({ text: "one", language: "en-IN" }), env as never, ctx())).status).toBe(200);
  });

  it.each([401, 402, 403])("falls back when ElevenLabs credentials or credits fail (%s)", async (status) => {
    elevenlabs.mockResolvedValueOnce(new Response('{"detail":{"status":"quota_exceeded"}}', { status }));
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(501);
  });

  it("maps an ElevenLabs failure to an error the phone treats as 'use your own voice'", async () => {
    elevenlabs.mockResolvedValueOnce(new Response("quota", { status: 429 }));
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(503);
  });
});

describe("speak requests", () => {
  it.each(["en-IN", "hi-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN"])("sends the ISO language code for %s", async (language) => {
    await synthesize("k", parseSpeakRequest({ text: "Bob", language }), DEFAULT_SPEAKER);
    const [, options] = elevenlabs.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(options.body as string).language_code).toBe(language.split("-")[0]);
  });

  it("never reuses clips across voice models", async () => {
    const req = parseSpeakRequest({ text: "Bob", language: "en-IN" });
    expect(await speechKey(req, DEFAULT_SPEAKER)).not.toBe(await speechKey(req, DEFAULT_SPEAKER, "another-model"));
  });

  it.each(["", "{\"audio\":\"missing\"}"])("rejects malformed audio instead of caching it: %s", async (body) => {
    elevenlabs.mockResolvedValueOnce(new Response(body, { headers: { "Content-Type": "audio/mpeg" } }));
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(502);
    expect((env.SPEECH as ReturnType<typeof speechNamespace>).clips.size).toBe(0);
  });

  it("returns a fallback response on a network failure", async () => {
    elevenlabs.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect((await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx())).status).toBe(502);
  });

  it("rejects empty, overlong and unknown-language lines", () => {
    expect(() => parseSpeakRequest({ text: "", language: "hi-IN" })).toThrow();
    expect(() => parseSpeakRequest({ text: "x".repeat(601), language: "hi-IN" })).toThrow();
    expect(() => parseSpeakRequest({ text: "hi", language: "xx-XX" })).toThrow();
    expect(() => parseSpeakRequest({ text: "hi", language: "toString" })).toThrow();
    expect(() => parseSpeakRequest({ text: "hi", language: "valueOf" })).toThrow();
  });

  it("rounds and clamps the pace, so near-identical speeds share one cached line", async () => {
    expect(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.83 }).pace).toBe(0.85);
    expect(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 9 }).pace).toBe(1.2);
    const a = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.84 }), "shubh");
    const b = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.86 }), "shubh");
    const c = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.86 }), "priya");
    expect(a).toBe(b);
    expect(a).not.toBe(c);
  });
});
