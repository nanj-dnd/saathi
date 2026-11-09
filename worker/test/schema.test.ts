import { describe, expect, it } from "vitest";
import { issueToken, verifyToken } from "../src/auth.ts";
import { buildUserText } from "../src/prompt.ts";
import { BadRequest, nodeIdsFromTree, normaliseGuide, parseTurnRequest } from "../src/schema.ts";

const base = {
  language: "hi-IN",
  goal: "",
  app: "com.android.settings",
  playbook: "",
  screen_w: 1080,
  screen_h: 2400,
  tree: 'n1 TextView "Settings" [0,100,1080,200]\nn2 Button "Display" [0,300,1080,400] click',
  history: [],
};

describe("parseTurnRequest", () => {
  it("defaults old clients to Android and validates explicit iOS requests", () => {
    expect(parseTurnRequest(base).platform).toBe("android");
    expect(parseTurnRequest({ ...base, platform: "ios" }).platform).toBe("ios");
    expect(() => parseTurnRequest({ ...base, platform: "web" })).toThrow(BadRequest);
  });
  it("accepts a minimal auto-advance turn", () => {
    const r = parseTurnRequest(base);
    expect(r.utterance).toBeUndefined();
    expect(r.secure_screen).toBe(false);
  });

  it("rejects unsupported languages", () => {
    expect(() => parseTurnRequest({ ...base, language: "xx-XX" })).toThrow(BadRequest);
  });

  it.each(["toString", "valueOf", "__proto__"])("rejects inherited language key %s", (language) => {
    expect(() => parseTurnRequest({ ...base, language })).toThrow(BadRequest);
  });

  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])("rejects invalid screen dimensions %s", (size) => {
    expect(() => parseTurnRequest({ ...base, screen_w: size })).toThrow(BadRequest);
    expect(() => parseTurnRequest({ ...base, screen_h: size })).toThrow(BadRequest);
  });

  it("keeps only the last 8 history steps and sanitises status", () => {
    const history = Array.from({ length: 12 }, (_, i) => ({ say: `s${i}`, status: "weird", app: "a" }));
    const r = parseTurnRequest({ ...base, history });
    expect(r.history).toHaveLength(8);
    expect(r.history[0].say).toBe("s4");
    expect(r.history[0].status).toBe("next_step");
  });

  it("accepts bounded app context while keeping old clients compatible", () => {
    expect(parseTurnRequest(base).installed_apps).toEqual([]);
    const installed_apps = [{ package: "example.reader.app", label: "Reader" }];
    expect(parseTurnRequest({ ...base, app_name: "Reader", installed_apps })).toMatchObject({ app_name: "Reader", installed_apps });
  });

  it("rejects malformed or excessive installed app matches", () => {
    for (const installed_apps of ["apps", [null], [{ package: "bad package", label: "Reader" }],
      [{ package: "example.reader", label: "" }], Array(13).fill({ package: "example.reader", label: "Reader" }),
    ]) expect(() => parseTurnRequest({ ...base, installed_apps })).toThrow(BadRequest);
  });
});

describe("normaliseGuide", () => {
  const ids = nodeIdsFromTree(base.tree);

  it("keeps a valid node target", () => {
    const g = normaliseGuide(
      { say: "x", target_kind: "node", node_id: "n2", x: 0.5, y: 0.5, status: "next_step", danger: "none" },
      ids,
    );
    expect(g.target_kind).toBe("node");
    expect(g.node_id).toBe("n2");
    expect(g.x).toBe(-1);
  });

  it("drops a hallucinated node id", () => {
    const g = normaliseGuide({ say: "x", target_kind: "node", node_id: "n99" }, ids);
    expect(g.target_kind).toBe("none");
    expect(g.node_id).toBe("");
  });

  it("drops out-of-range points", () => {
    const g = normaliseGuide({ say: "x", target_kind: "point", x: 1.4, y: 0.2 }, ids);
    expect(g.target_kind).toBe("none");
  });

  it.each([
    { danger: "sensitive_screen" }, { danger: "scam_suspected" }, { status: "user_must_do" },
  ])("removes node and point targets on protected steps: %j", (protectedFields) => {
    for (const target_kind of ["node", "point"]) {
      const g = normaliseGuide({
        say: "Do this yourself", status: "next_step", danger: "none",
        target_kind, node_id: "n2", x: 0.5, y: 0.5,
        action: "scroll_down", ...protectedFields,
      }, ids);
      expect(g).toMatchObject({ target_kind: "none", node_id: "", x: -1, y: -1, action: "", action_arg: "" });
    }
  });

  it("defaults unknown enums", () => {
    const g = normaliseGuide({ say: "x", status: "??", danger: "??" }, ids);
    expect(g.status).toBe("next_step");
    expect(g.danger).toBe("none");
    expect(g.target_kind).toBe("none");
  });
});

