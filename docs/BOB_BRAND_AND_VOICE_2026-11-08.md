# Bob brand and ElevenLabs verification — 2026-11-08

> Historical verification snapshot from 2026-11-08. Deployment IDs and test counts below describe that run.

User request: use ElevenLabs, rename the app Bob, and replace the logo with a lowercase b. Confirmed choices: a calm male voice and the existing blue.

## Delivered

- User-visible name **Bob** in setup, accessibility settings, notifications, disclosure and spoken samples in all six languages. Application ID `app.saathi` and Worker URL remain compatible with existing installs.
- Authored lowercase **b** vector, white on `#1F5FE0`, used in setup and the adaptive launcher; a monochrome layer is included for themed icons. Production sources and imagegen concept provenance: `branding/`.
- ElevenLabs Eric (`cjVigY5qzO86Huf0OWal`) with `eleven_v3_conversational`, explicitly tested in all six app languages. Binary MP3 requests use the [official speech endpoint](https://elevenlabs.io/docs/api-reference/text-to-speech/convert). Speech recognition stays on Android.
- `ELEVENLABS_API_KEY` is a deployed Worker secret. A scan of every uncompressed APK entry confirmed that the key is absent from the APK. Daily per-device and global synthesis limits and Android fallback remain in place. No full paid prewarm was run.
- Server speech keys include provider/settings version, model, voice, output format, language, pace and text. The Android speech directory is now `speech-elevenlabs-eric-v1`; old Sarvam clips are not reused. Both guide-cache versions were bumped so older feminine first-person Hindi replies are not reused. Hindi first-person wording now agrees with the male voice.

## Verification

- Android: **135 unit tests passed**; `assembleDebug`, `bundleDebug`, `lintDebug` passed, with 0 lint errors and 11 existing warnings.
- Worker: **84 tests passed**, plus TypeScript checking. Coverage includes upstream request fields for all six languages, voice/model cache isolation, invalid audio, credentials/credits, rate limits and network fallback.
- Production Worker: https://saathi-worker.nanj-dnd.workers.dev
- Deployment version: `bcfc50c7-c632-405c-b0c4-9fa3ed08f341` (same code as the preceding `2e46b0d9-b429-48cd-bea6-17faff8ceb6c`, which served the language checks below).
- A post-deployment English smoke request returned HTTP 200, cached MP3 (19,975 bytes). All six live responses decoded successfully with ffprobe; these are synthetic setup voice samples, not personal screen content.

| Language | HTTP | Source | MP3 bytes | Fetch + decode |
|---|---:|---|---:|---:|
| en-IN | 200 | elevenlabs | 19975 | 1.71 s |
| hi-IN | 200 | elevenlabs | 18721 | 2.49 s |
| ta-IN | 200 | elevenlabs | 20288 | 2.7 s |
| te-IN | 200 | elevenlabs | 20915 | 2.14 s |
| kn-IN | 200 | elevenlabs | 23214 | 3.57 s |
| ml-IN | 200 | elevenlabs | 23214 | 2.27 s |

The repeat English request returned `source=cache` in 0.59 s with identical audio. A slower-speed request (pace 0.85) returned a separate valid ElevenLabs clip. This verifies API acceptance and cache separation; it is not a listening assessment of pronunciation or pace quality.

Real Android 35 ARM64 emulator captures: `phone-light.png` (new b + Bob in setup), `phone-dark-large.png` (renamed copy in dark mode at font scale 1.3), `installed-icon.png` (adaptive icon and Bob label in OS App info). The emulator's Pixel Launcher briefly became unresponsive during capture; the invalid screenshot was replaced and is not review evidence. No physical device was connected and no human listening study was performed.

Evidence directory: `/Users/anshulanand/Downloads/bob-verification-2026-11-08` (private directory; no API key or registration token). Independent review found no scoped visual or code fixes in the supplied setup and installed-icon evidence. Its sole material fix was persistence of the new brand in system documentation. That fix is resolved in PRODUCT.md, DESIGN.md, the token sidecar and surface contract. Final disposition: **ship**, scoped to the scored documentation fix; this does not claim a fresh audit of the entire app or a themed-launcher capture. The full review and verdict are archived as `finish-review.md` and `verdict.md`.

## APK

`android/app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `f3d422b6960916bb0f8b20d72ad5092d14cf492bd2a90a13c849533a728df024`

This is a debug APK configured for the deployed Worker. Existing install data is retained with `adb install -r`. The older deployment report remains a historical record of the pre-Bob implementation.
