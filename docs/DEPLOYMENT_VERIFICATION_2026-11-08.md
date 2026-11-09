# Deployment and verification — 2026-11-08

> Historical verification snapshot from 2026-11-08, before the Bob rename and ElevenLabs switch. See README.md for the current setup.

The Worker is deployed at https://saathi-worker.nanj-dnd.workers.dev.

- Active deployment (100%): `beeb4503-716a-481b-bdad-7a9038d5eb4e`.
- Previous deployment before this task: `27d343e7-ca32-4d12-8390-d7ff08179e6d`.
- Source: the local working tree, including its existing uncommitted changes. No Git commit or push was made.
- Phone APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- APK SHA-256: `7c52516a0b09591572f2e5be005095489c2ec2f296e26a92202456176ab82beb`.
- APK endpoint: `https://saathi-worker.nanj-dnd.workers.dev`.

## Automated and live API checks

- 135 Android unit tests passed, zero failures/errors.
- 72 Worker tests passed; TypeScript checking passed.
- APK and app bundle builds passed. Android lint: zero errors, 11 remaining warnings.
- Production `/ping`, `/recipes` (178 recipes), registration, authentication rejection and invalid-language rejection were checked.
- An unfamiliar-app request reached the real model and selected the supplied New note node.
- All six live language requests returned HTTP 200 and selected the correct node. This checks routing/targeting, not native-speaker translation quality.
- Unreadable requests return a local handoff without consuming the model budget.

## Emulator checks

A separate clean Android 15 / API 35 ARM64 emulator was used. Test apps were installed with synthetic notes, dummy private input and dummy OTP values. No personal phone data or real payments/messages were used.

| Scenario | Verified |
| --- | --- |
| Unfamiliar native app | Correct New note control targeted with no recipe. |
| Native input field | Dummy typed value absent from the emitted tree; no screenshot sent. |
| Split OTP | Separate numeric node masked; no screenshot sent. |
| Secure payment window | Local handoff rendered, no actionable pointer and no Worker request. |
| Native image-only canvas | Real screenshot used to point at the visible Play control. |
| Asynchronous content | Loading followed by Continue guidance after the content update. |
| Duplicate launcher names | No guessed node, coordinate or app launch; guidance continues after manual app choice. |
| Cross-app transition | Original goal and prior-app history reached the new app. This was a synthetic explicit destination, not a third-party share transaction. |
| WebView form | Document becomes readable after a short wait; dummy field value omitted and no screenshot sent. |

Live testing also found and fixed a concurrent TTS completion/stop crash. The final APK uses atomic one-shot completion and passed the concurrent callback regression tests and repeated navigation checks. Window screenshot throttling also exposed a secure-flag retry loop; the final capture path retries the same app window, never falls back to a whole-display image on Android 14+ after a window error, and hands control back when either look identifies a protected screen. The final secure-window check made no Worker request.

An initial UIAutomator dump interrupted the accessibility service in the test harness. Subsequent active-session taps used the observed pointer bounds instead. A cached cross-app replay was rerun with a fresh cache to verify the actual outgoing goal/history request.

## Privacy behavior

WebView/GeckoView screenshots are withheld even when their document has labels, because a field may paint before its accessibility node appears. Accessible web controls still use their labels. Image-only web canvases without accessible controls cannot be guided under this policy. Native image-only targeting remains available.

## Remaining external checks

- No physical Android phone was connected; USB device/manufacturer checks remain pending.
- Sarvam returned HTTP 402 with insufficient quota. The Worker correctly returns 501 so the phone can use its own TTS. Cloud voice needs credits replenished or a funded key. No billing changes were made.
- Audible voice quality was not verified: the emulator ran without audio output. These tests establish guidance and privacy behavior, not universal real-app or hardware compatibility.

Evidence and reproducible synthetic test sources: `/Users/anshulanand/Downloads/saathi-verification-2026-11-08`.
