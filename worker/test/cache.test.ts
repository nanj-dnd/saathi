import { describe, expect, it } from "vitest";
import { informative, readShared, screenSignature, shareable, sharedKey } from "../src/cache.ts";
import { parseTurnRequest, type Guide } from "../src/schema.ts";

const settings = [
  'n1 TextView "Settings" [63,409,408,536]',
  'n2 LinearLayout "Network & internet" [0,778,1080,1009] click',
  'n3 LinearLayout "Connected devices" [0,1009,1080,1240] click',
  'n4 LinearLayout "Apps" [0,1240,1080,1471] click',
  'n5 LinearLayout "Notifications" [0,1471,1080,1702] click',
  'n6 LinearLayout "Display & touch" [0,1702,1080,1933] click',
].join("\n");

const turn = (over: Record<string, unknown> = {}) =>
  parseTurnRequest({
    language: "hi-IN",
    goal: "make the text bigger",
    app: "com.android.settings",
    playbook: "",
    screen_w: 1080,
    screen_h: 2400,
    tree: settings,
    history: [],
    ...over,
  });

const step: Guide = {
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

describe("shared cache keys", () => {
  it("are stable and ignore a pixel of jitter and the screen's exact size", async () => {
    const a = await sharedKey(turn());
    expect(a).toMatch(/^guide:[0-9a-f]{64}$/);
    expect(await sharedKey(turn())).toBe(a);
    expect(await sharedKey(turn({ tree: settings.replace("[0,1702,1080,1933]", "[1,1703,1080,1933]") }))).toBe(a);
    const bigger = settings.replace(/\[(\d+),(\d+),(\d+),(\d+)\]/g, (_, l, t, r, b) => `[${l * 2},${t * 2},${r * 2},${b * 2}]`);
    expect(await sharedKey(turn({ tree: bigger, screen_w: 2160, screen_h: 4800 }))).toBe(a);
  });

  it("change with the screen text, language, goal and progress", async () => {
    const keys = await Promise.all([
      sharedKey(turn()),
      sharedKey(turn({ tree: settings.replace("Apps", "Applications") })),
      sharedKey(turn({ language: "ta-IN" })),
      sharedKey(turn({ goal: "turn on wifi" })),
      sharedKey(turn({ history: [{ say: "x", label: "Display & touch", status: "next_step", app: "a" }] })),
    ]);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("are never made for personal apps, spoken turns, or risky screens", async () => {
    expect(await sharedKey(turn({ app: "com.whatsapp" }))).toBeNull();
    expect(await sharedKey(turn({ app: "com.google.android.apps.nbu.paisa.user" }))).toBeNull();
    expect(await sharedKey(turn({ utterance: "अक्षर बड़े करने हैं" }))).toBeNull();
    expect(await sharedKey(turn({ secure_screen: true }))).toBeNull();
    expect(await sharedKey(turn({ tree: settings + '\nn7 TextView "Enter OTP" [0,0,10,10]' }))).toBeNull();
    expect(await sharedKey(turn({ tree: settings + '\nn7 TextView "Your KYC is expiring" [0,0,10,10]' }))).toBeNull();
    expect(await sharedKey(turn({ tree: 'n1 View "" [0,0,1080,2400] click' }))).toBeNull();
  });

  it("keeps the full conversation and app hints in the key", async () => {
    const history = [
      { say: "Pick the first contact", label: "", status: "next_step", app: "contacts" },
      { say: "Continue", label: "Continue", status: "next_step", app: "settings" },
      { say: "Continue", label: "Continue", status: "next_step", app: "settings" },
    ];
    const req = turn({ history });
    const key = await sharedKey(req);
    expect(await sharedKey({ ...req, history: [{ ...req.history[0], say: "Pick the second contact" }, ...req.history.slice(1)] })).not.toBe(key);
    expect(await sharedKey({ ...req, history: req.history.map((h) => ({ ...h, app: "other" })) })).not.toBe(key);
    expect(await sharedKey({ ...req, playbook: "new app instructions" })).not.toBe(key);
    expect(await sharedKey({ ...req, app_name: "Different display name" })).not.toBe(key);
    expect(await sharedKey({ ...req, installed_apps: [{ package: "example.reader", label: "Reader" }] })).not.toBe(key);
  });
});

describe("shareable answers", () => {
  it("are plain steps without numbers", () => {
    expect(shareable(step)).toBe(true);
    expect(shareable({ ...step, status: "done", target_kind: "none", node_id: "" })).toBe(true);
    expect(shareable({ ...step, danger: "scam_suspected" })).toBe(false);
    expect(shareable({ ...step, status: "need_info" })).toBe(false);
    expect(shareable({ ...step, status: "user_must_do" })).toBe(false);
    expect(shareable({ ...step, target_kind: "point", x: 0.5, y: 0.5 })).toBe(false);
    expect(shareable({ ...step, say: "₹1499 वाला ADD दबाइए" })).toBe(false);
    expect(shareable({ ...step, label: "Flat 402, MG Road" })).toBe(false);
    expect(shareable({ ...step, say: "Select the ₹49 item" })).toBe(false);
    expect(shareable({ ...step, say: "₹४९ वाला चुनिए" })).toBe(false);
  });
});

describe("cached answer validation", () => {
  const read = (entry: unknown) => readShared({ get: async () => JSON.stringify(entry) } as never, "key", turn());

  it("rejects corrupt, protected and mismatched cached answers", async () => {
    for (const entry of [
      null, {}, { ...step, say: 123 }, { ...step, target_kind: "invalid" },
      { ...step, danger: "sensitive_screen" }, { ...step, status: "need_info" },
      { ...step, node_id: "n99" }, { ...step, label: "A different button" },
    ]) expect(await read(entry)).toBeNull();
  });

  it("revalidates cached actions before returning them", async () => {
    expect(await read({ ...step, action: "tap", action_arg: "com.whatsapp" })).toMatchObject({
      target_kind: "node", node_id: "n6", action: "", action_arg: "",
    });
  });
});

describe("screen helpers", () => {
  it("call a screen with little text uninformative", () => {
    expect(informative(settings)).toBe(true);
    expect(informative(settings.split("\n").slice(0, 3).join("\n"))).toBe(false);
  });

  it("sort flags so their order does not matter", () => {
    expect(screenSignature('n1 Switch "Wi-Fi" [0,0,100,100] click checked', 1000, 1000)).toBe(
      screenSignature('n1 Switch "Wi-Fi" [0,0,100,100] checked click', 1000, 1000),
    );
  });
});

describe("platform cache isolation", () => {
  it("does not reuse Android replies for an iOS request with otherwise identical context", async () => {
    expect(await sharedKey(turn({ platform: "ios" }))).not.toBe(await sharedKey(turn()));
  });
});
