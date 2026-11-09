---
name: Bob
description: Station signboards for a phone helper. The step is set black on signboard yellow, the things you press are black boards with yellow lettering, and a black hand with a yellow rim lands on the button.
colors:
  signboard-yellow: "#FFD200"
  board-ink: "#121212"
  paper: "#FFFFFF"
  icon-yellow: "#FDD202"
  frame-grey: "#F4F4F4"
  track-grey: "#D9D9D9"
  veil: "#00000040"
  done-green: "#1E8A4C"
  danger-red: "#D7262E"
typography:
  sign:
    fontFamily: "Paytone One (Latin only); other scripts: the phone's sans at 800"
    fontWeight: 400
  title:
    fontFamily: "{typography.sign}"
    fontSize: "31px"
    lineHeight: "40px"
  label-button:
    fontFamily: "{typography.sign}"
    fontSize: "21px"
    lineHeight: 1.4
  label:
    fontFamily: "{typography.sign}"
    fontSize: "19px"
    lineHeight: 1.4
  strip-count:
    fontFamily: "{typography.sign}"
    fontSize: "16px"
    letterSpacing: "0.5px"
  caption-head:
    fontFamily: "Roboto plus Android's Noto faces"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.3
  caption-sub:
    fontFamily: "Roboto plus Android's Noto faces"
    fontSize: "18px"
    fontWeight: 500
    lineHeight: 1.38
  body:
    fontFamily: "Roboto plus Android's Noto faces"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: 1.45
  language-native:
    fontFamily: "Roboto plus Android's Noto faces"
    fontSize: "23px"
    fontWeight: 600
  language-english:
    fontFamily: "Roboto plus Android's Noto faces"
    fontSize: "15px"
    fontWeight: 400
rounded:
  board: "8px"
  help-board: "10px"
  face: "5px"
  spotlight: "18px"
spacing:
  keyline-inset: "5px"
  keyline: "2px"
  rivet: "2.4px"
  page: "24px"
  block: "16px"
  card-pad: "22px"
components:
  strip:
    backgroundColor: "{colors.signboard-yellow}"
    textColor: "{colors.board-ink}"
    rounded: "{rounded.board}"
    height: "68px"
  primary-board:
    backgroundColor: "{colors.board-ink}"
    textColor: "{colors.signboard-yellow}"
    typography: "{typography.label-button}"
    rounded: "{rounded.board}"
    height: "64px"
  quiet-board:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.board-ink}"
    rounded: "{rounded.board}"
    height: "60px"
  language-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.board-ink}"
    height: "64px"
  language-row-selected:
    backgroundColor: "{colors.board-ink}"
    textColor: "{colors.signboard-yellow}"
  section-label:
    backgroundColor: "{colors.board-ink}"
    textColor: "{colors.signboard-yellow}"
  caption-board:
    backgroundColor: "{colors.signboard-yellow}"
    textColor: "{colors.board-ink}"
    rounded: "{rounded.board}"
    padding: "22px 22px 20px"
  repeat-board:
    backgroundColor: "{colors.board-ink}"
    textColor: "{colors.signboard-yellow}"
    height: "56px"
  close-board:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.board-ink}"
    height: "56px"
  help-board:
    backgroundColor: "{colors.signboard-yellow}"
    textColor: "{colors.board-ink}"
    rounded: "{rounded.board}"
    size: "68px"
---

# Design System: Bob

## Overview

**Creative North Star: "The Platform Board"**

Bob talks like a station signboard: the step in your own script, black on signboard yellow, telling you exactly where to press next. Every board is a flat rectangle with slightly rounded corners, a 2dp black keyline set 5dp in from its edge, and a small rivet in each corner. Things you press are black boards with yellow lettering. Over another app, a black hand with a thick yellow rim flies to the button and taps it inside a spotlight ringed in yellow and keylined in black. The face of the product is a clay mark: two black dot eyes and a tick for a smile on yellow.

