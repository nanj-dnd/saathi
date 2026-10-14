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
  it("accepts a minimal auto-advance turn", () => {
    const r = parseTurnRequest(base);
    expect(r.utterance).toBeUndefined();
    expect(r.secure_screen).toBe(false);
  });

  it("rejects unsupported languages", () => {
    expect(() => parseTurnRequest({ ...base, language: "fr-FR" })).toThrow(BadRequest);
  });

  it("keeps only the last 8 history steps and sanitises status", () => {
    const history = Array.from({ length: 12 }, (_, i) => ({ say: `s${i}`, status: "weird", app: "a" }));
    const r = parseTurnRequest({ ...base, history });
    expect(r.history).toHaveLength(8);
    expect(r.history[0].say).toBe("s4");
    expect(r.history[0].status).toBe("next_step");
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

  it("defaults unknown enums", () => {
    const g = normaliseGuide({ say: "x", status: "??", danger: "??" }, ids);
    expect(g.status).toBe("next_step");
    expect(g.danger).toBe("none");
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
