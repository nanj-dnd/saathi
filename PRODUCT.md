# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
- **Indian senior citizens** (roughly 60+) who must use phone apps they find confusing: grocery and food delivery (Blinkit, Zepto, Swiggy, Zomato), cabs (Uber, Ola), UPI and bill payment, WhatsApp, and government services (DigiLocker, UMANG, IRCTC, pension). Many have weak eyesight and low digital literacy, and speak Hindi, Tamil, Telugu, Kannada, Malayalam or simple Indian English.
- **Setup is done by either** a family member or helper, or the senior alone, about equally often. Setup must work for both.

## Product Purpose
A phone helper in the spirit of Clicky (Farza's macOS buddy). A floating Help button sits over every app. The senior taps it and says what they want; Bob reads the screen, says the single next step aloud in their language and points at the exact button, then looks again after each tap until the task is done. Success is a senior completing a real task on their own phone without calling anyone.

## Positioning
It points and talks, and never taps. The senior's own hands make every tap, so they learn the app and stay in control, and payment, PIN and OTP steps are always theirs alone. The only things Bob does itself are harmless moves that can't buy, send, delete or change anything: scrolling a list to bring the next button into view, and opening the app a task needs (at the start of a task, never pulling the senior out mid-task). Common tasks run on the phone from scripted recipes; only unknown screens go to the model.

## Operating Context
- Native Android (11+) accessibility service: reads the foreground app's element tree, takes a screenshot, and draws accessibility overlays (bubble, spotlight pointer, caption card) above every app.
- Bob's spoken voice is the calm male ElevenLabs Eric (`cjVigY5qzO86Huf0OWal`), using `eleven_v3_conversational` across all six app languages. Speech is cached on the Worker and phone, with native Android speech fallback; recognition stays on Android. A Cloudflare Worker calls Claude (Haiku by default) for screens without a recipe.
- Distributed as a sideloaded APK for now; Play Store review is still ahead.

## Capabilities and Constraints
- Guidance works inside any installed Android app whose screen can be read. Recipes are shortcuts rather than an app allowlist; unfamiliar flows use the general guide and model. Secure/unreadable screens hand control back to the user. See `docs/APP_COMPATIBILITY.md`.
- Six languages: hi-IN, en-IN, ta-IN, te-IN, kn-IN, ml-IN. Every visible string and spoken line exists in all six.
- Never taps for the user and dispatches no gestures. It may scroll a list (the accessibility scroll action) and open an app (its launcher intent); the user asked for this on 2026-10-19 to cut the time a task takes.
- Overlays must never block the app underneath except where the senior is meant to touch (bubble, caption buttons).
- Scam safety is a core function: remote-control apps, OTP/PIN requests and "receive money" QR codes get spoken warnings.

## Brand Commitments
- The product name is **Bob**, approved on 2026-11-08.
- The logo is an unmistakable lowercase b: white on Bob Blue, with a tall stem and one clear open counter. The background is flat, with no gradient. Independently authored vector geometry in `branding/bob-mark.svg` supplies the setup mark, adaptive launcher and monochrome themed icon; no font dependency or generated raster ships as the mark.
- Internal compatibility identifiers remain `app.saathi` and `saathi-worker`; they are not product labels.
- Visual direction requested by the user (2026-10-15): clean and modern.

## Evidence on Hand
- Working Android build and live Worker (https://saathi-worker.nanj-dnd.workers.dev); 178 scripted recipes in `shared/recipes`.
- No user testing with real seniors yet, no testimonials, no metrics. None may be invented.

## Product Principles
1. The senior's hands do every step; Bob only shows and tells.
2. One instruction at a time, in the senior's own language and words.
3. Anything involving money, PINs or codes is protected, slowed down, and handed back to the senior.
4. Calm over clever: nothing on screen should startle, rush or scold.

## Accessibility & Inclusion
- Dark theme must work properly, for the overlay and setup alike.
- TalkBack must be able to operate the bubble, caption buttons and every setup step.
- Never rely on colour alone (colour-blind safe): states carry shape, icon or text too.
- Users are older with weaker eyesight: large type and generous touch targets throughout.
