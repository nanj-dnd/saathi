---
version: 1
slug: "n-java-app-saathi-overlay-overlayviews-kt-9f0b3b1f"
primary_target: "android/app/src/main/java/app/saathi/overlay/OverlayViews.kt"
related_targets: ["android/app/src/main/java/app/saathi/ui/MainActivity.kt","android/app/src/main/java/app/saathi/overlay/OverlayController.kt"]
---

# Saathi overlay + setup

Scope: the overlay drawn over every app (Help button, target ring, arrow, caption sign) and the setup screen, plus the launcher icon. Mode: Operate. Audience: Indian seniors with weak eyesight (overlay); the senior or a family member (setup). Task: see exactly where to press and what it does, press it with their own finger. Constraints: TalkBack, dark theme, colour-blind safe (state by shape and text, never colour alone), six languages incl. Indic scripts, 48dp+ targets.

Approved comps: `.impeccable/mocks/comp-1-pointer-top.png` (primary), companions `comp-2-pointer-bottom.png` (caption at bottom, arrow up, Help listening) and `comp-3-setup-line.png` (setup as a metro line). The user approved all three as one system on 2026-10-15.

Ranked grounded list (resonance): 1 Delhi Metro wayfinding, 2 Indian Railways platform boards, 3 ATM screens with side keys, 4 Doordarshan subtitle strip, 5 bank passbook, 6 wall calendar, 7 India Post inland letter (the roll). The user chose 1 (the pick).

## Direction contract
THESIS: Saathi guides like a metro sign: one heavy arrow, one ringed stop, the instruction on an enamel panel. It refuses the category default of a rounded bottom sheet with a soft glowing highlight.
OWN-WORLD: White enamel sign panels with a thick metro-blue band on the leading edge; near-black bold signage sans; metro blue the only accent; the target ringed by a thick blue rounded outline carrying a solid station dot; arrows solid and geometric; a flat neutral grey veil over everything else. Dark scheme: graphite panel, white type, a lighter blue band and ring.
STORY: The senior sees at once where to press and what will happen, presses it, and the sign moves to the next stop. Setup reads as a short journey of stops, done stops ticked, the current stop large.
FIRST VIEWPORT: Over any app: the sign panel spans the screen edge away from the target, a large arrow at its leading side aimed at the target, the first sentence in big bold type and the rest smaller beneath, a filled blue Repeat and an outline Close set apart; the ring on the target with its station dot; a round blue Help disc at the screen edge.
FORM: Delhi Metro wayfinding, position 1 on my ordered list (IMPECCABLE'S PICK), seed key 8f734108.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
