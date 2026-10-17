import { DurableObject } from "cloudflare:workers";

/**
 * One counter for model calls across every phone, so an open /register can't run up an unbounded
 * Anthropic bill. A Durable Object is used rather than KV because KV allows about one write a
 * second per key, which would undercount exactly when traffic spikes. One instance ("global")
 * handles requests one at a time, so the count is exact.
 */
export class SpendGuard extends DurableObject {
  /** Takes one call from [day]'s budget. False once [limit] calls have been made that day. */
  async take(day: string, limit: number): Promise<boolean> {
    const key = `day:${day}`;
    const used = (await this.ctx.storage.get<number>(key)) ?? 0;
    if (used >= limit) return false;
    if (used === 0) {
      // First call of a new day: drop the old days' counters.
      const old = await this.ctx.storage.list({ prefix: "day:" });
      await this.ctx.storage.delete([...old.keys()].filter((k) => k !== key));
    }
    await this.ctx.storage.put(key, used + 1);
    return true;
  }
}

export const today = () => new Date().toISOString().slice(0, 10);
