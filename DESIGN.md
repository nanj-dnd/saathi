---
name: Bob
description: A pointing-hand buddy for Indian seniors; one blue hand flies to the exact button while a quiet dark caption says the step.
colors:
  saathi-blue: "#1F5FE0"
  saathi-blue-night-line: "#4D8BFF"
  on-blue: "#FFFFFF"
  caption-near-black: "#15171C"
  caption-night: "#22252B"
  caption-edge-night: "#FFFFFF1F"
  caption-ink: "#FFFFFF"
  caption-ink-soft: "#FFFFFFB3"
  pill-white: "#FFFFFF"
  pill-quiet: "#FFFFFF29"
  hand-edge: "#FFFFFF"
  veil-day: "#00000038"
  veil-night: "#00000052"
  ground-day: "#FFFFFF"
  ground-night: "#0F1114"
  ink-day: "#101215"
  ink-night: "#F3F4F6"
  ink-soft-day: "#5B616B"
  ink-soft-night: "#A4AAB3"
  tile-day: "#F2F3F5"
  tile-night: "#1C1F24"
  raised-day: "#FFFFFF"
  raised-night: "#33373F"
  track-day: "#E3E6EA"
  track-night: "#2A2E35"
  done-green: "#1E9E5A"
  caution-amber: "#F5A524"
  danger-red: "#E5484D"
typography:
  display:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "32px"
    fontWeight: 700
    lineHeight: "38px"
  headline:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.3
  title:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: 1.4
  body-caption:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: 1.38
  body:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: 1.45
  body-small:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
  label-button:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: 1.4
  label:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "19px"
    fontWeight: 600
    lineHeight: 1.4
  label-help:
    fontFamily: "Roboto, Noto Sans Devanagari, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Kannada, Noto Sans Malayalam, sans-serif"
    fontSize: "19px"
    fontWeight: 700
    lineHeight: 1.4
rounded:
  spotlight: "18px"
  tile: "20px"
  frame: "24px"
  card: "28px"
  pill: "9999px"
spacing:
  track: "4px"
  hair: "6px"
  sm: "8px"
  md: "12px"
  gutter: "14px"
  lg: "16px"
  card: "20px"
  page: "24px"
  step-top: "36px"
components:
  help-pill:
    backgroundColor: "{colors.saathi-blue}"
    textColor: "{colors.on-blue}"
    typography: "{typography.label-help}"
    rounded: "{rounded.pill}"
    padding: "0 22px 0 18px"
    height: "56px"
  help-pill-busy:
    backgroundColor: "{colors.caption-near-black}"
    textColor: "{colors.caption-ink}"
    typography: "{typography.label-help}"
    rounded: "{rounded.pill}"
    height: "56px"
  caption-card:
    backgroundColor: "{colors.caption-near-black}"
    textColor: "{colors.caption-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.card}"
    padding: "20px 20px 16px"
  caption-card-night:
    backgroundColor: "{colors.caption-night}"
    textColor: "{colors.caption-ink}"
    rounded: "{rounded.card}"
    padding: "20px 20px 16px"
  pill-repeat:
    backgroundColor: "{colors.pill-white}"
    textColor: "{colors.caption-near-black}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "56px"
  pill-close:
    backgroundColor: "{colors.pill-quiet}"
    textColor: "{colors.caption-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "56px"
  badge-done:
    backgroundColor: "{colors.done-green}"
    textColor: "{colors.on-blue}"
    rounded: "{rounded.pill}"
    size: "36px"
  badge-caution:
    backgroundColor: "{colors.caution-amber}"
    textColor: "{colors.caption-near-black}"
    rounded: "{rounded.pill}"
    size: "36px"
  badge-danger:
    backgroundColor: "{colors.danger-red}"
    textColor: "{colors.on-blue}"
    rounded: "{rounded.pill}"
    size: "36px"
  primary-pill:
    backgroundColor: "{colors.saathi-blue}"
    textColor: "{colors.on-blue}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    padding: "14px 24px"
    height: "60px"
  quiet-button:
    backgroundColor: "{colors.tile-day}"
    textColor: "{colors.ink-day}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "12px 20px"
    height: "56px"
  text-action:
    textColor: "{colors.saathi-blue}"
    rounded: "{rounded.pill}"
    padding: "8px 12px"
    height: "48px"
  language-tile:
    backgroundColor: "{colors.tile-day}"
    textColor: "{colors.ink-day}"
    typography: "{typography.title}"
    rounded: "{rounded.tile}"
    padding: "12px 18px"
    height: "76px"
  language-tile-selected:
    backgroundColor: "{colors.saathi-blue}"
    textColor: "{colors.on-blue}"
    typography: "{typography.title}"
    rounded: "{rounded.tile}"
    padding: "12px 18px"
    height: "76px"
  segmented-track:
    backgroundColor: "{colors.tile-day}"
    rounded: "{rounded.pill}"
    padding: "4px"
  segmented-selected:
    backgroundColor: "{colors.raised-day}"
    textColor: "{colors.ink-day}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    height: "56px"
  progress-segment:
    backgroundColor: "{colors.track-day}"
    rounded: "{rounded.pill}"
    height: "6px"
  progress-segment-reached:
    backgroundColor: "{colors.saathi-blue}"
    rounded: "{rounded.pill}"
    height: "6px"
