# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
- **Indian senior citizens** (roughly 60+) who must use phone apps they find confusing: grocery and food delivery (Blinkit, Zepto, Swiggy, Zomato), cabs (Uber, Ola), UPI and bill payment, WhatsApp, and government services (DigiLocker, UMANG, IRCTC, pension). Many have weak eyesight and low digital literacy, and speak Hindi, Tamil, Telugu, Kannada, Malayalam or simple Indian English.
- **Setup is done by either** a family member or helper, or the senior alone, about equally often. Setup must work for both.

## Product Purpose
A phone helper in the spirit of Clicky (Farza's macOS buddy). A floating Help button sits over every app. The senior taps it and says what they want; Saathi reads the screen, says the single next step aloud in their language and points at the exact button, then looks again after each tap until the task is done. Success is a senior completing a real task on their own phone without calling anyone.

## Positioning
It points and talks, never taps. The senior's own hands complete every step, so they learn the app and stay in control, and payment, PIN and OTP steps are always theirs alone. Common tasks run on the phone from scripted recipes; only unknown screens go to the model.

## Operating Context
- Native Android (11+) accessibility service: reads the foreground app's element tree, takes a screenshot, and draws accessibility overlays (bubble, spotlight pointer, caption card) above every app.
- Speech uses the phone's own recogniser and text-to-speech. A Cloudflare Worker calls Claude (Haiku by default) for screens without a recipe.
- Distributed as a sideloaded APK for now; Play Store review is still ahead.

## Capabilities and Constraints
- Six languages: hi-IN, en-IN, ta-IN, te-IN, kn-IN, ml-IN. Every visible string and spoken line exists in all six.
- Point-and-talk only: no gesture dispatch, never taps for the user.
- Overlays must never block the app underneath except where the senior is meant to touch (bubble, caption buttons).
- Scam safety is a core function: remote-control apps, OTP/PIN requests and "receive money" QR codes get spoken warnings.
- Undecided: final product name (see Brand Commitments).

## Brand Commitments
- "Saathi" is a **working name** and may change; design must not depend on the name or its meaning.
- Visual direction requested by the user (2026-10-15): clean and modern.

## Evidence on Hand
- Working Android build and live Worker (https://saathi-worker.nanj-dnd.workers.dev); 123 scripted recipes in `shared/recipes`.
- No user testing with real seniors yet, no testimonials, no metrics. None may be invented.

## Product Principles
1. The senior's hands do every step; Saathi only shows and tells.
2. One instruction at a time, in the senior's own language and words.
3. Anything involving money, PINs or codes is protected, slowed down, and handed back to the senior.
4. Calm over clever: nothing on screen should startle, rush or scold.

## Accessibility & Inclusion
- Dark theme must work properly, for the overlay and setup alike.
- TalkBack must be able to operate the bubble, caption buttons and every setup step.
- Never rely on colour alone (colour-blind safe): states carry shape, icon or text too.
- Users are older with weaker eyesight: large type and generous touch targets throughout.