There is **one theme**. Setup and the overlay look the same whatever the phone's dark-mode setting (the user's instruction, 2026-11-09): setup is a white page under a yellow strip, and the overlay's yellow and black read on light and dark apps alike.

This world replaced the blue Clicky-style minimal look on 2026-11-09 (direction seed cc8f7b37, the Platform Board card). It refuses the soft grey-tile Material helper it replaced: no grey text, no pastel tiles, no pill-shaped everything.

Bob speaks every language Eleven v3 speaks (85, in `shared/languages.json`), in the calm male ElevenLabs Eric voice; the picker lists India first and then the world.

**Key characteristics:**
- Signboard yellow and board black carry everything; white is the page.
- Text is full ink or full yellow, never grey. Rank comes from size and weight.
- State reads by fill and shape as well as colour: a chosen row floods black and carries a drawn tick; badges are a tick, a lock or a warning triangle.
- The word itself is the control. A language row is just the language's name, set large in its own script.
- Steps change as whole pages with a short slide; nothing sits half-scrolled between steps.
- Latin board lettering is Paytone One. Every other script uses the phone's own face at extra-bold, so Hindi, Arabic or Thai boards are never thin.

## Colors

Two inks and a page.

- **Signboard Yellow** (#FFD200): the setup strip, the caption board, lettering on black boards, the hand's rim, the Help board (Bob's face on yellow), the spotlight halo.
- **Board Ink** (#121212): every word on white or yellow, the black boards (primary, Repeat, section labels), the chosen language row, keylines and rivets, the hand itself.
- **Paper** (#FFFFFF): the setup page, the Close board, the language list.
- **Icon Yellow** (#FDD202): the launcher and in-app mark's ground, sampled from the clay render so the adaptive layers meet without a seam.
- **Frame Grey** (#F4F4F4) and **Track Grey** (#D9D9D9): the panel that stages the Help board in setup, and unreached progress bars. Never text.
- **Veil** (black at 25%): the dim over an app outside the spotlight.
- **Done Green** (#1E8A4C) and **Danger Red** (#D7262E): badge fills only, always with a white glyph. "Yours alone" (PIN, OTP, payment) is a black badge with a yellow lock, because amber would vanish on the yellow board.

### Named Rules
**The Two Inks Rule.** Black and yellow do all the talking. Nothing that can be read is grey.

**The Press-Here Rule.** Black boards are for pressing. Yellow on white marks the strip and the caption, never a button.

## Typography

**Board lettering:** Paytone One (OFL, `android/app/src/main/res/font/paytone_one.ttf`), chosen by measuring the approved comp's headline. It covers Latin only; `SaathiType.signFor` and the setup screen's `signFamily` switch any non-Latin text to the phone's sans at weight 800.

**Captions and body:** the phone's own sans (Roboto plus Android's Noto faces), so a mixed line like "नीचे 'Alarm' पर दबाइए" sets as one sentence.

### Hierarchy
- **Title** (sign, 31sp, 40sp line): one per setup step.
- **Label Button** (sign, 21sp): primary boards. **Label** (sign, 19sp): Repeat, Close, quiet boards, segments, Help.
- **Strip count** (sign, 16sp): "STEP 1 OF 5", localised.
- **Caption head** (700, 24sp) and **caption sub** (500, 18sp), both full black; the split is at the first sentence end. Both scale with the system font up to 1.3x.
- **Language native** (600, 23sp) above **language English** (400, 15sp) when the two differ.
- **Body** (400, 18sp, full ink).

### Named Rules
**The Indic Line Rule.** Line heights are explicit (1.3 to 1.45x) with trimming off, so matras and vowel signs are never clipped.

## Layout

Setup: a yellow strip (12dp in from the sides, at least 68dp tall) holds the 46dp face, "bob" and the step count. Five progress bars (6dp, black up to the current step) sit under it, then one scrolling column with 24dp margins and 16dp between blocks. A "Back" text action and the 64dp primary board are pinned at the bottom. The language step is a 60dp search field, then up to three keylined lists (On this phone, India, World), each topped by a black section label.

Overlay: the caption board spans the screen width with 14dp around it, under the status bar unless it would cover the target. The Help board sits on a side edge and moves off the target. The spotlight pads the target by 6dp, grows small targets to 56dp and stays 12dp in from the screen edges.

Right-to-left languages (Arabic, Urdu, Persian, Hebrew, Pashto, Sindhi) mirror the whole of setup and the caption board.

Touch targets: 64dp primary board, 60dp rows, segments and quiet boards, 56dp overlay controls, 48dp text actions.

## Elevation & Depth

Flat boards. Depth only where something floats over someone else's app: the caption board (elevation 10dp, cast from the board's own outline), the Help board (8dp blur, 3dp drop), the hand (6dp blur under its yellow rim), the primary board (3dp). The spotlight halo is an 18dp yellow outer blur plus a 2dp black keyline.

## Shapes

Boards are 8dp rectangles, the Help board included. The spotlight is 18dp. Badges and rivets are circles. Glyphs keep their 24-unit grid and 2.4-unit round stroke. The hand is a filled silhouette.

## Components

### Bob Mark
A clay face: two round black eyes and a black tick for a smile, slightly 3D, on yellow. The source is `branding/bob-clay-face-source.png` (Figma AI render); `branding/make_face.py` recomposes it with the user's adjustments (eyes 400px apart, smile at 70%, lower and to the right) and writes the launcher foreground, the in-app mark (`res/drawable-nodpi/bob_mark.png`) and `branding/bob-icon-1024.png`. Every one carries its provenance in the file. The themed icon is a flat vector of the same face (`branding/bob-face.svg`, `ic_launcher_monochrome.xml`).

### Strip (setup)
Yellow board with keyline and rivets: mark, "bob" in Paytone One, step count at the far end.

### Language Picker
Search field (white board, black keyline, drawn magnifier) matching native name, English name or tag. Sections: the phone's language when Bob offers it, India, World. Each row shows the language in its own script with its English name under it; the chosen row floods black with yellow type and a drawn yellow tick.

### Primary, Quiet and Text actions
Primary: black board, yellow lettering, rivets. Quiet: white board, black keyline on the edge, black lettering. Text action: black, underlined.

### Help Board (overlay signature)
Bob himself: a 68dp square yellow board with the keyline and rivets, and the clay face sitting small in the middle (12dp in from each side, 1.5dp low; 44dp in the setup preview), clear of the keyline and rivets, with no word. Listening swaps the face for black voice bars with a soft black pulse; looking, for a black spinning arc. TalkBack still hears "Help", "Listening" or "Looking". Everything Bob says or shows calls the board "Bob" ("Press Bob and speak again"; setup's Ready step: "press the yellow Bob board"), never "Help", since no word is drawn on it. The setup screens never show the floating board over themselves. Keyline and rivets are drawn over the face so the image never hides them.

### Caption Board
Yellow board, keyline and rivets. Step large, the rest smaller, both black. Repeat is a black board with yellow lettering; Close is a white board with a black keyline. A badge (tick, lock, warning) leads the text when the caption is more than a step.

### Pointing Hand and Spotlight (overlay signature)
A black hand with a 3dp yellow rim flies on an arc from the Help board and taps the target with a black ripple; the trail is black dots.

## Do's and Don'ts

### Do:
- Set every readable word in full black or full yellow.
- Give non-Latin board text the phone's face at extra-bold.
- Keep the keyline, the rivets and the 8dp corners on every board.
- Test right-to-left languages whenever setup or the caption changes.

### Don't:
- Bring back a dark theme, grey body text or pill-shaped buttons.
- Use yellow for a button on white, or blue anywhere.
- Put a coloured bar on the side of a row or card.

## iPhone trial

The shelved iPhone trial (`ios/`) predates this world and still uses the blue lowercase b. It is not maintained.
