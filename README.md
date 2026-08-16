# Deep Universe

An Android romance/visual-novel game in the vein of *Love and Deepspace*, with a mixed cast of male
and female love interests — and a character creator that can build your character from a photo of
your face, entirely on-device, while leaving every parameter yours to change.

Aurora-9 is a station on the edge of the Drift. Something out there has been repeating a nine-second
pattern for eleven days, and you are the only person aboard who can hear it.

---

## What's in this build

| Area | State |
| --- | --- |
| Photo → character generation | Complete and unit-tested |
| Manual character editor (30 parameters, colours, hair, presets) | Complete |
| Live parametric portrait renderer | Complete |
| Story engine, affection, gated scenes, saves | Complete and unit-tested |
| Cast of six love interests | Complete, with 9 shipped scenes |
| Character art, voice, music, 3D | Not started — see [Roadmap](#roadmap) |

62 unit tests cover the character pipeline and the game logic.

---

## The photo feature

The player has three ways to make a character, all of which land in the same editor:

1. Pick a **preset** look.
2. **Take or choose a photo** — the app reads their face and sets the sliders.
3. Start from the **default** and drag everything by hand.

The photo path is one option among three, never a funnel. Whatever it produces, the player lands on
the same editor with all ~30 sliders live, plus a *strength* slider that blends continuously between
the look they had before the photo and the generated one.

### How it works

```
photo  →  ML Kit face mesh  →  named landmarks  →  invariant measurements  →  slider values
          (on-device)          MeshLandmarkMapper    FaceGeometry             PhotoToAppearance
                     ↘
                       pixel patches → median colours → nearest palette swatch
                       BitmapColorSampler                Palettes
```

**Nothing leaves the device.** The face mesh model is bundled in the APK, the measurement and colour
sampling happen in-process, and the bitmap is released the moment the analysis returns. No network
call is made at any point, no photo is written to the gallery, and the camera's temporary file is
deleted as soon as the analysis finishes. The only thing that survives is a set of numbers between
0 and 1.

That is also why this is a geometric mapping rather than a generative model. A diffusion model would
be slower, would need a server and an API key, would be far harder to make safe for a consumer game
handling face photos, and — decisively — could not hand the result back as editable sliders.

### The properties that make it usable

These are enforced by tests, not just intended:

- **Scale invariant** — standing closer to the camera does not change the character.
- **Position invariant** — where the face sits in the frame does not matter.
- **Roll corrected** — a tilted photo is straightened before anything is measured.
- **Mirror invariant** — a mirrored front-camera selfie produces the same character as an
  unmirrored one. Left and right are decided from the image, not from mesh index labels.
- **Deterministic** — the same photo always produces the same character.
- **Centred calibration** — an average face lands mid-range on *every* slider, so the traits that
  actually distinguish a face are the ones that show up.
- **Clamped** — a misdetected landmark pins a slider at an extreme rather than producing a nose
  three heads wide.
- **Non-destructive** — parameters a camera cannot see (build, height, freckles, hairstyle) are
  never touched, so re-running the analysis cannot silently undo manual edits.

### What it deliberately does not do

- **Hairstyle** is not inferred. It is unreliable from a single photo, and getting it wrong is very
  visible. The player picks it.
- **Stylised hair colours** (mint, nebula violet) can never come out of a photo — only the eight
  natural swatches can. Green-tinted lighting cannot hand someone green hair.
- **Age, ethnicity and gender are never inferred.** The pipeline measures geometry and colour; it
  does not classify people. Pronouns and presentation are chosen by the player, independently of
  each other and of anything in the photo.

### When it fails

Every failure is a normal thing for a player to do, so each returns a written message rather than an
error: no face found, face partly out of frame, face too small. Low-confidence results still succeed
but come with specific guidance ("your head was turned a little"), and confidence is used to inform
the player, never to reject their photo.

---

## Architecture

```
core/                     Pure Kotlin/JVM — no Android dependency, fully unit-tested
  character/              CharacterAppearance, AppearanceParam, palettes, presets
  color/                  ARGB maths and perceptual colour distance
  photo/                  MeshLandmarkMapper, FaceGeometry, PhotoToAppearance
  game/                   StoryEngine, GameState, Cast, StoryLibrary

app/                      Android + Jetpack Compose
  photo/                  ML Kit adapter, bitmap colour sampling, EXIF handling
  ui/avatar/              Parametric portrait renderer (Compose Canvas)
  ui/creator/             Character creator, photo pickers
  ui/home, ui/route, ui/story
  data/                   Atomic JSON save file
```

The split is deliberate: everything that involves a decision lives in `core`, where it runs on the
JVM without a device. The Android module is adapters and drawing. `FaceMeshMapper` in `app` is nine
lines — all the judgement about which mesh index means what sits in `core`, under test.

**One parameter model.** The AI path and the manual editor write into the same
`Map<AppearanceParam, Float>`. Because `AppearanceParam` is an enum, the editor UI is generated from
it: adding a parameter adds a slider with no UI change.

**The portrait is drawn, not assembled from art.** A conventional creator swaps pre-drawn assets,
which means every slider needs an artist. Drawing from the parameters means all 30 sliders are
continuous and visible now, which is exactly what the photo generator needs — it sets arbitrary
values across every axis at once. When real art arrives, this becomes the layer behind it and the
parameter model does not change.

---

## The cast

Six love interests, all romanceable regardless of the player's pronouns or presentation.

| | Role | |
| --- | --- | --- |
| **Lyra Vance** (she/her) | Interceptor Pilot | Flies like she has nothing to lose |
| **Dr. Nadia Okonkwo** (she/her) | Xenobiologist | Reads people the way she reads samples |
| **Rook** (she/her) | Salvage Runner | No surname on file, no apologies either |
| **Kaito Mori** (he/him) | Navigator | Charts the Drift by feel, panics at small talk |
| **Sev Aldair** (he/him) | Station Commander | Duty first, everything else in a locked drawer |
| **Idris Calloway** (he/him) | Chief Engineer | Fixes everything, breaks it first for science |

Scenes unlock on bond tier rather than in a fixed order, so spending your choices on one person
advances that route while the others wait at their introduction — routes feel chosen, not queued.
Dialogue is pronoun-aware: story text is written once with `{name}` / `{they}` / `{their}` tokens and
reads correctly for every player.

---

## Building

### On GitHub — no local setup at all

A workflow builds the APK on GitHub's runners, so you can get an installable file without
installing Android Studio or the SDK.

1. Push to any branch, or go to the repo's **Actions** tab → **Build** → **Run workflow**.
2. Wait for the run to finish (a few minutes).
3. Open the finished run and download **`deep-universe-debug-apk`** from the *Artifacts* section
   at the bottom of the page.
4. Unzip it and copy `app-debug.apk` to an Android phone. You will need to allow
   "install from unknown sources" the first time.

If a run fails, the summary page lists the compile errors directly, and the full logs are attached
as the **`build-logs`** artifact.

Note that the debug APK is unsigned for distribution — it installs fine by hand, but it is not
suitable for the Play Store. That needs a release build with your own signing key.

### Locally

Open in **Android Studio** (Ladybug or newer) and run. Requires JDK 17+ and the Android SDK
(compileSdk 35, minSdk 24).

From the command line:

```bash
./gradlew :app:assembleDebug     # build the APK
./gradlew :core:test             # run the unit tests
```

### What has been verified, and how

| Layer | How it's checked |
| --- | --- |
| `core/` — character, photo and game logic | 62 unit tests, run on the JVM |
| `app/photo/`, `app/data/`, `GameViewModel`, `CastLooks` | Type-checked against the real `core` jar plus hand-written stubs of the small Android/ML Kit surface they use |
| `app/ui/` Compose screens | Parsed clean; Compose/Material3 API usage checked against the published API docs |

The Compose screens have not been through a full Android build — that needs the Android SDK and
Google's Maven repo. Build once in Android Studio before trusting the UI layer.

### Building `core` without the Android SDK

The game logic is a plain Kotlin module and can be built and tested on any machine — a CI runner, a
container with no Android SDK, no access to Google's Maven repo:

```bash
gradle -Pdeepuniverse.jvmOnly=true :core:test
```

That flag drops `:app` from the build entirely. This is how the 62 tests in this repository are run.

---

## Roadmap

Nearest first:

1. **Character art.** Replace the drawn portrait with layered illustration driven by the same
   parameters, keeping the renderer as the fallback layer.
2. **More story.** The engine is content-driven — `StoryLibrary` is a list of data. Adding scenes
   requires no engine or UI change.
3. **Daily loop.** Messages, calls, gifts and a reason to open the app on a Tuesday.
4. **Save slots and cloud sync.** The save format already round-trips and tolerates unknown fields
   from future versions.
5. **Localisation.** Player-facing strings currently live in the composables and would move to
   resources; the pronoun templating already generalises past English.
6. **Photo pipeline v2.** Depth-aware nose bridge from a second angle, optional hairstyle
   classification with an explicit opt-in, and a "does this look like you?" comparison step.

---

## Privacy

Written down because a game that photographs faces should say it plainly:

- Face photos are analysed on-device and never uploaded.
- No photo is stored by the game. The camera's temporary capture is deleted after analysis.
- No face template, embedding or measurement is persisted — only the resulting slider values, which
  are indistinguishable from a character made by hand.
- The camera permission is optional. Declining it leaves the photo picker and the whole manual
  editor working.
- The app makes no network calls.
