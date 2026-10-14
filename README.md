# Saathi

A Clicky-style phone helper for Indian seniors. A big orange **मदद / Help** button floats over every app. The senior taps it and says what they want ("Blinkit से दूध मंगाना है"). Saathi looks at the screen, says the single next step out loud in their language, and puts a spotlight and a pointing hand on the button to press. After the senior taps, Saathi looks again and gives the next step, until the task is done.

Saathi only points and talks; it never taps anything. OTP, PIN and payment screens are always left to the senior. Opening a remote-control app (AnyDesk, TeamViewer…) gets a spoken scam warning.

Most steps never reach Claude. The phone answers common tasks itself with scripted [recipes](shared/recipes) (116 today: WhatsApp calls and photos, Settings switches, UPI balance and bills, Blinkit and other shopping, YouTube, Maps, DigiLocker, spoken tips, scam warnings, reading the screen aloud), and it remembers answers it already got for the same screen. Those steps are free, instant and work offline.

Languages: Hindi, Tamil, Telugu, Kannada, Malayalam. Android 11+ (API 30).

```
android/          Kotlin app: accessibility service, overlay, voice, guide loop, onboarding
shared/recipes/   Scripted guides, shipped in the app and served by the Worker
worker/           Cloudflare Worker: one /turn call = screen + what was said → Claude → next step
```

## How a turn works

1. **Ears** ([SpeechListener.kt](android/app/src/main/java/app/saathi/voice/SpeechListener.kt)) use the phone's own speech recognition (Google's on most phones, free, all five languages) to turn speech into text. A mic foreground service is held only while listening.
2. **Eyes** ([ScreenReader.kt](android/app/src/main/java/app/saathi/capture/ScreenReader.kt)) read the foreground app's accessibility tree and take a screenshot, except when the screen mentions an OTP, PIN or password: then only the masked tree is sent. On Android 14+ only the app window is captured, and secure (PIN/OTP) windows refuse. On older versions a black frame is treated as secure. [NodeTree.kt](android/app/src/main/java/app/saathi/capture/NodeTree.kt) turns the tree into compact lines (`n12 Button "ADD" [l,t,r,b] click`). Typed text, passwords and numbers near "OTP/PIN" are never sent.
3. **On the phone first** ([LocalFirstBrain.kt](android/app/src/main/java/app/saathi/brain/LocalFirstBrain.kt)): a screen the phone couldn't capture gets a fixed "do this part yourself" line; then a [recipe](shared/recipes/README.md) answers if one fits what was said and what is on screen; then the phone's own cache ([TurnCache.kt](android/app/src/main/java/app/saathi/brain/TurnCache.kt)) answers if it has seen this screen, goal and progress before. A screen with scam signs skips the last two so the model always sees it. Offline, an expired cached answer is still used.
4. **Worker** `/turn` ([index.ts](worker/src/index.ts)) makes one Claude call with structured output. Claude returns `{say, target: node id or x,y, status, danger}`. Node ids are checked against the tree, so a made-up target becomes "no pointer". Before calling Claude it checks a cache shared between phones ([cache.ts](worker/src/cache.ts)), only for screens of non-personal apps (Settings, shopping catalogues), only when the senior said nothing, and only for an exact match of the screen's text. Every response says who answered (`source`).
5. **Mouth** ([Speaker.kt](android/app/src/main/java/app/saathi/voice/Speaker.kt)) speaks the step with the phone's TextToSpeech at 0.85× speed. **Overlay** ([OverlayViews.kt](android/app/src/main/java/app/saathi/overlay/OverlayViews.kt)) dims the screen, glides a ring to the target, shows a hand, and puts a big caption with Repeat/Close on the opposite half of the screen.
6. **Guide loop** ([GuideSession.kt](android/app/src/main/java/app/saathi/session/GuideSession.kt)) watches for the senior's click, navigation, scroll or typing. It waits for the screen to settle, then looks again. `need_info` turns listen again automatically. A watch with no activity ends after 2 minutes. A scroll or a flickering dialog that leaves the screen's text unchanged keeps the current step without asking again. A slow answer gets a spoken "one moment"; each kind of failure (no internet, busy mic, no speech service, Worker busy, daily limit, a phone call in progress) has its own explanation.

The system prompt is in [prompt.ts](worker/src/prompt.ts), and per-app hints are in [assets/playbooks](android/app/src/main/assets/playbooks).

## Run it

**Worker**
```bash
cd worker && npm install
cp .dev.vars.example .dev.vars   # then fill in keys
npm run dev                      # http://localhost:8787
```
`ANTHROPIC_API_KEY` and `TOKEN_SECRET` go in `.dev.vars` locally, or `wrangler secret put` for deploys. `npm run dev` and `npm run deploy` first rebuild `src/recipe-bundle.json` from `shared/recipes` (`npm run recipes`). The shared answer cache is the optional `GUIDE_CACHE` KV namespace in [wrangler.toml](worker/wrangler.toml). Without an Anthropic key, the Worker returns a **mock** step that points at the first clickable element, so the whole phone flow runs with no keys at all.

The model is `GUIDE_MODEL` in [wrangler.toml](worker/wrangler.toml): `claude-haiku-5-5` at `GUIDE_EFFORT=low` by default, roughly ₹0.05 per step. If it misplaces the pointer on real apps, try `claude-sonnet-5-5` or `claude-opus-5-5`, which cost up to ~40× more.

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
The Android tests run every recipe's example phrases in all five languages and check each reaches its own recipe and no other, that a list of requests meant for Claude (bills, payments, complaints) reaches it, and that no recipe points at a payment or PIN control.

Debug builds save every turn request, including what the senior said, to `/sdcard/Android/data/app.saathi/files/fixtures/`. To build a regression set, pull interesting ones into `worker/fixtures/` and replay them against the Worker in any language:
```bash
cd worker && npm run fixtures -- --lang ta-IN [--say "पढ़ने के लिए अक्षर बड़े करने हैं"]
```

## Before a public launch

- **Native-speaker review** of all strings ([res/values-*](android/app/src/main/res)), the recipe lines in [shared/recipes](shared/recipes), and the spoken style in each language.
- **Recipe labels on real phones**: button text differs between Pixel, Samsung, Xiaomi and app versions. A wrong label only means Claude answers instead, but each right one saves a model call. Watch `source` in debug logs (`adb logcat -s Saathi`) to see which turns still reach the model.
- **Speech quality on cheap phones**: Google's recognizer and voices are free but vary by phone. Setup now plays a test line, offers to install a missing voice, and on Android 13+ downloads offline speech recognition. If quality is poor, Sarvam's paid speech-to-text and text-to-speech can be added back in the Worker.
- **Real-device runs** of Blinkit, Uber, DigiLocker, UPI bill pay and WhatsApp video call, with real keys, to tune the prompt and playbooks. Also measure latency per stage (`timings_ms` is in every response and the Worker log).
- **Abuse protection on `/register`**: it is open today. Gate it with Play Integrity. Set the real KV namespace ids (`USAGE`, `GUIDE_CACHE`) in `wrangler.toml`.
- **Play Store accessibility declaration**: this is a non-"accessibility tool" app using AccessibilityService. The disclosure screen exists. A review video and privacy policy are still needed. Fallbacks are a direct APK download or the Indus App Store. Sideloaded installs on Android 13+ need "Allow restricted settings", and onboarding explains this.
- **iOS**: voice-only version (ReplayKit broadcast plus spoken directions, no pointer).