---

# Design System: Bob

## Overview

**Creative North Star: "The Pointing Hand"**

Bob is a pointer and a voice, not a signboard. Over any app, one blue pointing hand flies on an upward arc from the Help pill and lands with its fingertip inside the exact button, then taps it with a ripple; the app is only lightly dimmed and the target sits in a soft rounded spotlight with a faint blue halo. A near-black caption card floats above it all and says the step in white system sans: the first sentence large and bold, the rest smaller at 70% white. Everything the senior might press is a full pill.

The system is clean, modern and calm. Blue is spent only where to press (the hand, the spotlight halo and its tap ripple) and on what can be pressed (the Help pill, the primary pill, a selected tile). The caption is pure contrast, white on near-black, with no grey panels and no borders by day. Setup is the same world on a white ground: one step per screen, five progress segments, a big title, soft grey tiles and one pinned blue pill at the bottom.

The build explicitly refuses the instructional-UI vocabulary of the superseded world: bordered signboards, edge bands and boxy outline buttons.

Bob's spoken voice is the calm male ElevenLabs Eric (`cjVigY5qzO86Huf0OWal`), using `eleven_v3_conversational` across all six app languages. Speech is cached on the Worker and phone, with native Android speech fallback; recognition stays on Android.

**Key Characteristics:**
- The logo is an unmistakable lowercase b: white on Bob Blue, with a tall stem and one clear open counter. The background is flat, with no gradient. Independently authored vector geometry in `branding/bob-mark.svg` supplies the setup mark, adaptive launcher and monochrome themed icon; no font dependency or generated raster ships as the mark.
- One blue pointing hand (48dp, white edge, soft shadow) is the system's in-use mark: pointer and Help pill glyph.
- Light veil (22% black by day, 32% at night) with a rounded spotlight; the app stays readable.
- Near-black floating caption card, two type sizes ranked by weight and opacity.
- Every action is a full pill; containers are softly rounded rectangles (18 to 28dp).
- State reads by badge shape (tick, lock, warning) and word, never colour alone.
- The phone's own sans (Roboto plus Android's Noto Indic faces), with explicit line heights for Indic scripts.

## Colors

One saturated blue on a near-black and white base, with three badge colours that only ever appear as round fills behind a glyph.

