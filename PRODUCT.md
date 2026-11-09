# Product

<!-- impeccable:product-schema 1 -->

## Platform

Current focus: **Android**. The user shelved the iPhone experiment on 2026-11-09 after a poor live trial. Its code remains an unfinished prototype, not an active product target.

Android 11+ is the existing product. An additional native iPhone-only, portrait iOS 17+ technical trial explores live screen sharing with voice, as explicitly chosen by the user. The iPhone trial does not establish Android feature parity or production readiness.

## Users
- **Older people anywhere** (roughly 60+), with India as the home market (user's decision, 2026-11-09). The first users are **Indian senior citizens** who must use phone apps they find confusing: grocery and food delivery (Blinkit, Zepto, Swiggy, Zomato), cabs (Uber, Ola), UPI and bill payment, WhatsApp, and government services (DigiLocker, UMANG, IRCTC, pension). Many have weak eyesight and low digital literacy, and speak Hindi, Tamil, Telugu, Kannada, Malayalam or simple Indian English.
- **Setup is done by either** a family member or helper, or the senior alone, about equally often. Setup must work for both.

## Product Purpose
On Android, a phone helper in the spirit of Clicky (Farza's macOS buddy). A floating Help button sits over every app. The senior taps it and says what they want; Bob reads the screen, says the single next step aloud in their language and points at the exact button, then looks again after each tap until the task is done. Success is a senior completing a real task on their own phone without calling anyone.

The iPhone trial explores the same one-step spoken help through a user-started ReplayKit broadcast. Bob reads text locally with Vision and speaks a manual next step while the user operates another app. The iPhone has no floating Help button or pointer.

## Positioning
On Android, it points and talks, and never taps. The senior's own hands make every tap, so they learn the app and stay in control, and payment, PIN and OTP steps are always theirs alone. The only things Bob does itself are harmless moves that can't buy, send, delete or change anything: scrolling a list to bring the next button into view, and opening the app a task needs (at the start of a task, never pulling the senior out mid-task). Common tasks run on the phone from scripted recipes; only unknown screens go to the model.

On iPhone, every navigation step is manual: no taps, automatic scroll, app opening or store-page opening. Guidance uses OCR text regions rather than an accessibility tree or cloud screenshots.

## Operating Context
- **Android:** native Android (11+) accessibility service: reads the foreground app's element tree, takes a screenshot, and draws accessibility overlays (bubble, spotlight pointer, caption card) above every app.
- Speech recognition uses Android's recogniser. Spoken guidance uses ElevenLabs Eric, the premade calm male voice (`cjVigY5qzO86Huf0OWal`), through the Worker with `eleven_v3_conversational` in all six app languages. Speech is cached on the Worker and phone; native Android text-to-speech remains the fallback. A Cloudflare Worker calls Claude (Haiku by default) for screens without a recipe.
- Android is distributed as a sideloaded APK for now; Play Store review is still ahead.
- **iPhone trial:** SwiftUI Form/navigation, native system broadcast picker and actual user-consented microphone/SFSpeech recognition in the main app; ElevenLabs Eric output with native iPhone speech fallback. Apple Speech may process microphone audio. The ReplayKit extension ignores app/microphone audio and uses no silent audio keepalive.
- The extension bounds frames to a 1280-pixel longest edge at most once per second and passes only the latest file-protected frame through an on-device App Group. Main-app Vision OCR withholds known sensitive screens and redacts numeric/contact text. Only OCR text and task context go to the Worker, explicitly as `platform=ios`; no screen images are uploaded. Worker iPhone guidance strips Android actions, defaults older clients to Android and isolates shared answer caches by platform.

## Capabilities and Constraints
- Android offers every language Eleven v3 speaks: 85 languages in 86 entries (English for India and for the world), listed in `shared/languages.json`, India first and then the world. Setup text is translated for 48 of them (`shared/i18n/`, built by `shared/i18n/build.py`); the other 37 show setup in English while Bob still listens and speaks in the chosen language. Scripted recipes exist in hi-IN, en-IN, ta-IN, te-IN, kn-IN and ml-IN (English anywhere uses the English ones); every other language is guided by the model. Machine translations have had no native-speaker review.
- Android never taps for the user and dispatches no gestures. It may scroll a list (the accessibility scroll action) and open an app (its launcher intent); the user asked for this on 2026-10-19 to cut the time a task takes.
- Android overlays must never block the app underneath except where the senior is meant to touch (bubble, caption buttons).
- Scam safety is a core function: remote-control apps, OTP/PIN requests and "receive money" QR codes get spoken warnings.
- The iPhone trial interface is English; guidance choices are hi-IN, en-IN, ta-IN, te-IN, kn-IN and ml-IN, subject to Apple recognition/OCR availability. OCR cannot reliably interpret image-only controls.
- iPhone capture is gated by session identity, a host heartbeat, expiry and foreground state; stale frames cannot advance guidance. Sessions end after at most ten minutes or twenty steps and stop on failures/audio interruptions. Temporary frames are cleared at session start/stop.
- iPhone sensitive-screen detection is best effort. Stop sharing before entering secrets; known-screen detection does not establish that a screen is safe. No cross-app overlays, third-party accessibility tree or automated actions are available.
- A signed installation with matching App Group provisioning is required for a physical iPhone trial. Cross-app ReplayKit, background listening/playback, interruptions, extension memory and device-language behavior remain unverified.

## Brand Commitments
- The product name is **Bob**, capitalized in app labels and copy (approved 2026-11-08).
- The logo is a **clay face**: two black dot eyes and a black tick for a smile, slightly 3D, on signboard yellow (user's instruction, 2026-11-09, with eyes wider apart and the smile smaller, lower and to the right). Source and recomposition script in `branding/`; it replaced the blue lowercase b.
- The visual world is **Platform Board** (station signboards: black on signboard yellow), chosen 2026-11-09. **No dark mode**: setup and the overlay look the same in any phone theme.
- The voice is calm and male: ElevenLabs Eric. Android recognition, speech caching and native speech fallback remain part of the product.
- Internal compatibility identifiers remain `app.saathi` and `saathi-worker` (including its deployed URL); they are not product labels.

## Evidence on Hand
- Working Android build and live Worker (https://saathi-worker.nanj-dnd.workers.dev); 123 scripted recipes in `shared/recipes`.
- iPhone trial evidence (2026-11-09): ten native tests, including real Vision OCR on synthetic screens; 88 Worker tests and TypeScript checks; Simulator Debug, unsigned iPhone Release and signed iPhone Debug builds. A Simulator smoke run received live model guidance and an ElevenLabs MP3 and completed playback using synthetic screen input. These results do not verify live cross-app capture or physical-device audio. The ten native tests passed again after the final `UIColor.label` contrast correction.
- An iPhone 16 Pro is connected and paired. An Apple Personal Team is configured, Apple Development certificates are valid, and both provisioning profiles include the shared App Group. A signed Debug build succeeded and Bob is installed on that phone. The developer profile was trusted and Bob launched successfully; Bob and BobBroadcast processes were observed. The user reported no voice during the first live Settings trial. A diagnostic build is installed and running while that failure is investigated; actual cross-app guidance/playback remains unverified. Debug-only trial diagnostics record event names, counts, status and error codes in Bob’s own app cache, never screen text, transcripts, tokens or images. No App Store approval or production-readiness claim is supported.
- No user testing with real seniors yet, no testimonials, no metrics. None may be invented.

## Product Principles
1. The senior's hands do every step; Bob only shows and tells.
2. One instruction at a time, in the senior's own language and words.
3. Anything involving money, PINs or codes is protected, slowed down, and handed back to the senior.
4. Calm over clever: nothing on screen should startle, rush or scold.

## Accessibility & Inclusion
- One theme only (no dark mode, by the user's choice); the overlay's yellow and black must read on both light and dark apps.
- On Android, TalkBack must be able to operate the bubble, caption buttons and every setup step.
- Never rely on colour alone (colour-blind safe): states carry shape, icon or text too.
- Users are older with weaker eyesight: large type and generous touch targets throughout.
- Right-to-left languages (Arabic, Urdu, Persian, Hebrew, Pashto, Sindhi) mirror setup and the caption board.
- The iPhone trial uses SF semantic Dynamic Type, system light/dark grouped surfaces, native navigation/pickers and SF Symbols. Supporting, placeholder, status and privacy text use semantic `UIColor.label` foreground; filled blue actions use white text, symbols and loading indicator. Status remains visible even before a caption exists. Native controls retain system accessibility behavior; VoiceOver and enlarged-text behavior on a physical iPhone still require verification.