describe("prompt", () => {
  it("marks auto-advance turns", () => {
    const text = buildUserText(parseTurnRequest(base));
    expect(text).toContain("the screen changed");
    expect(text).toContain("Hindi");
  });

  it("quotes what the senior said", () => {
    const text = buildUserText(parseTurnRequest({ ...base, utterance: "  दूध मंगाना है " }));
    expect(text).toContain('Senior said: "दूध मंगाना है"');
  });
});

describe("auth", () => {
  it("round-trips and rejects tampering", async () => {
    const token = await issueToken("secret");
    const id = await verifyToken("secret", `Bearer ${token}`);
    expect(id).toBe(token.split(".")[0]);
    expect(await verifyToken("other", `Bearer ${token}`)).toBeNull();
    expect(await verifyToken("secret", `Bearer x${token}`)).toBeNull();
    expect(await verifyToken("secret", null)).toBeNull();
  });
});

describe("guide actions", () => {
  const base = {
    say: "WhatsApp खोल रही हूँ।", goal: "call on WhatsApp", target_kind: "none", node_id: "", x: -1, y: -1,
    label: "WhatsApp", status: "next_step", danger: "none", expect: "",
  };
  const ids = new Set(["n1"]);

  it("keeps a safe action with a real package", () => {
    const g = normaliseGuide({ ...base, action: "open_app", action_arg: "com.whatsapp" }, ids);
    expect(g.action).toBe("open_app");
    expect(g.action_arg).toBe("com.whatsapp");
    expect(normaliseGuide({ ...base, action: "scroll_down", action_arg: "x" }, ids)).toMatchObject({ action: "scroll_down", action_arg: "" });
  });

  it("drops actions on warnings, the senior's own steps, pointed steps and made-up packages", () => {
    expect(normaliseGuide({ ...base, action: "open_app", action_arg: "com.whatsapp", danger: "scam_suspected" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, action: "scroll_down", status: "user_must_do" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, action: "scroll_down", target_kind: "node", node_id: "n1" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, action: "open_store_page", action_arg: "WhatsApp app" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, action: "tap", action_arg: "com.whatsapp" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, action: "none" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, status: "cannot_help", action: "scroll_down" }, ids).action).toBe("");
    expect(normaliseGuide({ ...base, status: "need_info", action: "open_app", action_arg: "com.whatsapp" }, ids).action).toBe("");
  });
});

describe("languages", () => {
  it("accepts every language in shared/languages.json, India and world alike", async () => {
    const { LANGUAGE_ENTRIES } = await import("../src/schema.ts");
    expect(LANGUAGE_ENTRIES.length).toBe(86);
    for (const tag of ["fr-FR", "es-MX", "ar-SA", "yue-HK", "fil-PH", "bn-IN", "cy-GB"]) {
      expect(parseTurnRequest({ ...base, language: tag }).language).toBe(tag);
    }
  });

  it("maps tags to Eleven v3 language codes, including the three-letter and Norwegian ones", async () => {
    const { ELEVENLABS_CODE } = await import("../src/schema.ts");
    expect(ELEVENLABS_CODE["nb-NO"]).toBe("no");
    expect(ELEVENLABS_CODE["fil-PH"]).toBe("fil");
    expect(ELEVENLABS_CODE["yue-HK"]).toBe("yue");
    expect(ELEVENLABS_CODE["zh-CN"]).toBe("zh");
    expect(new Set(Object.values(ELEVENLABS_CODE)).size).toBe(85);
  });
});
