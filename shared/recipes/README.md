# Recipes

Scripted guides for common tasks. Each file is one task: what a senior might say, and the step to give
on each screen of the app. The phone runs them itself, so a recipe step costs nothing, works offline
and sends nothing anywhere. When no step fits, the turn goes to Claude as before.

The Android build copies this folder into the app's assets, and the Worker serves it at
`GET /recipes`. Phones fetch it at most once a day, so a recipe can be fixed after an app redesign
without shipping a new APK.

## How a recipe is chosen

1. **What was said** is matched against `triggers`. A trigger is a list of groups, and each group
   needs one of its phrases somewhere in the sentence. `{"all": [["whatsapp", "व्हाट्सएप"], ["video", "वीडियो"]]}`
   needs both a WhatsApp word and a video word. Phrases from all six languages sit side by side.
2. Phrases must **start a word**, so `गाना` (song) does not fire inside `लगाना` (to set). A phrase ending
   in a virama, a Malayalam chillu letter or an anusvara also matches its inflected forms (`ബ്ലിങ്കിറ്റ്` matches
   `ബ്ലിങ്കിറ്റിൽ`). For other endings, add the stem yourself (`மருந்து சீட்ட` for `சீட்டு`/`சீட்டை`).
3. `unless` words and the common list in `_common.json` (negations, "why", "problem") send the request to
   Claude instead. They match anywhere in a word, which catches Tamil `செய்யவில்லை` and Malayalam `വരുന്നില്ല`.
   Safety warnings set `skip_common_unless` so "should I *not* tell the OTP?" still gets the warning.
4. The recipe with the most matched groups wins. A tie goes to the one whose app is already open
   (`in_app` triggers), and otherwise to Claude. Saathi never guesses between two recipes.
5. On later turns nothing is said; the recipe is found again by its English `goal`.

## Word families and failsafes

`_common.json` holds `concepts`: families of words for one idea in every language and common
Hinglish spellings ("order": order, buy, purchase, mangana, मंगा, ख़रीद, வாங்க…). A trigger group that
lists one member of a synonym family accepts the whole family, so a recipe never has to list every
way of saying "buy". A group widens only when one of its phrases *is* a family word (or that word with
a short inflected ending), never because a longer phrase happens to contain one.

Two failsafes stop the right words reaching the wrong script:

- **Exclusive families** (`"exclusive": true`: download, uninstall, update, cancel, block, unblock)
  change what the task is. A request that says one of them never picks a recipe whose triggers don't.
  "Download Blinkit" can't start Blinkit's ordering recipe.
- **Opposites** (`opposites`: on/off, bigger/smaller, download/uninstall, block/unblock): a request for
  one side never picks a recipe that covers only the other. "Turn on dark mode" can't run the
  turn-it-off recipe.

Safety warnings that speak in any app are exempt: they always speak.

Speech recognisers split and misspell names, so for Latin-script phrases, whole words joined without
spaces count ("blink it" is "blinkit"), and a word of six or more letters may be one letter off (two
from nine letters). Indian scripts are matched exactly, as recognisers spell them consistently.

When two recipes tie, the one that matched in its own words beats one that matched only through a
family. A tie that remains still goes to Claude.

## How a step is chosen

Steps are checked **in order and the first match wins**, so list the furthest-along steps first.
A step matches when all of these hold:

| Field | Meaning |
| --- | --- |
| `apps` | Packages this step runs in when it is not the recipe's own app (the share sheet, WhatsApp after sharing from the gallery). |
| `screen_has` | Groups of text; each group needs one phrase on screen. |
| `screen_lacks` | None of these may be on screen. |
| `target` | Label alternatives of the clickable element to point at. Exact match beats "starts with" beats "contains". The step doesn't match if none is found. |
| `exact` | Whole-label matching only, for short words that also appear inside chat messages ("Send", "Chats", "Calling"). |
| `switch` + `switch_is` | An on/off row and the state it must be in. `tap_switch` points at the switch itself. |
| `after` | How many times an earlier step's line must already have been said (`{"step": "attach", "min": 1}`). |
| `max_repeats` | Hand over to Claude once this exact line was said this many times in a row (default 3): the senior is stuck or the app looks different. |
| `read_screen` | `{screen}` in the line becomes the visible text, read aloud. |

`say` has one line per language. `{label}` becomes the matched element's text. Lines are spoken at a
slow rate to people over 65: one instruction, under about 15 words, everyday words, describe colour and
position ("नीचे हरा 'Add' बटन दबाइए"), never "icon" or "toolbar".

`status` is `next_step` (default), `done`, or `user_must_do`; `danger` is `none`, `sensitive_screen` or
`scam_suspected`. Recipes whose `apps` is `["*"]` are spoken tips that work in any app and have one step.

When the recipe's app is not open, Saathi points at its icon on the home screen (`app_names`), or says
how to get there, using the shared lines in `_common.json`.

## Safety rules the engine enforces

- Never point at payment, PIN, OTP, CVV or password controls. A target whose text matches one of
  these ("Pay", "Place order", "भुगतान", …) is refused at load time and again at run time.
- Steps with a `danger` never point at anything.
- Screens the phone could not capture (PIN, payment) get a fixed "do this yourself" line.
- A screen with signs of a scam (AnyDesk, KYC, lottery, digital arrest, collect request) skips recipes
  and caches entirely, so Claude, which knows how to warn, always sees it.
- A recipe that fails validation is dropped, so a bad download cannot break guidance.

## Adding or fixing a recipe

1. Copy a similar file. Give it a unique `id` and `goal`, and bump `version` when editing a shipped one.
2. Write `examples` in all six languages: phrases a senior would really say. They are not used at
   run time; the tests check that each one picks this recipe and no other.
3. Run the tests:
   ```bash
   cd android && ./gradlew testDebugUnitTest   # RecipeEngineTest: validity, examples, collisions
   cd worker && npm run recipes && npm test    # rebuilds src/recipe-bundle.json and checks it
   ```
4. Check the labels on a real phone. Button text differs between Pixel, Samsung, Xiaomi and app
   versions; list the alternatives you see. A wrong label is not dangerous (the step just doesn't
   match and Claude answers), but a right one saves a model call every time.

Lines in Tamil, Telugu, Kannada and Malayalam need a native speaker's review before launch.
