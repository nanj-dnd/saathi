import { beforeEach, describe, expect, it, vi } from "vitest";
import { issueToken } from "../src/auth.ts";
import type { Guide } from "../src/schema.ts";

const reply: Guide = {
  say: "'Display & touch' पर दबाइए।",
  goal: "make the text bigger",
  target_kind: "node",
  node_id: "n6",
  x: -1,
  y: -1,
  label: "Display & touch",
  status: "next_step",
  danger: "none",
  expect: "",
};
const askGuide = vi.fn(async () => reply);
vi.mock("../src/guide.ts", async (orig) => ({ ...(await orig<typeof import("../src/guide.ts")>()), askGuide }));
const { default: worker, SpendGuard } = await import("../src/index.ts");

class MemoryKV {
  data = new Map<string, string>();
  async get(k: string) {
    return this.data.get(k) ?? null;
  }
  async put(k: string, v: string) {
    this.data.set(k, v);
  }
}

/** Just enough of DurableObjectStorage for SpendGuard. */
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

/** A SPEND binding backed by one real SpendGuard, as "global" is the only name used. */
function spendNamespace() {
  const storage = new MemoryStorage();
  const guard = new SpendGuard({ storage } as never, {} as never);
  return { storage, idFromName: () => "global", get: () => guard };
}

const tree = [
  'n1 TextView "Settings" [63,409,408,536]',
  'n2 LinearLayout "Network & internet" [0,778,1080,1009] click',
  'n3 LinearLayout "Connected devices" [0,1009,1080,1240] click',
  'n4 LinearLayout "Apps" [0,1240,1080,1471] click',
  'n5 LinearLayout "Notifications" [0,1471,1080,1702] click',
  'n6 LinearLayout "Display & touch" [0,1702,1080,1933] click',
].join("\n");

const body = {
  language: "hi-IN",
  goal: "make the text bigger",
  app: "com.android.settings",
  playbook: "",
  screen_w: 1080,
  screen_h: 2400,
  tree,
  history: [],
};

let env: Record<string, unknown>;
let pending: Promise<unknown>[];
const ctx = () => ({ waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} }) as unknown as ExecutionContext;

let token: string;

async function turn(over: Record<string, unknown> = {}) {
  const res = await worker.fetch(
    new Request("https://w/turn", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ ...body, ...over }),
    }),
    env as never,
    ctx(),
  );
  await Promise.all(pending);
  return { status: res.status, json: (await res.json()) as Record<string, any> };
}

beforeEach(async () => {
  token = await issueToken("secret"); // one device per test, so the daily limit adds up
  askGuide.mockClear();
  pending = [];
  env = {
    ANTHROPIC_API_KEY: "test",
    TOKEN_SECRET: "secret",
    GUIDE_MODEL: "claude-haiku-5-5",
    GUIDE_EFFORT: "low",
    DAILY_TURN_LIMIT: "300",
    USAGE: new MemoryKV(),
    GUIDE_CACHE: new MemoryKV(),
    GLOBAL_DAILY_LIMIT: "3000",
    SPEND: spendNamespace(),
  };
});

describe("global daily cap", () => {
  it("refuses model turns from every phone once the shared budget is spent", async () => {
    env.GLOBAL_DAILY_LIMIT = "2";
    delete env.GUIDE_CACHE;
    for (let i = 0; i < 2; i++) {
      token = await issueToken("secret"); // a new phone each time, as an abuser would register
      expect((await turn()).status).toBe(200);
    }
    token = await issueToken("secret");
    const third = await turn();
    expect(third.status).toBe(503);
    expect(third.json.error).toBe("busy");
    expect(askGuide).toHaveBeenCalledTimes(2);
  });

  it("does not spend the budget on cached answers", async () => {
    env.GLOBAL_DAILY_LIMIT = "1";
    expect((await turn()).json.source).toBe("model");
    expect((await turn()).json.source).toBe("cache");
    expect((await turn()).status).toBe(200);
  });

  it("starts each day fresh and forgets old days", async () => {
    const ns = spendNamespace();
    const guard = ns.get();
    expect(await guard.take("2026-10-14", 1)).toBe(true);
    expect(await guard.take("2026-10-14", 1)).toBe(false);
    expect(await guard.take("2026-10-15", 1)).toBe(true);
    expect([...ns.storage.data.keys()]).toEqual(["day:2026-10-15"]);
  });
});

describe("/turn with the shared cache", () => {
  it("asks the model once, then serves the same screen from the cache", async () => {
    const first = await turn();
    expect(first.json.source).toBe("model");
    const second = await turn();
    expect(second.json.source).toBe("cache");
    expect(second.json.guide).toEqual(reply);
    expect(askGuide).toHaveBeenCalledTimes(1);
  });

  it("always asks the model when the senior spoke", async () => {
    await turn({ utterance: "अक्षर बड़े करने हैं" });
    await turn({ utterance: "अक्षर बड़े करने हैं" });
    expect(askGuide).toHaveBeenCalledTimes(2);
  });

  it("does not share answers from personal apps", async () => {
    await turn({ app: "com.whatsapp" });
    await turn({ app: "com.whatsapp" });
    expect(askGuide).toHaveBeenCalledTimes(2);
  });

  it("drops a cached target that is not on the new screen", async () => {
    await turn();
    // Same signature is impossible without n6, so simulate a stale entry under the same key.
    const kv = env.GUIDE_CACHE as MemoryKV;
    const [key] = [...kv.data.keys()];
    kv.data.set(key, JSON.stringify({ ...reply, node_id: "n99" }));
    expect((await turn()).json.source).toBe("model");
  });

  it("serves cached screens after the daily limit, and refuses only new model turns", async () => {
    env.DAILY_TURN_LIMIT = "1";
    expect((await turn()).json.source).toBe("model");
    expect((await turn()).json.source).toBe("cache");
    expect((await turn({ app: "com.whatsapp" })).status).toBe(429);
    expect(askGuide).toHaveBeenCalledTimes(1);
  });

  it("works without a cache namespace", async () => {
    delete env.GUIDE_CACHE;
    expect((await turn()).json.source).toBe("model");
    expect((await turn()).json.source).toBe("model");
  });

  it("labels dev stand-ins", async () => {
    env.ANTHROPIC_API_KEY = "";
    expect((await turn()).json.source).toBe("mock");
    env.DEMO = "1";
    expect((await turn()).json.source).toBe("demo");
  });
});

describe("GET /recipes", () => {
  it("serves the recipe bundle with a cache header", async () => {
    const res = await worker.fetch(new Request("https://w/recipes"), env as never, ctx());
    expect(res.status).toBe(200);
    expect(res.headers.get("Cache-Control")).toContain("max-age");
    const json = (await res.json()) as { recipes: { id: string }[]; common: unknown };
    expect(json.recipes.length).toBeGreaterThan(10);
    expect(json.recipes.map((r) => r.id)).toContain("settings-text-size");
  });

  it("is the only GET route", async () => {
    const res = await worker.fetch(new Request("https://w/turn"), env as never, ctx());
    expect(res.status).toBe(404);
  });
});
