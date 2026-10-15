---
name: Saathi
description: A point-and-talk phone helper for Indian seniors, drawn as Delhi Metro wayfinding over any app.
colors:
  metro-blue: "#0858A8"
  metro-blue-night-line: "#6AB2F2"
  metro-blue-night-fill: "#1E6CC0"
  enamel-white: "#FFFFFF"
  graphite-panel: "#1C1E22"
  graphite-raised: "#2A2D33"
  night-ground: "#111215"
  ring-edge-night: "#0B0C0E"
  signage-ink: "#111214"
  signage-ink-soft: "#3B3F45"
  night-ink: "#F4F5F7"
  night-ink-soft: "#C3C7CE"
  veil-day: "#0000005C"
  veil-night: "#00000080"
  close-grey: "#E9EBEE"
  chip-grey: "#ECEEF1"
  hairline: "#DDE1E6"
  track-grey: "#C9CDD3"
  track-night: "#3A3E45"
  done-green: "#1B7F3B"
  done-green-night: "#4CC071"
  caution-amber: "#9A5B00"
  caution-amber-night: "#F0A63A"
  danger-red: "#C62828"
  danger-red-night: "#FF6B6B"
typography:
  display:
    fontFamily: "Anek, sans-serif"
    fontSize: "40px"
    fontWeight: 800
    lineHeight: 1.2
  sign-head:
    fontFamily: "Anek, sans-serif"
    fontSize: "27px"
    fontWeight: 700
    lineHeight: 1.3
  sign-sub:
    fontFamily: "Anek, sans-serif"
    fontSize: "20px"
    fontWeight: 500
    lineHeight: 1.35
  stop-current:
    fontFamily: "Anek, sans-serif"
    fontSize: "26px"
    fontWeight: 700
    lineHeight: 1.2
  stop:
    fontFamily: "Anek, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: 1.2
  body:
    fontFamily: "Anek, sans-serif"
    fontSize: "19px"
    fontWeight: 400
    lineHeight: "27px"
  body-small:
    fontFamily: "Anek, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "23px"
  button:
    fontFamily: "Anek, sans-serif"
    fontSize: "21px"
    fontWeight: 700
    lineHeight: 1.2
  button-secondary:
    fontFamily: "Anek, sans-serif"
    fontSize: "18px"
    fontWeight: 600
    lineHeight: 1.2
  disc-word:
    fontFamily: "Anek, sans-serif"
    fontSize: "16px"
    fontWeight: 700
    fontVariation: "'wdth' 88"
rounded:
  none: "0px"
  sign: "8px"
  button: "10px"
  ring: "16px"
  chip: "26px"
  disc: "9999px"
spacing:
  hairline: "6px"
  gap: "10px"
  inset: "12px"
  sign-gap: "14px"
  panel: "18px"
  gutter: "24px"
  button-apart: "28px"
components:
  sign-panel:
    backgroundColor: "{colors.enamel-white}"
    textColor: "{colors.signage-ink}"
    typography: "{typography.sign-head}"
    rounded: "{rounded.sign}"
    padding: "18px"
  sign-panel-docked:
    backgroundColor: "{colors.enamel-white}"
    textColor: "{colors.signage-ink}"
    rounded: "{rounded.none}"
    padding: "18px"
  sign-panel-night:
    backgroundColor: "{colors.graphite-panel}"
    textColor: "{colors.night-ink}"
    rounded: "{rounded.sign}"
  button-repeat:
    backgroundColor: "{colors.metro-blue}"
    textColor: "{colors.enamel-white}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
    height: "60px"
  button-close:
    backgroundColor: "{colors.enamel-white}"
    textColor: "{colors.metro-blue}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
    height: "60px"
  button-close-on-veil:
    backgroundColor: "{colors.close-grey}"
    textColor: "{colors.metro-blue}"
    rounded: "{rounded.button}"
    height: "60px"
  button-secondary:
    backgroundColor: "{colors.enamel-white}"
    textColor: "{colors.metro-blue}"
    typography: "{typography.button-secondary}"
    rounded: "{rounded.button}"
    padding: "12px 16px"
    height: "56px"
  help-disc:
    backgroundColor: "{colors.metro-blue}"
    textColor: "{colors.enamel-white}"
    typography: "{typography.disc-word}"
    rounded: "{rounded.disc}"
    size: "80px"
  language-chip:
    backgroundColor: "{colors.chip-grey}"
    textColor: "{colors.signage-ink}"
    rounded: "{rounded.chip}"
    padding: "10px 20px"
    height: "52px"
  choice-selected:
    backgroundColor: "{colors.metro-blue}"
    textColor: "{colors.enamel-white}"
    rounded: "{rounded.button}"
    padding: "0 12px"
    height: "56px"
---

