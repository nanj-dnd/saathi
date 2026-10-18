import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { issueToken } from "../src/auth.ts";
import { parseSpeakRequest, speechKey } from "../src/speech.ts";

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
let sarvam: ReturnType<typeof vi.fn>;
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
    SARVAM_API_KEY: "k",
    USAGE: new MemoryKV(),
    GUIDE_CACHE: new MemoryKV(),
    SPEND: { idFromName: () => "speech", get: () => guard },
  };
  token = await issueToken("s");
  sarvam = vi.fn(async () => new Response(JSON.stringify({ audios: [btoa(MP3)] })));
  vi.stubGlobal("fetch", sarvam);
});

afterEach(() => vi.unstubAllGlobals());

describe("/speak", () => {
  it("returns Sarvam's audio as MP3 and serves the same line from the cache after that", async () => {
    const first = await worker.fetch(speakReq({ text: "'Display & touch' पर दबाइए।", language: "hi-IN" }), env as never, ctx());
    expect(first.status).toBe(200);
    expect(first.headers.get("Content-Type")).toBe("audio/mpeg");
    expect(first.headers.get("X-Saathi-Source")).toBe("sarvam");
    expect(new TextDecoder().decode(await first.arrayBuffer())).toBe(MP3);
    await Promise.all(pending);

    const sent = JSON.parse((sarvam.mock.calls[0] as [string, RequestInit])[1].body as string);
    expect(sent).toMatchObject({ language_code: "hi-IN", model: "bulbul:v3", speaker: "priya", pace: 1, output_audio_codec: "mp3" });

    const again = await worker.fetch(speakReq({ text: "'Display & touch' पर दबाइए।", language: "hi-IN" }), env as never, ctx());
    expect(again.headers.get("X-Saathi-Source")).toBe("cache");
    expect(sarvam).toHaveBeenCalledTimes(1);
  });

  it("answers 501 without a Sarvam key, so the phone uses its own voice", async () => {
    delete env.SARVAM_API_KEY;
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(501);
    expect(sarvam).not.toHaveBeenCalled();
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

  it("maps a Sarvam failure to an error the phone treats as 'use your own voice'", async () => {
    sarvam.mockResolvedValueOnce(new Response("quota", { status: 429 }));
    const res = await worker.fetch(speakReq({ text: "hello", language: "en-IN" }), env as never, ctx());
    expect(res.status).toBe(503);
  });
});

describe("speak requests", () => {
  it("rejects empty, overlong and unknown-language lines", () => {
    expect(() => parseSpeakRequest({ text: "", language: "hi-IN" })).toThrow();
    expect(() => parseSpeakRequest({ text: "x".repeat(601), language: "hi-IN" })).toThrow();
    expect(() => parseSpeakRequest({ text: "hi", language: "fr-FR" })).toThrow();
  });

  it("rounds and clamps the pace, so near-identical speeds share one cached line", async () => {
    expect(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.83 }).pace).toBe(0.85);
    expect(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 9 }).pace).toBe(1.3);
    const a = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.84 }), "shubh");
    const b = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.86 }), "shubh");
    const c = await speechKey(parseSpeakRequest({ text: "a", language: "hi-IN", pace: 0.86 }), "priya");
    expect(a).toBe(b);
    expect(a).not.toBe(c);
  });
});
