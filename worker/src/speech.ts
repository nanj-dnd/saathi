import { LANGUAGES, type Language } from "./schema.ts";

// Natural voices from Sarvam (bulbul), for the phone to play instead of its own text-to-speech.
// Every line is cached (src/speech-store.ts): recipe steps are fixed sentences, so each one is paid
// for once, ever, across every phone. Only the model's own replies are new text.

export const SPEECH_MODEL = "bulbul:v3";
export const DEFAULT_SPEAKER = "priya";
/** Longest line accepted, in characters. Captions are a sentence or two; this is generous. */
export const MAX_TEXT = 600;

export class UpstreamError extends Error {
  constructor(readonly status: number, detail: string) {
    super(`sarvam-tts ${status}: ${detail.slice(0, 300)}`);
  }
}

export interface SpeakRequest {
  text: string;
  language: Language;
  /** 1 is natural speed; "slow" in setup and repeats ask for less. */
  pace: number;
}

export class BadSpeakRequest extends Error {}

export function parseSpeakRequest(body: unknown): SpeakRequest {
  const b = (body ?? {}) as Record<string, unknown>;
  const text = typeof b.text === "string" ? b.text.trim() : "";
  if (!text) throw new BadSpeakRequest("text is required");
  if (text.length > MAX_TEXT) throw new BadSpeakRequest(`text is longer than ${MAX_TEXT}`);
  const language = b.language;
  if (typeof language !== "string" || !(language in LANGUAGES)) throw new BadSpeakRequest("unknown language");
  const raw = typeof b.pace === "number" && Number.isFinite(b.pace) ? b.pace : 1;
  // Rounded so near-identical speeds share cache entries; clamped to what stays understandable.
  const pace = Math.round(Math.min(1.3, Math.max(0.6, raw)) * 20) / 20;
  return { text, language: language as Language, pace };
}

/** 16 kHz is plenty for a voice, and a third the size of 48 kHz MP3 on a slow phone connection. */
export const SAMPLE_RATE = 16000;

export async function speechKey(req: SpeakRequest, speaker: string): Promise<string> {
  const material = [SPEECH_MODEL, speaker, SAMPLE_RATE, req.language, req.pace.toFixed(2), req.text].join("\n");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(material));
  const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `tts:${hex}`;
}

/** Lines cached in KV before SpeechStore existed. */
export async function readSpeech(cache: KVNamespace, key: string): Promise<ArrayBuffer | null> {
  return cache.get(key, "arrayBuffer");
}


/** Asks Sarvam for [req] in [speaker]'s voice and returns MP3 bytes. */
export async function synthesize(apiKey: string, req: SpeakRequest, speaker: string): Promise<ArrayBuffer> {
  const res = await fetch("https://api.sarvam.ai/text-to-speech", {
    method: "POST",
    headers: { "api-subscription-key": apiKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      text: req.text,
      language_code: req.language,
      model: SPEECH_MODEL,
      speaker,
      pace: req.pace,
      speech_sample_rate: SAMPLE_RATE,
      output_audio_codec: "mp3",
    }),
  });
  if (!res.ok) throw new UpstreamError(res.status, await res.text());
  const json = (await res.json()) as { audios?: string[] };
  const b64 = json.audios?.[0];
  if (!b64) throw new UpstreamError(502, "no audio in response");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}
