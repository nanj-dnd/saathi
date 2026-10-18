import { DurableObject } from "cloudflare:workers";

/**
 * One counter for model calls across every phone, so an open /register can't run up an unbounded
 * Anthropic bill. A Durable Object is used rather than KV because KV allows about one write a
 * second per key, which would undercount exactly when traffic spikes. One instance ("global")
 * handles requests one at a time, so the count is exact.
 */
export class SpendGuard extends DurableObject {
  /**
   * Takes one call from [counter]'s budget for [day]. False once [limit] calls have been made that
   * day. "day" is the model's global budget; "speech" the natural voice's; "turns-<device>" and
   * "speech-<device>" are one phone's own daily limits. (These lived in KV, whose free plan allows
   * only 1,000 writes a day; this storage allows 100,000.)
   */
  async take(day: string, limit: number, counter = "day"): Promise<boolean> {
    const key = `${counter}:${day}`;
    const used = (await this.ctx.storage.get<number>(key)) ?? 0;
    if (used >= limit) return false;
    if (used === 0) {
      // First call of a new day for this counter: drop its old days.
      const old = await this.ctx.storage.list({ prefix: `${counter}:` });
      await this.ctx.storage.delete([...old.keys()].filter((k) => k !== key));
    }
    await this.ctx.storage.put(key, used + 1);
    return true;
  }
}

export const today = () => new Date().toISOString().slice(0, 10);
