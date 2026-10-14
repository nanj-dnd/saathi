# Saathi

A Clicky-style phone helper for Indian seniors. A big orange **मदद / Help** button floats over every app. The senior taps it and says what they want ("Blinkit से दूध मंगाना है"). Saathi looks at the screen, says the single next step out loud in their language, and puts a spotlight and a pointing hand on the button to press. After the senior taps, Saathi looks again and gives the next step, until the task is done.

Saathi only points and talks; it never taps anything. OTP, PIN and payment screens are always left to the senior. Opening a remote-control app (AnyDesk, TeamViewer…) gets a spoken scam warning.

Languages: Hindi, Tamil, Telugu, Kannada, Malayalam. Android 11+ (API 30).

```
android/   Kotlin app: accessibility service, overlay, voice, guide loop, onboarding
worker/    Cloudflare Worker: one /turn call = speech-to-text → Claude → text-to-speech
```

## How a turn works

1. **Ears** ([Recorder.kt](android/app/src/main/java/app/saathi/voice/Recorder.kt)) record 16 kHz audio and stop after 1.6 s of silence. A mic foreground service is held only while listening.
2. **Eyes** ([ScreenReader.kt](android/app/src/main/java/app/saathi/capture/ScreenReader.kt)) read the foreground app's accessibility tree and take a screenshot. On Android 14+ only the app window is captured, and secure (PIN/OTP) windows refuse. On older versions a black frame is treated as secure. [NodeTree.kt](android/app/src/main/java/app/saathi/capture/NodeTree.kt) turns the tree into compact lines (`n12 Button "ADD" [l,t,r,b] click`). Typed text, passwords and numbers near "OTP/PIN" are never sent.
3. **Worker** `/turn` ([index.ts](worker/src/index.ts)) runs Sarvam STT (`saaras:v4`), then Claude with structured output, then Sarvam TTS (`bulbul:v3`, pace 0.85). Claude returns `{say, target: node id or x,y, status, danger}`. Node ids are checked against the tree, so a made-up target becomes "no pointer".
4. **Overlay** ([OverlayViews.kt](android/app/src/main/java/app/saathi/overlay/OverlayViews.kt)) dims the screen, glides a ring to the target, shows a hand, and puts a big caption with Repeat/Close on the opposite half of the screen.
5. **Guide loop** ([GuideSession.kt](android/app/src/main/java/app/saathi/session/GuideSession.kt)) watches for the senior's click, navigation, scroll or typing. It waits for the screen to settle, then looks again. `need_info` turns listen again automatically. A watch with no activity ends after 2 minutes.

The system prompt is in [prompt.ts](worker/src/prompt.ts), and per-app hints are in [assets/playbooks](android/app/src/main/assets/playbooks).

## Run it

**Worker**
```bash
cd worker && npm install
cp .dev.vars.example .dev.vars   # then fill in keys
npm run dev                      # http://localhost:8787
```
`ANTHROPIC_API_KEY`, `SARVAM_API_KEY` and `TOKEN_SECRET` go in `.dev.vars` locally, or `wrangler secret put` for deploys. Without an Anthropic key, the Worker returns a **mock** step that points at the first clickable element. Without a Sarvam key, it skips cloud speech and the phone uses its own TextToSpeech. So the whole phone flow runs with no keys at all.

The model is `GUIDE_MODEL` in [wrangler.toml](worker/wrangler.toml): `claude-opus-5-5` at `GUIDE_EFFORT=low` by default. `claude-haiku-5-5` is the cheaper, faster option to compare against.

**App**
```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew installDebug              # emulator → http://10.0.2.2:8787
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew installDebug -PworkerUrl=https://saathi-worker.<you>.workers.dev
```
On a real phone during development, `adb reverse tcp:8787 tcp:8787` and `-PworkerUrl=http://localhost:8787` reach a local Worker.

Enable the service without clicking through Settings:
```bash
adb shell settings put secure enabled_accessibility_services app.saathi/app.saathi.service.BuddyAccessibilityService
```

Debug builds also accept a typed question, which is handy on an emulator with no microphone:
```bash
adb shell "am broadcast -a app.saathi.DEBUG_ASK --es text 'how do I make the text bigger'"
```

## Tests and fixtures

```bash
cd worker && npm test && npm run typecheck
cd android && ./gradlew testDebugUnitTest
```

Debug builds save every turn request (minus audio) to `/sdcard/Android/data/app.saathi/files/fixtures/`. To build a regression set, pull interesting ones into `worker/fixtures/` and replay them against the Worker in any language:
```bash
cd worker && npm run fixtures -- --lang ta-IN [--audio]
```

## Before a public launch

- **Native-speaker review** of all strings ([res/values-*](android/app/src/main/res)) and of the spoken style in each language.
- **Real-device runs** of Blinkit, Uber, DigiLocker, UPI bill pay and WhatsApp video call, with real keys, to tune the prompt and playbooks. Also measure latency per stage (`timings_ms` is in every response and the Worker log).
- **Abuse protection on `/register`**: it is open today. Gate it with Play Integrity. Set the real KV namespace id in `wrangler.toml`.
- **Play Store accessibility declaration**: this is a non-"accessibility tool" app using AccessibilityService. The disclosure screen exists. A review video and privacy policy are still needed. Fallbacks are a direct APK download or the Indus App Store. Sideloaded installs on Android 13+ need "Allow restricted settings", and onboarding explains this.
- **Screenshot redaction**: the tree masks OTPs, but a screenshot of an SMS app can still show one. Consider skipping screenshots when the foreground app is a messaging app showing an OTP.
- **iOS**: voice-only version (ReplayKit broadcast plus spoken directions, no pointer).
