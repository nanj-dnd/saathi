---
version: 1
slug: "n-java-app-saathi-overlay-overlayviews-kt-9f0b3b1f"
primary_target: "android/app/src/main/java/app/saathi/overlay/OverlayViews.kt"
related_targets: ["android/app/src/main/java/app/saathi/ui/MainActivity.kt","android/app/src/main/java/app/saathi/overlay/OverlayController.kt"]
---

# Bob overlay + setup

Scope: the overlay drawn over every app (Help button, pointer, caption) and the setup screen, plus the launcher icon. Mode: Operate. Audience: older people anywhere with weak eyesight, India as the home market (overlay); the senior or a family member (setup). Task: see exactly where to press, hear and read one line, press it with their own finger. Constraints: TalkBack, colour-blind safe (state by shape and text), 85 languages including Indic and right-to-left scripts, 56dp+ targets.

Redesign 2026-11-09: the user found the blue Clicky-minimal build unfinished-looking and opened everything (mark, colour, type, layout). Direction round seed cc8f7b37: the user took the Platform Board card over the assigned Daily Sheet. Same day the user asked for every language ElevenLabs speaks (85) and a seniors-anywhere audience, **no dark mode** (setup is always light; the overlay is the same in any phone theme), and a new mark: **a face of two dot eyes and a tick for a smile, slightly 3D like clay** (`branding/bob-clay-face-source.png`, flat themed version `branding/bob-face.svg`). The blue lowercase b, the Clicky comps and the dark-setup comp (`board-3-setup-night.png`) are superseded.

Approved comps (2026-11-09): `.impeccable/mocks/board-4-setup-language-world.png` (language step), `.impeccable/mocks/board-2-overlay.png` (overlay). `board-3-setup-night.png` was approved for its numbered-panel structure only; its dark ground is withdrawn by the no-dark-mode instruction. Do not literalize: comp copy (the build uses real strings), the black left bars on rows (refused by the craft floor; rules and boards carry the world), the arrow glyph on rows.

## Direction contract
THESIS: Bob speaks like a station board: the step in your own script, black on signboard yellow, telling you exactly where to press next. It refuses the soft grey-tile Material helper it replaces.
OWN-WORLD: Signboard yellow #FFD200, board black #121212, white ground, no greys for text. Boards are flat rectangles with a 2dp black inset keyline and four small rivet dots; actions are black boards with yellow lettering; selected rows flood black with yellow text and a drawn tick. Overpass (signage face) for Latin board lettering, system Noto for every other script. Clay face mark on yellow. Hand pointer black with a thick yellow rim.
STORY: Setup is five boards: language (search, On this phone / India / World), microphone, see the screen, voice, ready. Over any app the senior hears and reads one board line, sees the black hand on the button, presses it.
FIRST VIEWPORT: Setup step 1: compact yellow strip header (mark, bob, STEP 1 OF 5), title "Choose your language", search field, phone-language row selected, India then World rows in their own scripts, black Continue board pinned at the bottom. Overlay: yellow caption board under the status bar (bottom only when it would cover the target), step large, Repeat (black board) and Close (white keyline board); light veil and spotlight; black hand with yellow rim in the target; black Help board on the side edge.
FORM: Platform Board, IMPECCABLE'S PICK, my rank 1 of 7, seed cc8f7b37. Raises carried from the round: no grey text (datamatics), state by fill as well as colour (labanotation), the word itself is the control (metro), printed-rule alignment (j-card), hard snap between whole steps (feed).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
