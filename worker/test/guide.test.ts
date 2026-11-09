import { beforeEach, describe, expect, it, vi } from "vitest";
import { askGuide } from "../src/guide.ts";
import { parseTurnRequest, type Guide, type TurnRequest } from "../src/schema.ts";

const { create } = vi.hoisted(() => ({ create: vi.fn() }));
vi.mock("@anthropic-ai/sdk", () => ({ default: class {
  beta = { messages: { create } };
} }));

const cfg = { apiKey: "test", model: "claude-haiku-test", effort: "low" as const };
const req = (over: Partial<TurnRequest> = {}): TurnRequest => parseTurnRequest({
  language: "en-IN", goal: "send a message in Signal", app: "org.thoughtcrime.securesms", app_name: "Signal",
  playbook: "General guidance", screen_w: 1080, screen_h: 2400,
  tree: 'n1 Button "New message" [40,1500,200,1700] click', history: [],
  installed_apps: [{ package: "org.thoughtcrime.securesms", label: "Signal" }], ...over,
});
const base: Guide = {
  say: "Press New message", goal: "send a message in Signal", target_kind: "node", node_id: "n1",
  x: -1, y: -1, label: "New message", status: "next_step", danger: "none", expect: "message editor", action: "", action_arg: "",
};
const respond = (guide: Partial<Guide>) => create.mockResolvedValue({
  stop_reason: "end_turn", content: [{ type: "text", text: JSON.stringify({ ...base, ...guide }) }],
});
beforeEach(() => { create.mockReset(); respond({}); });

describe("guidance for arbitrary apps", () => {
  it("uses the iPhone prompt and strips Android actions even when the model emits one", async () => {
    respond({ target_kind: "none", node_id: "", action: "open_app", action_arg: "com.android.settings" });
    const result = await askGuide(cfg, req({ platform: "ios", app: "ios.screen-broadcast", installed_apps: [] }));
    expect(result).toMatchObject({ action: "", action_arg: "", target_kind: "none", node_id: "", x: -1, y: -1 });
    const prompt = create.mock.calls[0][0].system[0].text;
    expect(prompt).toContain("iPhone");
    expect(prompt).toContain("on-device");
    expect(prompt).not.toContain("You are Bob, a patient helper that sits on an Indian senior citizen's Android phone");
  });

  it("does not ask iOS users to tap Android's Help overlay on an unreadable screen", async () => {
    const result = await askGuide(cfg, req({ platform: "ios", tree: "", screenshot_b64: undefined }));
    expect(result.say).not.toContain("tap Help");
    expect(create).not.toHaveBeenCalled();
  });
  it("guides an unfamiliar app using its real screen and installed display name", async () => {
    expect(await askGuide(cfg, req())).toMatchObject({ target_kind: "node", node_id: "n1", status: "next_step" });
    const sent = create.mock.calls[0][0];
    const text = sent.messages[0].content.find((c: { type: string }) => c.type === "text").text;
    expect(text).toContain("org.thoughtcrime.securesms");
    expect(text).toContain('App display name: "Signal"');
    expect(text).toContain('"label":"Signal"');
    expect(text).toContain('n1 Button "New message"');
  });

  it("can point in an image-only custom app when the picture is available", async () => {
    respond({ target_kind: "point", node_id: "", x: 0.5, y: 0.7, label: "Play" });
    expect(await askGuide(cfg, req({ app: "example.canvas.app", tree: "", screenshot_b64: "picture" })))
      .toMatchObject({ target_kind: "point", x: 0.5, y: 0.7 });
    expect(create.mock.calls[0][0].messages[0].content[0].type).toBe("image");
  });

  it("refuses guessed coordinates when a screenshot was suppressed", async () => {
    respond({ target_kind: "point", node_id: "", x: 0.5, y: 0.7 });
    expect(await askGuide(cfg, req())).toMatchObject({ target_kind: "none", status: "need_info" });
  });

  it.each(["hi-IN", "en-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN"] as const)("stops unreadable screens without calling the model in %s", async (language) => {
    expect(await askGuide(cfg, req({ language, tree: "", screenshot_b64: undefined })))
      .toMatchObject({ target_kind: "none", status: "cannot_help" });
    expect(create).not.toHaveBeenCalled();
  });

  it("repairs a wrong package only from a unique installed app label", async () => {
    respond({ target_kind: "none", node_id: "", label: "Signal", action: "open_app", action_arg: "com.guessed.signal" });
    expect(await askGuide(cfg, req())).toMatchObject({ action: "open_app", action_arg: "org.thoughtcrime.securesms" });
    respond({ target_kind: "none", node_id: "", label: "Unverified", action: "open_app", action_arg: "com.guessed.app" });
    const fallback = await askGuide(cfg, req());
    expect(fallback.action).toBeUndefined();
    expect(fallback.status).toBe("need_info");
  });

  it("hands app selection back instead of opening or pointing at an ambiguous name", async () => {
    const installed_apps = [
      { package: "org.thoughtcrime.securesms", label: "Signal" }, { package: "example.signal.clone", label: "Signal" },
    ];
    for (const target of [
      { target_kind: "none" as const, node_id: "", action: "open_app" as const, action_arg: "org.thoughtcrime.securesms" },
      { target_kind: "node" as const, node_id: "n1", action: "" as const },
    ]) {
      respond({ ...target, label: "Signal" });
      expect(await askGuide(cfg, req({ app: "com.android.launcher3", installed_apps })))
        .toMatchObject({ status: "next_step", target_kind: "none", say: "More than one app has that name. Please open the one you mean." });
    }
  });

  it("continues inside the selected app even when another app shares its label", async () => {
    respond({});
    expect(await askGuide(cfg, req({ installed_apps: [
      { package: "org.thoughtcrime.securesms", label: "Signal" }, { package: "example.signal.clone", label: "Signal" },
    ] }))).toMatchObject({ target_kind: "node", node_id: "n1" });
  });

  it("allows a specific package to disambiguate an explicit app selection", async () => {
    respond({ target_kind: "none", node_id: "", label: "Signal", action: "open_app", action_arg: "org.thoughtcrime.securesms" });
    expect(await askGuide(cfg, req({ app: "com.android.launcher3", utterance: "Open org.thoughtcrime.securesms", installed_apps: [
      { package: "org.thoughtcrime.securesms", label: "Signal" }, { package: "example.signal.clone", label: "Signal" },
    ] }))).toMatchObject({ action: "open_app", action_arg: "org.thoughtcrime.securesms" });
  });
});
