import type { Guide, Language, TurnRequest } from "./schema.ts";

/**
 * Scripted guidance for recording demos without an API key (set DEMO=1 in .dev.vars).
 * Walks "make the text bigger" in Android Settings by matching what is actually on screen,
 * so the phone side (tree capture, pointer, auto-advance) runs for real. Not used otherwise.
 */

type Line = Partial<Record<Language, string>> & { "hi-IN": string };

const LINES = {
  scroll: {
    "hi-IN": "स्क्रीन पर उंगली रखकर ऊपर की ओर सरकाइए।",
    "ta-IN": "திரையில் விரலை வைத்து மேலே தள்ளுங்கள்.",
  },
  display: {
    "hi-IN": "'Display & touch' पर दबाइए। अक्षरों का साइज़ इसी में है।",
    "ta-IN": "'Display & touch' ஐ அழுத்துங்கள். எழுத்து அளவு இதில் உள்ளது.",
  },
  size: {
    "hi-IN": "अब 'Display size and text' पर दबाइए।",
    "ta-IN": "இப்போது 'Display size and text' ஐ அழுத்துங்கள்.",
  },
  larger: {
    "hi-IN": "'Font size' के दाईं ओर '+' बटन दबाइए। अक्षर बड़े होंगे।",
    "ta-IN": "'Font size' வலது பக்கம் உள்ள '+' ஐ அழுத்துங்கள். எழுத்துகள் பெரிதாகும்.",
  },
  again: {
    "hi-IN": "और बड़ा चाहिए तो एक बार और दबाइए।",
    "ta-IN": "இன்னும் பெரிதாக வேண்டுமென்றால் மீண்டும் அழுத்துங்கள்.",
  },
  done: {
    "hi-IN": "बहुत बढ़िया! अब अक्षर बड़े हो गए हैं।",
    "ta-IN": "அருமை! இப்போது எழுத்துகள் பெரிதாகிவிட்டன.",
  },
  lost: {
    "hi-IN": "Settings ऐप खोलिए, फिर मदद बटन दबाइए।",
    "ta-IN": "Settings ஆப்பைத் திறந்து, உதவி பொத்தானை அழுத்துங்கள்.",
  },
} satisfies Record<string, Line>;

const say = (line: Line, lang: Language) => line[lang] ?? line["hi-IN"];

/** First clickable node whose label contains [text]. */
function find(tree: string, text: string): string | null {
  for (const l of tree.split("\n")) {
    if (l.includes(text) && /\bclick\b/.test(l)) return /^\s*(n\d+)/.exec(l)?.[1] ?? null;
  }
  return null;
}

export function demoGuide(req: TurnRequest): Guide {
  const lang = req.language;
  const goal = "make the text bigger";
  const step = (line: Line, nodeId: string | null, label: string, status: Guide["status"] = "next_step"): Guide => ({
    say: say(line, lang),
    goal,
    target_kind: nodeId ? "node" : "none",
    node_id: nodeId ?? "",
    x: -1,
    y: -1,
    label: nodeId ? label : "",
    status,
    danger: "none",
    expect: "",
  });

  const larger = find(req.tree, '"Make larger"');
  if (larger && req.tree.includes("Font size")) {
    const presses = req.history.filter((h) => h.label === "Make larger").length;
    if (presses === 0) return step(LINES.larger, larger, "Make larger");
    if (presses === 1) return step(LINES.again, larger, "Make larger");
    return step(LINES.done, null, "", "done");
  }
  const size = find(req.tree, "Display size and text");
  if (size) return step(LINES.size, size, "Display size and text");
  const display = find(req.tree, "Display & touch");
  if (display) return step(LINES.display, display, "Display & touch");
  if (req.app === "com.android.settings") return step(LINES.scroll, null, "");
  return step(LINES.lost, null, "", "need_info");
}
