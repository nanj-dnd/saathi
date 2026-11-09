# Bob iOS live-help trial — 2026-11-09

The user requested an iOS trial and selected live screen sharing with voice first. The native iPhone app and ReplayKit upload extension are implemented in `ios/`; the existing Android installation remains separate.

## Verified

- Simulator Debug build and ten native tests pass. Tests include actual Vision OCR over rendered synthetic Settings/PIN screens, sensitive-text withholding, contact/numeric redaction, fresh/distinct/stable screen gating, session identity and host-heartbeat guards, atomic shared-state cleanup and exclusion of images from Worker requests.
- 88 Worker tests and TypeScript checking pass. iOS requests explicitly select an iPhone prompt and have Android actions stripped. Older Android clients default to Android; shared answer keys include platform.
- Worker deployment `1823ad88-4ddc-4703-8eae-024ca67d9e4d` serves the iOS path at the existing Worker URL.
- A real Simulator run received a live model step, fetched an ElevenLabs MP3, decoded it and completed playback for synthetic Settings input. This is not a live cross-app test.
- Native layout captures cover light/dark, accessibility-medium Dynamic Type and interruption status without a caption. The reviewed contrast and filled-button foreground fixes are in the rebuilt UI.
- An Apple development certificate and matching profiles with `group.app.saathi.bob.ios` were created after the user signed in. The signed Debug build succeeded and Bob was installed on the connected iPhone 16 Pro (iOS27.0.1). The user trusted their developer profile; the app launched successfully. Both Bob and BobBroadcast processes were observed.

## Current physical-test result

**The first live Settings trial produced no voice, as reported by the user. Cross-app guidance is not verified.** An updated diagnostic build is installed and launched, and the user has been asked to restart the Settings test. The local diagnostic cache is readable and currently confirms app scene entry; stage evidence will be added after the retry. Diagnostic records contain only event names, counts, state, timings and error domain/codes, never screen text, goals, microphone transcripts, tokens or images.

Apple Device Hub refuses physical screen viewing while a microphone/camera is active. This prevents remote visual inspection during the actual listening session; it does not itself establish why guidance failed. No hardware playback success or production-readiness claim is supported.

## Architecture and limits

The ReplayKit extension keeps at most one bounded 1280-pixel frame per second in a file-protected on-device App Group. The host app performs Vision OCR, suppresses known sensitive screens and redacts numeric/contact strings. Only OCR text and task context go to the model; screen images stay on the phone. These heuristics can miss private content, so users must stop sharing before entering secrets.

The host runs a user-consented microphone/Speech session and plays ElevenLabs Eric, with native speech fallback. Recognition is suspended during Bob's output to avoid recognizing its own replies. There is no silent audio keepalive. The extension ignores broadcast app/microphone audio. Capture checks session identity, consent/foreground state, heartbeat, expiry and fresh stable frames. Sessions end after at most ten minutes/twenty steps or on failure/interruption.

The trial is English UI with six guidance language choices; Apple recognition/OCR availability varies. There is no floating cross-app pointer, external app accessibility tree, automatic tap/scroll or app opening. Background listening/playback, memory use, interruptions, lock/stop behavior and live language coverage still require physical verification.

## Run and evidence

See [ios/README.md](../ios/README.md) for Simulator and physical setup steps. Xcode project: `ios/Bob.xcodeproj`; project generator: `ios/project.yml`. The checked-in development team identifier is public signing configuration, not a private key. API credentials remain only in Worker secrets.

Private evidence directory: `/Users/anshulanand/Downloads/bob-ios-trial-2026-11-09`. No account credentials, provisioning certificate data, personal screen content or registration tokens are copied into this report or evidence package. Review verdict is scoped to its listed UI/documentation fixes, not runtime correctness.
