import type { Language } from "./schema.ts";

const BASE = "https://api.sarvam.ai";

export class UpstreamError extends Error {
  constructor(
    readonly service: string,
    readonly status: number,
    detail: string,
  ) {
    super(`${service} ${status}: ${detail.slice(0, 300)}`);
  }
}

export async function transcribe(
  apiKey: string,
  wav: Uint8Array,
  language: Language,
): Promise<string> {
  const form = new FormData();
  form.append("file", new Blob([wav], { type: "audio/wav" }), "speech.wav");
  form.append("model", "saaras:v4");
  form.append("language_code", language);

  const res = await fetch(`${BASE}/speech-to-text`, {
    method: "POST",
    headers: { "api-subscription-key": apiKey },
    body: form,
  });
  if (!res.ok) throw new UpstreamError("sarvam-stt", res.status, await res.text());
  const json = (await res.json()) as { transcript?: string };
  return (json.transcript ?? "").trim();
}

/** Returns base64 WAV. */
export async function speak(
  apiKey: string,
  text: string,
  language: Language,
  pace: number,
): Promise<string> {
  const res = await fetch(`${BASE}/text-to-speech`, {
    method: "POST",
    headers: { "api-subscription-key": apiKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      text,
      language_code: language,
      model: "bulbul:v3",
      pace,
      speech_sample_rate: 22050,
      output_audio_codec: "wav",
    }),
  });
  if (!res.ok) throw new UpstreamError("sarvam-tts", res.status, await res.text());
  const json = (await res.json()) as { audios?: string[] };
  const audio = json.audios?.[0];
  if (!audio) throw new UpstreamError("sarvam-tts", 502, "no audio in response");
  return audio;
}
