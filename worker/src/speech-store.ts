import { DurableObject } from "cloudflare:workers";

/**
 * The natural voice's shared cache: one MP3 per line, kept in this Durable Object's SQLite storage.
 * The free plan allows 100,000 writes a day here (KV allows 1,000), so the whole cache can be filled
 * in one go and still leave room for everyday use. Lines are a few tens of KB; the 5 GB limit holds
 * well over a hundred thousand of them.
 */
export class SpeechStore extends DurableObject {
  async read(key: string): Promise<ArrayBuffer | null> {
    return (await this.ctx.storage.get<ArrayBuffer>(key)) ?? null;
  }

  async write(key: string, audio: ArrayBuffer): Promise<void> {
    await this.ctx.storage.put(key, audio);
  }
}
