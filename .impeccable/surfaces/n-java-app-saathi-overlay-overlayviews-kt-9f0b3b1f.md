---
version: 1
slug: "n-java-app-saathi-overlay-overlayviews-kt-9f0b3b1f"
primary_target: "android/app/src/main/java/app/saathi/overlay/OverlayViews.kt"
related_targets: ["android/app/src/main/java/app/saathi/ui/MainActivity.kt","android/app/src/main/java/app/saathi/overlay/OverlayController.kt"]
---

# Bob overlay + setup

Current identity (2026-11-08): **Bob**, with an authored lowercase **b**, white on flat `#1F5FE0`. Setup, adaptive launcher and themed icon use the same path from `branding/bob-mark.svg`. Bob's spoken voice is the calm male ElevenLabs Eric (`cjVigY5qzO86Huf0OWal`), using `eleven_v3_conversational` across all six app languages. Speech is cached on the Worker and phone, with native Android speech fallback; recognition stays on Android.

Scope: the overlay drawn over every app (Help button, pointer, caption) and the setup screen, plus the launcher icon. Mode: Operate. Audience: Indian seniors with weak eyesight (overlay); the senior or a family member (setup). Task: see exactly where to press, hear and read one line, press it with their own finger. Constraints: TalkBack, dark theme, colour-blind safe (state by shape and text), six languages incl. Indic scripts, 48dp+ targets.

Redesign 2026-10-18: the user called the Metro Wayfinding build ugly (all four surfaces) and pinned "Clicky-style minimal" with a lighter dim. The Metro comps under `.impeccable/mocks/comp-*` are superseded and carry no authority.

## Direction contract
THESIS: Bob is a cursor buddy: one blue pointer flies from the Help button to the exact button while a quiet dark caption says the step. It refuses the bordered signboard, edge bands and boxy outline buttons of instructional UI.
OWN-WORLD: App lightly dimmed (about 22% black) with a soft rounded spotlight and faint blue halo; a plump tilted cursor arrow in Bob blue with a white edge and soft shadow, tip touching the target; a near-black floating caption card (28 radius, 16 inset) in white system sans, first sentence large, the rest at 70% white, two pill buttons inside (white Repeat, translucent Close); a blue pill Help button carrying the cursor glyph and its word. State: small round badges (green tick, amber lock, red warning). Setup: one step per screen, segmented progress, big title, one pinned pill button, language tiles.
STORY: The senior taps Help, speaks, watches the cursor glide to the button, hears and reads one line, presses it. Setup is five short screens with one obvious button each.
FIRST VIEWPORT: Over Settings: light dim, "Display & touch" spotlighted, the blue pointer resting inside it past the label; the dark caption floating at the top below the status bar (bottom only when it would cover the target, per the user's approval) ("Press 'Display & touch'." / "The text size is in here.") with Repeat and Close pills inside; the blue Help pill at the right edge clear of the target.
FORM: Clicky cursor-buddy minimal, pinned by the user over the roll (seed key b6b2c390, assigned index 5 overridden). Raises: warm consumer canon (accent only where to press or what can be pressed); timetable rack (rank by weight and opacity, two sizes per caption); Ikeda datamatics (caption is pure contrast, white on near-black, no grey panels).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