### Primary
- **Bob Blue** (#1F5FE0): the hand, the Help pill, the primary pill, the selected language tile, reached progress segments, the setup wordmark disc and the launcher background. The same fill in light and dark.
- **Night Line Blue** (#4D8BFF): blue as a line or text on a dark ground at night (spotlight halo and hairline, listening pulse, segmented tick, text actions). By day the line colour is Bob Blue itself.

### Neutral
- **Caption Near-Black** (#15171C): the caption card by day, the Help pill while listening or looking, and the ink on white pills and on the amber badge.
- **Caption Night** (#22252B): the caption card at night, lifted off dark apps by a 1dp hairline (#FFFFFF at 12%).
- **Caption Ink** (#FFFFFF) and **Caption Ink Soft** (white at 70%): the caption's first sentence and the rest.
- **Pill White** (#FFFFFF) and **Pill Quiet** (white at 16%): Repeat and Close inside the card.
- **Hand Edge** (#FFFFFF): the white rim around the hand and the Help pill that keeps them visible on any app colour.
- **Veil** (black at 22% by day, 32% at night): the dim over everything except the spotlight.
- **Setup ground, ink and tiles**: white ground (#FFFFFF / night #0F1114), ink (#101215 / #F3F4F6), soft ink for body copy (#5B616B / #A4AAB3), tiles and quiet buttons (#F2F3F5 / #1C1F24), the raised segment (#FFFFFF / #33373F), the unreached progress track (#E3E6EA / #2A2E35).

### Status (badge fills only)
- **Done Green** (#1E9E5A) with a white tick.
- **Caution Amber** (#F5A524) with a near-black lock: steps the senior must do alone (PIN, OTP, payment).
- **Danger Red** (#E5484D) with a white knocked-out warning triangle: scam warnings.

### Named Rules
**The Press-Here Rule.** Outside the approved logo treatment, blue marks only where to press or what can be pressed. Body copy, headings, cards and decoration are never blue.

**The Pure Contrast Rule.** The caption is white on near-black. No grey panels, tinted cards or coloured caption backgrounds; rank is carried by size, weight and the 70% white of the second line.

**The Shape-Not-Hue Rule.** Every state carries a glyph or word as well as a colour: tick, lock, warning triangle, voice bars, spinning arc, the tick inside a selected tile or segment.

## Typography

**Display Font:** Roboto (system default sans)
**Body Font:** Roboto, with Android's Noto Sans Devanagari, Tamil, Telugu, Kannada and Malayalam shaping each script
**Label/Mono Font:** none distinct

**Character:** The phone's own voice, plain and familiar, so a mixed line like "'Display & touch' पर दबाइए" sets as one sentence. Hierarchy comes from weight (400 / 600 / 700) and size, not from a display face.

### Hierarchy
- **Display** (700, 32sp, 38sp line): setup step titles, one per screen.
- **Headline** (700, 24sp, 1.3 line height): the caption's first sentence; scales with system font size up to 1.3x.
- **Title** (600, 24sp): language names in their own script on the language tiles.
- **Body Caption** (400, 18sp, 1.38 line height, 70% white): the rest of the caption beneath the headline, 6dp below it.
- **Body** (400, 18sp, 1.45 line height, soft ink): setup explanations. The access disclosure uses **Body Small** (400, 16sp, 24sp line).
- **Label Button** (600, 20sp): the pinned primary pill.
- **Label** (600, 19sp): Repeat, Close, quiet buttons, segment labels (700 when selected), confirmation rows.
- **Label Help** (700, 19sp): the Help pill's word; shrinks in 0.5sp steps to fit a 240dp pill, never below 15sp.

### Named Rules
**The Two Sizes Rule.** A caption uses exactly two sizes: the first sentence large and bold, everything after it small and regular. The split happens at the first `.`, `!`, `?` or `।`.

**The Indic Line Rule.** Line heights are always explicit (about 1.3 to 1.45x) with trimming off, so Devanagari matras and Dravidian vowel signs are never clipped and a two-line instruction never falls apart into two lines. Pill words shrink to fit (to 13sp) rather than truncate.

## Layout

Overlay: three windows over the app. The caption spans the screen width with a 14dp margin around the card, sits just below the status bar by default, and moves to the bottom (above the navigation bar or an open keyboard) only when it would cover the target. The Help pill is side-anchored: it starts on the right at 62% of screen height, snaps to the nearer edge after a drag, keeps its outer edge on the screen edge as its word changes width, and slides to the opposite side (or above/below a full-width target) whenever it comes within 24dp of the target. The spotlight pads the target by 6dp, grows small targets to at least 56dp, and stays 12dp in from screen edges. On a wide row the hand lands at 78% of the row's width, past the label and short of a trailing switch; near the right or bottom edge it mirrors.

Setup: one step per screen in a single column with 24dp side margins. A 64dp top bar (wordmark on the first step, a 48dp back target after), five progress segments (6dp tall, 6dp gaps), then content starting 36dp below with 16dp between blocks. The primary pill is pinned at the bottom (8dp above, 16dp below). The language grid is two columns with 12dp gaps. Steps change with a short horizontal slide of a sixth of the width plus fade (320ms in, 220ms out).

Touch targets are at least 56dp for every overlay control and setup button (60dp for the primary pill, 48dp minimum for text actions and back).

## Elevation & Depth

Soft, ambient shadows on the few things that float over someone else's app, plus a dim-and-spotlight that pushes the app back. Setup is flat apart from the raised segment and the illustrative pills.

### Shadow Vocabulary
- **Hand lift** (blur 6dp, y 2dp, black at 35%): under the hand's white edge.
- **Help pill lift** (blur 8dp, y 3dp, black at 25%): under the Help pill, overlay and setup alike.
- **Caption float** (Android elevation 10dp): the caption card.
- **Spotlight halo** (18dp outer blur in the line blue at about 78%, plus a 1dp hairline at about 59%): the target's edge.
- **Raised segment** (elevation 2dp): the selected half of the segmented control.

### Named Rules
**The Light Veil Rule.** The app underneath is dimmed, never blacked out (22% by day, 32% at night), so the senior still recognises their own screen.

## Shapes

Round and soft. Every pressable control is a full pill (Help, Repeat, Close, primary, quiet, segments, progress). Containers are generously rounded rectangles: the caption card at 28dp, illustrative frames at 24dp, tiles and notices at 20dp, the spotlight at 18dp. Badges and the wordmark disc are circles. Icons are drawn on a 24-unit grid with one 2.4-unit rounded stroke (round caps and joins); the hand is a filled silhouette of rounded shapes (index finger up, three folded fingers, palm, thumb). White edges exist only where something must separate from an unknown background: the hand's rim, the Help pill's 1.5dp rim, the night caption hairline.

## Components

### Bob Logo
The product label is **Bob**; the mark is lowercase **b**.
- **Geometry:** reuse the authored path in `branding/bob-mark.svg`: tall left stem, rounded lower bowl and one clear open counter. Never substitute a typed glyph.
- **Treatment:** white mark on flat Bob Blue in the setup disc and launcher; the standalone master is blue on transparent. Setup uses a 34dp disc with a 24dp mark, a 10dp gap and the 21sp bold Bob label.
- **Variants:** setup `logo_mark.xml`, adaptive `ic_launcher_foreground.xml` and themed `ic_launcher_monochrome.xml` share the same path. The launcher uses a solid background; the monochrome layer is tinted by Android.
- **Provenance:** `branding/bob-logo-concept.png` is a critique reference only. Its exact prompt is embedded and preserved in `branding/bob-logo.prompt.txt`; production vectors are independently authored.

### Help Pill (signature)
The senior's way in: a blue pill carrying the hand glyph and the word "Help".
- **Shape:** full pill, 56dp tall, 18dp start / 22dp end padding, 24dp glyph with 8dp gap, 240dp maximum width.
- **Idle:** Bob Blue fill, white glyph and word, 1.5dp white rim (black at 40% at night), soft lift shadow.
- **Listening:** near-black fill, four white voice bars rising and falling out of step, two soft line-blue halos breathing outward; a haptic tap when the mic opens.
- **Looking:** near-black fill, a line-blue arc spinning on a faint white ring.
- **Behaviour:** draggable, snaps to the nearer edge, reported to TalkBack as a button.

### Pointing Hand and Spotlight (signature)
The one signature motion. The hand (48dp, blue, white edge, soft shadow) flies on a quadratic arc that bows upward by 22% of its length, 720ms on Material's emphasized curve (0.3, 0, 0.1, 1), trailing 14 fading line-blue dots; it lands with its fingertip inside the target and then taps it every 1.5s: the finger dips 3dp and shrinks 6% while a line-blue ring spreads 24dp from the fingertip and fades. When Bob scrolls or opens an app itself, the caption says so in one short line and no hand is shown. The spotlight morphs from the previous target to the next during the flight; a first step fades the veil in over the first half. With "Remove animations" on, everything cuts instantly.

### Caption Card
- **Corner Style:** 28dp radius.
- **Background:** Caption Near-Black (Caption Night plus hairline after dark).
- **Shadow Strategy:** caption float (10dp elevation).
- **Internal Padding:** 20dp sides and top, 16dp bottom; buttons 18dp below the text, 14dp apart.
- **Content:** headline plus soft second line, each up to four lines; a 36dp badge leads the text (14dp gap), centred on the first line, for done, do-it-yourself and warning captions. TalkBack reads the whole caption as one announcement.

### Buttons
- **Shape:** full pill everywhere.
- **Repeat (in caption):** white fill, near-black word and drawn repeat glyph (22dp, 8dp gap), 56dp tall.
- **Close (in caption):** white at 16% on the card, white word and drawn close glyph.
- **Primary (setup):** Bob Blue, white 20sp semibold word, 60dp minimum, full width, pinned at the bottom; exactly one per step.
- **Quiet (setup):** tile grey fill, ink word with an optional 22dp glyph, 56dp, full width.
- **Text action:** line-blue 18sp semibold word, no fill, offset 12dp left so the word aligns with the column while its padding holds overhanging matras.

### Chips / Selection
- **Language tiles:** two-column grid, 76dp minimum, 20dp radius, tile grey with the name in its own script; selected becomes Bob Blue with a white 26dp disc holding a blue tick.
- **Segmented control:** tile-grey pill track with 4dp inset; the selected half is a raised white pill with a line-blue tick and bold ink word, the other half medium soft ink.

### Navigation
- **Progress:** five pill segments, 6dp tall; blue up to and including the current step, track grey after.
- **Back:** a drawn chevron (26dp) in a 48dp target; system back steps back too.

### Status Rows
- **Badge:** 36dp circle in the caption, glyph at 60% of its width.
- **Done row (setup):** 30dp green disc with white tick, 19sp semibold ink label.
- **Notice (setup):** amber at 16% on a 20dp-radius block, 18dp padding, warning glyph plus 17sp ink text.

## Do's and Don'ts

### Do:
- **Do** keep blue for where to press and what can be pressed: hand, spotlight halo, Help pill, primary pill, selected tile.
- **Do** float the caption at the top below the status bar, and move it to the bottom only when it would cover the target.
- **Do** keep the Help pill against a screen edge and move it to the other side when it comes within 24dp of the target.
- **Do** split every caption into a large bold first sentence and a smaller 70%-white remainder.
- **Do** make every pressable control a full pill at least 56dp tall.
- **Do** pair every state colour with a drawn glyph or word (tick, lock, warning, voice bars, arc).
- **Do** draw icons from the shared 24-unit glyph set with its one rounded stroke; never mix in an icon font.
- **Do** set explicit line heights for Indic scripts and shrink pill words to fit instead of truncating.
- **Do** honour "Remove animations": the hand's flight, tap and pulses become instant cuts.

### Don't:
- **Don't** build bordered signboards, edge bands or boxy outline buttons; the caption is a borderless floating card.
- **Don't** put grey panels, tints or colour inside the caption card; it stays white on near-black.
- **Don't** dim the app heavily; the veil stays at 22% by day and 32% at night.
- **Don't** let the hand or the Help pill sit over the next row or the target; the tip rests inside the target.
- **Don't** use colour alone for any state, selection or warning.
- **Don't** put more than one primary pill on a setup step.
