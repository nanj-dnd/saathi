# Bob for iPhone — shelved experiment

**Shelved by the user on 2026-11-09. Android Bob is the current focus. The first physical trial did not produce useful voice guidance; this prototype is not verified end-to-end.**

A native SwiftUI iPhone prototype, sharing Bob's blue lowercase **b**, Claude guidance backend and ElevenLabs Eric voice. The user chose **live screen sharing with voice first**.

This is a runnable technical trial, with a built ReplayKit extension. Actual cross-app operation and background audio remain **unverified on hardware**. An iPhone 16 Pro is connected and paired. The Apple Personal Team is configured, its development certificate and matching App Group profiles were created, and the signed Debug app is installed. iOS currently requires the user to trust the developer profile before launch. Live cross-app operation has not yet been verified.

## Try the Simulator

Open `Bob.xcodeproj`, select the **Bob** scheme and an iPhone Simulator, then Run. Tap **Try sample guidance** to send a synthetic Settings screen to the deployed Worker and play its ElevenLabs reply. The label explicitly identifies synthetic input; this does not start a broadcast or microphone session.

Command-line alternative, from the repository root:

```sh
xcodegen generate --spec ios/project.yml --project ios
xcodebuild -project ios/Bob.xcodeproj -scheme Bob \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath /tmp/bob-ios-build CODE_SIGNING_ALLOWED=NO build
xcrun simctl boot 'iPhone 17 Pro' # skip if already booted
xcrun simctl install booted /tmp/bob-ios-build/Build/Products/Debug-iphonesimulator/Bob.app
xcrun simctl launch booted app.saathi.bob.ios --smoke
```

The checked-in Xcode project can be opened without XcodeGen. Regenerate it after changing `project.yml` or adding source files. Use an explicit Simulator UDID instead of `booted` when multiple Simulators run. Xcode 27 calls its interactive simulator window Device Hub.

## Try live sharing on an iPhone

1. Connect and unlock an iPhone, enable Developer Mode, and select it in Xcode.
2. Select a development team for **both Bob and BobBroadcast**. Configure the same App Group for both targets. The default is `group.app.saathi.bob.ios`; if your team needs a unique identifier, update `BOB_APP_GROUP` in `project.yml` and the literal group identifier in both `.entitlements` files to the same value. The signing team must support and provision App Groups; no credentials belong in the repository. After installation, iOS may require you to trust your own Apple development profile in Settings → General → VPN & Device Management.
3. Build and Run the **Bob** scheme. Enter your task, choose your language, then tap **Start live help**. Allow microphone and speech recognition.
4. Tap the visible recording control under **Share your screen**, choose **Bob Screen Help**, then **Start Broadcast**. Broadcast microphone audio is not needed; Bob's main app handles listening. Switch to the app you need help with.
5. Follow the spoken step and make each tap yourself. Return to Bob and tap **Stop help**, or stop the screen broadcast in Control Center. If you stop while the broadcast is paused and its red indicator remains, stop it in Control Center too.

Required hardware checks: real screen frames after switching apps; background listening and ElevenLabs playback; recognition restarts; interruption by a phone call; stop/paused/locked-screen behavior; OCR languages; extension memory use; sensitive-screen handoff; and a ten-minute session. Current automated and Simulator results do not establish these.

## What is shared

The extension receives ReplayKit frames and ignores broadcast audio. It bounds a frame to 1280 pixels and writes only the latest frame to a file-protected App Group container. The main app recognizes text locally using Vision, withholds known payment/password/code screens, and redacts numbers and email addresses. Only recognized text and task context go to the Worker; **no screen image is uploaded**. Temporary frames are cleared on stop/start; any abandoned frame is cleared at the next session. Apple Speech may process microphone audio for recognition; Bob stops listening when the session ends.

Detection may miss private screens. Stop sharing before entering a secret. OCR-only guidance cannot understand all image-only controls. Bob cannot inspect other apps' control trees, draw an Android-style floating pointer, tap, scroll or open another app. The UI is English for this trial; six guidance voice choices are present, while recognition and OCR language coverage depend on Apple's services/device support.

There is no silent audio loop. Background audio is attached to the user's active listening/guidance session in the main app. The broadcast extension does not play audio. Sessions have a ten-minute/twenty-step ceiling, stale-frame checks and capture/session heartbeat checks. Failures stop rather than silently looping network calls.

## Backend and tests

`WorkerClient.swift` uses the existing `/register`, `/turn` and `/speak` endpoints. Requests explicitly set `platform=ios`; the Worker uses an iPhone-specific prompt and strips Android actions. Older Android clients default to `platform=android`, and shared answer keys include the platform. API keys stay in Worker secrets.

```sh
xcodebuild -project ios/Bob.xcodeproj -scheme Bob \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath /tmp/bob-ios-build CODE_SIGNING_ALLOWED=NO test
npm --prefix worker test
npm --prefix worker run typecheck
```

Test coverage includes genuine Vision OCR on rendered synthetic screens, sensitive-screen withholding, contact/numeric redaction, distinct/stable/fresh frame gating, session identity, heartbeats, atomic shared-state cleanup, screenshot exclusion from cloud payloads, iOS prompt/action behavior and Android compatibility. Release device binaries compile unsigned; an unsigned build cannot be installed on an iPhone.

Icon sources are deterministic sRGB renderings of `../branding/bob-mark.svg`, generated by `scripts/make-icons.swift`, with provenance embedded in each PNG. The worker URL is unchanged so the Android installation continues to work.

Apple references: [broadcast picker](https://developer.apple.com/documentation/replaykit/rpsystembroadcastpickerview), [sharing data with the containing app and extension restrictions](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionScenarios.html), [play-and-record audio](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/playandrecord), [App Groups](https://developer.apple.com/documentation/xcode/configuring-app-groups).
