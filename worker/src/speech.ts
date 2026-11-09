import { LANGUAGES, type Language } from "./schema.ts";

// ElevenLabs speech, for the phone to play instead of its own text-to-speech.
// Every line is cached (src/speech-store.ts): recipe steps are fixed sentences, so each one is paid
// for once, ever, across every phone. Only the model's own replies are new text.

// Conversational v3 supports all six app languages, including Telugu, Kannada and Malayalam.
export const SPEECH_MODEL = "eleven_v3_conversational";
/** Eric: a calm, conversational premade male voice. */
export const DEFAULT_SPEAKER = "cjVigY5qzO86Huf0OWal";
export const OUTPUT_FORMAT = "mp3_22050_32";
/** Longest line accepted, in characters. Captions are a sentence or two; this is generous. */
export const MAX_TEXT = 600;

export class UpstreamError extends Error {
  constructor(readonly status: number, detail: string) {
    super(`elevenlabs-tts ${status}: ${detail.slice(0, 300)}`);
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
  if (typeof language !== "string" || !Object.hasOwn(LANGUAGES, language)) throw new BadSpeakRequest("unknown language");
  const raw = typeof b.pace === "number" && Number.isFinite(b.pace) ? b.pace : 1;
  // Rounded so near-identical speeds share cache entries; clamped to what stays understandable.
  const pace = Math.round(Math.min(1.2, Math.max(0.7, raw)) * 20) / 20;
  return { text, language: language as Language, pace };
}

export async function speechKey(req: SpeakRequest, speaker: string, model = SPEECH_MODEL): Promise<string> {
  // Provider and settings version prevent older providers' clips from leaking into Bob's voice.
  const material = ["elevenlabs:v1", model, speaker, OUTPUT_FORMAT, req.language, req.pace.toFixed(2), req.text].join("\n");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(material));
  const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `tts:${hex}`;
}

/** Lines cached in KV before SpeechStore existed. */
export async function readSpeech(cache: KVNamespace, key: string): Promise<ArrayBuffer | null> {
  return cache.get(key, "arrayBuffer");
}


/** Requests binary MP3; the key stays on the Worker, never on a phone. */
export async function synthesize(apiKey: string, req: SpeakRequest, speaker: string, model = SPEECH_MODEL): Promise<ArrayBuffer> {
  let res: Response;
  try {
    res = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(speaker)}?output_format=${OUTPUT_FORMAT}`, {
    method: "POST",
    headers: { "xi-api-key": apiKey, "Content-Type": "application/json", Accept: "audio/mpeg" },
    signal: AbortSignal.timeout(12_000),
    body: JSON.stringify({
      text: req.text,
      language_code: req.language.split("-")[0],
      model_id: model,
      voice_settings: { speed: req.pace, stability: 0.5, similarity_boost: 0.75 },
    }),
    });
    // Don't log upstream bodies: they can echo the senior's spoken text.
    if (!res.ok) throw new UpstreamError(res.status, "speech request failed");
    const body = await res.arrayBuffer();
    const bytes = new Uint8Array(body);
    const isMp3 = (bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) ||
      (bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0);
    if (!res.headers.get("Content-Type")?.startsWith("audio/") || bytes.length < 4 || !isMp3) {
      throw new UpstreamError(502, "invalid MP3 response");
    }
    return body;
  } catch (err) {
    if (err instanceof UpstreamError) throw err;
    throw new UpstreamError(504, "speech request unavailable or timed out");
  }
}
