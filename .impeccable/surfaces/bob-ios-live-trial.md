---
version: 1
slug: bob-ios-live-trial
primary_target: ios/Bob/ContentView.swift
related_targets: [ios/Bob/LiveSession.swift, ios/Bob/LiveVoice.swift, ios/Broadcast/SampleHandler.swift]
---

# Bob iOS live-help trial (shelved)

Mode: Operate. Native iPhone, portrait, iOS 17+. The user requested an iOS trial and chose live screen sharing with voice first. Existing Bob identity and the user's pinned blue/lowercase-b/male voice carry over. This is a technical trial adapting established Bob controls to native iOS Form, navigation, permissions and broadcast UI, with no new brand/world decision.

THESIS: Bob gives one spoken manual next step while a senior uses another iPhone app. The user makes every tap.
OWN-WORLD: Existing #1F5FE0 tint and authored white lowercase b, native SF Dynamic Type, semantic iOS backgrounds and grouped Form. Real system microphone/speech permission prompts and ReplayKit broadcast picker, no custom imitation or hidden picker-button invocation.
STORY: Enter a task and choose a language. Start help and allow listening. Use the visible system broadcast picker, choose Bob Screen Help, then Start Broadcast. Switch to another app and follow one spoken step. Stop through Bob or Control Center. On Simulator the primary action explicitly tries synthetic Settings guidance; it does not claim a live broadcast.
FIRST VIEWPORT: Native Bob large navigation title, 44pt round b, short introduction, task text field, native language picker and one primary action. Disclosure explains microphone activity, cloud OCR text and on-device images. Example guidance is explicitly labeled synthetic; no personal content appears in captures.
FORM: Native iOS controls adapt the incumbent identity. Existing logo and blue, voice-first trial purpose and system control structure are user/platform pinned; no open visual direction tournament.
FINISH: Actual Simulator captures in light/dark and enlarged Dynamic Type, fresh independent native review, and scoped system documentation. No web detector; no iPad target.

## Technical scope and limits

The ReplayKit extension passes one bounded, file-protected image per second through an App Group, on-device only. Vision OCR runs in the main app, avoiding heavy recognition in the memory-constrained extension. Screen images are never serialized into cloud requests. Known sensitive screens pause help; remaining numeric/contact strings are redacted, but detection is best effort. The main app handles a real consented microphone/speech session and ElevenLabs playback, with native voice fallback; no silent audio keepalive. Extension audio samples are ignored. Foreground Bob screens are excluded, both session IDs and host heartbeats gate capture, stale frames cannot advance, and sessions stop after at most ten minutes/twenty steps or on audio interruption/errors. No floating pointer, third-party accessibility tree, automatic scroll or app-launch actions. English interface for this trial; six guidance voice choices, with Apple recognition/OCR coverage dependent on installed system support.

Simulator proves build, native OCR/safety tests, preview layout and live Worker/ElevenLabs playback on synthetic input. It does not prove ReplayKit across real apps, background speech reliability, runtime App Group access, extension memory behavior or physical-device audio. An iPhone 16 Pro is connected and paired. A Personal Team, valid Apple Development certificates and matching App Group provisioning for both targets are configured; a signed Debug build succeeded and Bob is installed. After the developer profile was trusted, Bob launched successfully and both Bob and BobBroadcast processes were observed. The user reported no voice during the first live Settings trial. A diagnostic build is installed and launched while this is investigated; actual cross-app guidance/playback and background audio remain unverified. Do not describe this trial as production-ready or App Store approved.

## Native adaptation recorded from the build

`ContentView.swift` uses a real scrolling grouped SwiftUI Form inside NavigationStack, native task field/language picker and SF Symbols. SF semantic Dynamic Type supplies the hierarchy; system grouped backgrounds/rows resolve in light and dark. Android numeric tokens, overlay shapes and shared glyph rules remain scoped to Android in `DESIGN.md` and `.impeccable/design.json`. No new identity or visual world is introduced.

Placeholder, supporting instructions, section headings, status, error copy and privacy disclosure explicitly use semantic `UIColor.label` foreground. The blue prominent action has white play/microphone/text/loading foreground. Meaningful session status remains visible even when the next-step caption is empty, including stop/interruption recovery text. The iPhone entry mark is a 44pt circular version of the authored lowercase b. Native icon/mark assets in `ios/Bob/Assets.xcassets` are deterministic sRGB CoreGraphics renderings with embedded provenance; `ios/scripts/make-icons.swift` carries the authored geometry.

Evidence recorded on 2026-11-09: ten native tests (including actual Vision OCR on synthetic input), 88 Worker tests and TypeScript checks, Simulator Debug, unsigned iPhone Release and signed iPhone Debug builds. Simulator smoke received live Worker guidance and an ElevenLabs MP3 and completed playback from synthetic screen input. The Simulator smoke does not establish successful physical cross-app guidance/playback or production readiness. Debug-only trial diagnostics write event names, counts, status and error codes to Bob’s own app cache, never text, transcripts, tokens or images; they do not alter the visual system. Final screenshot recapture and the same native reviewer's verdict are tracked in `.impeccable/review/bob-ios/finish-review.md`; documentation does not stand in for that review.

Status 2026-11-09: shelved at the user's explicit request after a poor physical live trial. Android is the active focus. No end-to-end iPhone success is claimed.
