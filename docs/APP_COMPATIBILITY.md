# App compatibility

Saathi can attempt guidance in any installed Android app with a readable accessibility tree or screenshot. There is no app allowlist for the model. The recipe list provides fast offline shortcuts for familiar tasks; it does not define which apps may receive help.

## How unfamiliar apps work

- The phone discovers launcher apps using its existing package-visibility declaration, without `QUERY_ALL_PACKAGES`. It refreshes names on new requests and periodically during a task.
- App-name matching happens locally. Requests include the foreground display name and at most 12 relevant installed app matches; unrelated installed apps stay on the phone.
- Every app receives a general playbook. Existing app-specific and browser hints supplement it.
- A request naming another installed app cannot be answered by an unrelated recipe. Generic requests inside unfamiliar apps stay in the foreground app. When a scripted task enters an unscripted share destination, the model continues with the original goal and history.
- The model uses verified package names for opening apps. A wrong package may be repaired only from a unique installed label. Ambiguous launcher names hand app selection back to the senior without a guessed pointer or launch; an unknown app never triggers an automatic installation-page fallback in the catalogue-enabled phone path.
- Content-change events support asynchronous lists, web pages and custom interfaces. Identical screens do not trigger another model call, content changes wait for narration, and typing retains its longer pause.
- Accessible controls use node targets. Image-only native controls require a screenshot. WebView/GeckoView screenshots are always withheld because web fields can render before their accessibility nodes; Saathi briefly waits for the document and then uses its labels. A suppressed screenshot cannot produce guessed coordinates. Unreadable screens stop with a message in the selected language.

## Limits

New tasks without a recipe or a valid cached answer require a configured model and internet access. Android secure windows and inaccessible content cannot be bypassed. Password, PIN, OTP and payment steps stay with the senior. An image-only web canvas without accessible controls cannot be guided while its screenshot is withheld. Apps without a launchable user interface cannot be opened through a launcher intent. Work-profile, device-policy and overlay restrictions may also limit access.

Automated tests use synthetic app identities and mocked model responses. They verify routing, protocol, targeting and state handling; they do not establish the model's accuracy in every real app.

## Build and deploy

Deploy the matching Worker source so it understands app-name context and applies the new grounding rules:

```sh
cd worker
npm test
npm run typecheck
npm run deploy
```

Build a phone APK with your Worker URL; the default URL is for an Android emulator:

```sh
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleDebug -PworkerUrl=https://saathi-worker.nanj-dnd.workers.dev
```

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`. Existing Worker secrets and account configuration are required; building the APK does not deploy the Worker.

## Device checks before claiming compatibility

| Case | Check |
| --- | --- |
| App without a recipe | Open an unfamiliar app, ask for a task, and verify the pointer refers to its actual controls. |
| App selection | Name an installed app from Home; confirm its real package opens. Repeat with duplicate display names and verify Saathi asks. |
| Another mail or messaging app | Request mail or a message inside that app; confirm Saathi stays there instead of opening Gmail or WhatsApp. |
| Share flow | Start in Photos, select an unscripted destination, and verify the original goal survives. |
| Asynchronous UI | Let a result list load without another tap; verify guidance updates once the visible content changes. |
| Typing and narration | Type into a form and let background content update; verify typing pauses and spoken instructions are not cut off. |
| Custom or web canvas | Test a visible control without accessible labels; confirm image-based targeting works, then verify no coordinates are invented when the image is unavailable. |
| Privacy and secure screens | Confirm visible inputs omit screenshots, split codes are masked, and protected controls have no pointer. |
| Offline | Confirm known recipes remain available and an unfamiliar uncached task receives the network explanation. |
| Languages | Repeat the basic unfamiliar-app flow in each of the six languages. |

Latest deployment and runtime results: [2026-11-08 verification](DEPLOYMENT_VERIFICATION_2026-11-08.md).