# Design System: Saathi

## Overview

**Creative North Star: "The Metro Sign"**

Saathi guides the way a Delhi Metro sign does: one heavy arrow, one ringed stop, the instruction set on a white enamel panel with a thick metro-blue band on its leading edge. Everything else on screen is dimmed under a flat neutral veil, so the only lit things are the sign, the ring around the target, and the round blue Help disc. The setup screen is the same world read as a line map: five stops on a rail, done stops ticked, the current stop large with its sign beside it.

The system is built for older eyes and six scripts. Type is big and bold (Anek, a signage sans from Ek Type, in the script of the senior's language), touch targets never drop below 52 to 60 units, and every state is carried by a drawn glyph's shape as well as its colour. The world refuses the category default of a rounded bottom sheet with a soft glowing highlight: the sign is either an inset enamel card or a square-cornered platform board, and the target is ringed with a hard blue line, not lit.

All dimensions below are Android dp (sp for setup text), written as px in the tokens at 1px = 1dp. Both themes are first-class: the night scheme swaps to a graphite panel, white type, and a lighter blue line with a deeper blue fill.

**Key Characteristics:**
- White enamel panels, near-black bold signage type, metro blue the only accent.
- A thick coloured band on the leading edge of every sign; band colour and glyph shape together say what kind of sign it is.
- A thick blue ring with a bright edge and a solid station dot marks the one place to press.
- Flat veil, flat panels, square docking; depth only where a layer must hold over an unknown app.
- One drawn icon set on a 24-unit grid, used by overlay and setup alike.
- Motion is one glide of the ring from stop to stop, and it all stops when the system says Remove animations.

## Colors

A white-enamel and near-black signage palette with metro blue as the single accent, plus three band colours that each travel with their own glyph.

### Primary
- **Metro Blue** (metro-blue): the band on press-here signs, the ring and its station dot, the Help disc, the filled Repeat button, outline strokes, selected chips and choices, and the setup rail up to the current stop. In the day theme the line and the fill are the same blue.
- **Night Line Blue** (metro-blue-night-line): the night-theme band, ring, dot, outline strokes, rail and button text on outlines. Light enough to read as a line on graphite.
- **Night Fill Blue** (metro-blue-night-fill): the night-theme fill under white text and ticks (Help disc, Repeat, primary buttons, selected choice, done-stop discs). Kept deeper than the line blue so white on it holds contrast.

### Tertiary (state bands)
- **Done Green** (done-green / done-green-night): band of the done sign, always with the tick glyph; the setup done-line disc.
- **Caution Amber** (caution-amber / caution-amber-night): band of the "do this yourself" sign, always with the lock glyph; the setup notice border, always with the warning triangle.
- **Danger Red** (danger-red / danger-red-night): band and glyph of the scam warning, always the warning triangle.

### Neutral
- **Enamel White** (enamel-white): sign panel, day ground of setup, text and glyphs on blue fills, and the ring's bright edge in the day theme.
- **Graphite Panel** (graphite-panel): night sign panel.
- **Graphite Raised** (graphite-raised): night Close-on-veil fill, unselected chips and the sign hairline at night.
- **Night Ground** (night-ground): night setup background and system bars.
- **Ring Edge Night** (ring-edge-night): the near-black edge under the ring, around the station dot, and around the Help disc in the night theme.
- **Signage Ink** (signage-ink) / **Night Ink** (night-ink): first sentence of a sign, titles, arrows.
- **Signage Ink Soft** (signage-ink-soft) / **Night Ink Soft** (night-ink-soft): the smaller sentences beneath, setup body copy, upcoming stop numbers.
- **Veil** (veil-day, 36% black; veil-night, 50% black): the flat dim over everything except the target.
- **Close Grey** (close-grey): opaque fill of Close when it sits on the veil, so the app never shows through its word.
- **Chip Grey** (chip-grey), **Hairline** (hairline), **Track Grey** (track-grey / track-night): unselected language chips, the setup sign's 1-unit border, and the rail and markers of stops not yet reached.

### Named Rules
**The One Blue Rule.** Metro blue is the only accent. Green, amber and red appear only as a sign's band and glyph or a status mark, never as decoration or a second brand colour.

**The Shape-Plus-Colour Rule.** No state is told by colour alone. Press here is an arrow, done is a tick, do-it-yourself is a lock, warning is a triangle, a note with no target is a station stop. A selected choice carries a tick; a reached stop carries a tick; a selected chip carries a heavier weight and an outline.

**The Line and Fill Rule.** At night, blue that is a line (band, ring, stroke, text on an outline) uses the lighter line blue; blue that carries white on it uses the deeper fill blue.

## Typography

**Display Font:** Anek (one self-hosted variable file per script: Latin, Devanagari, Tamil, Telugu, Kannada, Malayalam; system sans only as a load failure fallback)
**Body Font:** Anek, same file
**Label Font:** Anek, same file, at width 88 for the Help disc word

**Character:** A signage sans with real Indic coverage; each script file also carries Latin, so a mixed line like "'Display & touch' पर दबाइए" sets in one face. Weight does the hierarchy work: 700 for the instruction, 500 for what follows.

### Hierarchy
- **Display** (800, 40, 1.2): the setup wordmark only.
- **Sign Head** (700, 27, line height 1.3): the first sentence of an instruction, up to four lines.
- **Sign Sub** (500, 20, line height 1.35): the rest of the instruction, in soft ink, 6 below the head.
- **Stop Current** (700, 26): the current setup stop's number and title; its number in blue.
- **Stop** (600, 20): other stop titles; done in ink, upcoming in soft ink.
- **Body** (400, 19, 27 line): setup tagline and Ready text. A lead summary may step up to 21/600/29.
- **Body Small** (400, 16, 23 line): disclosure and hints; notices at 17/25.
- **Button** (700, 21): Repeat, Close and primary actions; shrinks in half-unit steps to fit beside its glyph, never below 13. Secondary actions 18/600; choices 19/600; language chips 21 at 600, 700 when selected.
- **Disc Word** (700, 16, width 88): Help / Listening / Looking under the disc glyph; never below 14.

### Named Rules
**The First Sentence Rule.** A sign splits at its first sentence end (. ! ? or ।): that sentence is large and bold, the rest smaller beneath. One instruction reads at a glance.

**The Indic Line Rule.** Set the typeface before the size and line height, give every overlay text an explicit line height (1.3 head, 1.35 sub), and turn fallback line spacing and font padding off. Devanagari and the Dravidian scripts' tall metrics otherwise push a two-line instruction apart into what reads as two lines.

**The 1.3 Cap Rule.** Overlay type follows the system font scale up to 1.3× and no further, so a sign never covers the screen; the cap is read on every render.

**The Own-Script Chip Rule.** Each language chip is set in its own script's face, whatever the current language.

## Layout

The overlay places the sign on the half of the screen away from the target (and above an open keyboard). At the top it is an inset card, 12 from each side, 24 below the top (36 when the target is low), with Repeat and Close in a row 12 beneath it on the veil. At the bottom it docks full width to the screen edge (or the keyboard's top), with the buttons inside the panel and padded clear of the navigation bar. Repeat and Close split the width equally with 28 between them so Close is never hit by accident.

Inside the sign: a 12-wide band (10 in setup), a 64 glyph square, then the text column padded 18. The Help disc keeps 24 clear of the target, moving to the far side of the screen when a step needs it, and returns to where the senior put it at idle.

Setup is a single scrolling column with 24 gutters and 28 top and bottom. The rail sits 22 in from the column edge, 6 thick; stops are 26 apart and their content stacks 12 apart. Signs stack their content 14 apart. When the current step's action would scroll away, it pins to the bottom on the ground colour with 24 by 16 padding.

### Named Rules
**The Away-From-Target Rule.** The sign, the disc and the buttons never cover the thing the senior is meant to press.

## Elevation & Depth

Depth is mostly flat: the veil is a single flat neutral cut out around the target, the setup sign is a panel with a 1-unit hairline and no shadow, and the ring is drawn flat. Depth appears only where a layer floats over an unknown app and must separate from it: the inset sign at 4 elevation, the docked sign at 8 (square-cornered, so it reads as a board, not a sheet), and the Help disc with a soft shadow plus a 3-unit bright rim that keeps it visible on blue, orange and red apps.

### Shadow Vocabulary
- **Sign inset** (Android elevation 4): the top inset card.
- **Sign docked** (Android elevation 8): the bottom platform board.
- **Disc lift** (shadow layer, blur 6, y 2, black at 33%): the Help disc only.

### Named Rules
**The Flat Veil Rule.** The veil is one flat neutral with a hard cutout. No gradient, no blur, no glow around the target.

**The Bright Edge Rule.** Anything blue that floats over an app (ring, station dot, Help disc) carries a bright edge: white by day, near-black at night.

## Shapes

Corners are modest and purposeful: 8 for enamel sign panels and notices, 10 for every button and choice, 16 for the ring, 26 for language-chip pills, full circles for the Help disc, station dot and stop markers, and 0 for the docked sign. Strokes are heavy: 6 ring over an 11 edge, 2.5 button and chip outlines, 6 rail, 7 current-stop ring.

The drawn glyph set (24-unit grid, 3.2 stroke, round caps and joins) holds the arrow (solid, wide head, thick shaft), tick, lock, warning triangle with a knocked-out mark, station stop, microphone, repeat arc, close cross, speaker, and the guide mark: an arrow arriving at a station dot, which is also the launcher icon in white on metro blue.

### Named Rules
**The Station Dot Rule.** The station dot is the system's signature: a solid blue dot with a bright edge on the leading end of the ring, at the end of the guide mark, and in the launcher.

## Components

### Buttons
Heavy, rounded-rectangle sign buttons with a drawn glyph beside the word.
- **Shape:** gently rounded rectangle (10), at least 60 tall (56 for secondary and choices).
- **Repeat / Primary:** metro blue fill (night fill blue at night), white glyph and bold 21 word, glyph 24 with a 10 gap.
- **Close:** 2.5 blue outline with blue glyph and word. Inside a docked sign it sits on the panel colour; on the veil it takes an opaque neutral fill (close grey by day, graphite raised at night).
- **Glyph alignment:** the glyph is centred on the word's ink, not its line box, so it lines up with Devanagari headlines and tall Dravidian vowels.
- **Secondary:** 2.5 blue outline, blue 18/600 centred word, 12 by 16 padding so two-line labels clear the border.
- **Choice:** a two-way outlined pair; the selected one fills blue and gains a tick.
- **Accessibility:** each reads as a Button named by its word, not its icon.

### Chips
- **Style:** 26-radius pills, at least 52 tall, 20 by 10 padding, chip grey fill, ink text in the language's own script.
- **State:** selected takes a 14% metro-blue tint, a 2.5 blue outline, blue text and bold weight; exposed as a radio button.

### Cards / Containers
- **Overlay sign:** enamel panel, 8 radius inset or 0 docked, 12 band on the leading edge in the sign kind's colour, glyph in ink for arrows and in the band colour otherwise. TalkBack reads the whole sign as one announcement.
- **Setup sign:** enamel panel, 8 radius, 1 hairline border, 10 blue band, 18 padding, content 14 apart.
- **Notice:** 2 caution-amber border, 8 radius, 14 padding, warning glyph beside 17/400 text.

### Navigation (Setup Line)
Five stops on a 6-wide vertical rail: blue up to the current stop, track grey after. Done stops are 30 blue discs with a white tick; the current stop is a 34 open ring with a 7 blue stroke and a 26 bold title; upcoming stops are 22 open grey rings. Any stop title is a heading and a button that reopens it; done stops announce their done state.

### Ring Pointer (Signature Component)
A touch-through layer: the veil with a 16-radius cutout, a 6 metro-blue ring over an 11 bright edge, and a 9 station dot (with a 2.5 edge) on the ring's leading end. The ring is never smaller than 64 on either side and is kept fully on screen. It glides from its last stop, or from the Help disc, to the next target (520ms, decelerating); once arrived, only the station dot breathes (1600ms, up to 18% larger). With Remove animations on, it cuts straight to the target and stays still. Hidden entirely, with no veil, for scam warnings.

### Help Disc
A metro-blue disc with a 3 bright rim, the guide mark (microphone while listening) above its state word. It is sized once per language to fit the longest of its state words (80 to 124 in 2 steps) and keeps a fixed centre when that size changes, so it never resizes or moves between states. Listening sends two rings outward; Looking runs a white arc around the inside (both 1400ms, both off under Remove animations). Draggable, and a Button to TalkBack.

## Do's and Don'ts

### Do:
- **Do** pair every state with its glyph shape: arrow for press here, tick for done, lock for do-it-yourself, warning triangle for warnings, station stop for a note.
- **Do** put the sign at the top as an inset 8-radius card with Repeat and Close on the veil beneath, or at the bottom docked full width at 0 radius with the buttons inside.
- **Do** give Close an opaque neutral fill whenever it sits on the veil.
- **Do** make buttons 10-radius rounded rectangles at least 60 tall, with the glyph centred on the word's ink.
- **Do** size the Help disc once per language to its longest state word and keep its centre fixed.
- **Do** give the ring a bright edge (white by day, near-black at night) and a station dot on its leading end, and never draw it smaller than 64.
- **Do** set explicit line heights with fallback line spacing off for all overlay text.
- **Do** cap overlay font scale at 1.3× and honour Remove animations everywhere.
- **Do** draw every icon from the 24-unit glyph set.

### Don't:
- **Don't** use a rounded bottom sheet or a soft glowing highlight around the target; the veil is a flat neutral with a hard cutout and the ring is a hard line.
- **Don't** add a second accent colour; green, amber and red live only in bands, status marks and their glyphs.
- **Don't** let any state depend on colour alone.
- **Don't** shrink sign buttons' words below 13 or the disc word below 14; grow the container first.
- **Don't** cover the target with the sign, the disc or the buttons.
- **Don't** use emoji, font icons or text characters as icons; draw them.
