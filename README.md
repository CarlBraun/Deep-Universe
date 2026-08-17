# Deep Universe

An Android romance game with a mixed cast of male and female love interests, built around two ideas:
a **walk-around world** you explore Pokémon-style to find people, and a **character creator that can
build your character from a photo of your face** — entirely on-device, with every parameter still
yours to change.

You are the resonance pilot of Aurora-9, down at the station's planetside camp on shore rotation.
Something out in the Drift has been repeating a nine-second pattern for eleven days, and you are the
only person here who can hear it.

---

## What's in this build

| Area | State |
| --- | --- |
| Walk-around overworld, 7 locations | Complete and unit-tested |
| Collectable expressions (11 faces) | Complete and unit-tested |
| Endless bond ranks and the moments loop | Complete and unit-tested |
| Store, wallet, boosts, spend guard | Complete and unit-tested; billing stubbed |
| Photo → character generation | Complete and unit-tested |
| Manual character editor (30 parameters, colours, hair, presets) | Complete |
| Live parametric portrait renderer | Complete |
| Story engine, affection, gated scenes, saves | Complete and unit-tested |
| Cast of six love interests | Complete, with 9 shipped scenes |
| Character art, voice, music, 3D | Not started — see [Roadmap](#roadmap) |

130 unit tests cover the world, the economy, the character pipeline and the game logic.

---

## The world

You walk a pixel version of your own character between locations and talk to whoever you find. There
is no character-select menu: finding people *is* getting to know them.

```
              The Great Lodge          (meetings, and Sev over a map)
                     |
  Cabin Row ---- Camp Clearing         (the fire pit, and Idris cooking)
   |     |             |
 Field  Your      The Pine Path        (Rook, off the path)
  Lab   Cabin          |
 (Nadia)            The Beach          (Lyra at the water, Kaito on the pier)
```

Nobody is placed at random — each character is where their role puts them, so the world tells you
who someone is before they say a word. Walk up, face them, press **TALK**, and the full-size portrait
and dialogue open. If they have no new scene for you, they say something in passing rather than
nothing at all.

The walking sprite is drawn from the same `CharacterAppearance` as the portrait, so the skin tone,
hair colour and hairstyle you chose are what walks around the camp — one source of truth, no second
set of art to keep in sync.

### Maps are ASCII

Locations are authored as pictures, in `WorldAtlas`:

```
TTTTTTT--TTTTTTT
T..,,......,,..T
T.*..........*.T
T.....---......T
T....--f--.....T      f = the campfire
T.....---......T
T...x.....x....-      - = the path out east, to Cabin Row
```

You can see at a glance that the path connects, which no list of coordinates would give you. The
tests then check what the picture cannot: that every exit lands somewhere walkable, that no door
bounces you straight back, that nobody is standing inside a tree, and that **every area and every
character is reachable on foot from where the player starts**. One test walks the whole route from
the starting cabin to the beach, tile by tile.

---

## Progression, rewards and money

### Expressions are the collection

Eleven faces per character — *Soft smile*, *Caught out*, *Smouldering*, *Overwhelmed* — unlocked by
bond rank. Because the portrait renderer is parametric, an expression is a handful of offsets
(`ExpressionShape`) applied on top of whatever face that character already has, not a new
illustration. A smoulder on a wide-eyed face and on a hooded one are recognisably the same
expression *and* recognisably still those two people.

That is what makes an endless reward loop affordable: **a new face costs data, not art hours.**

When you earn one it is shown on the character immediately, full size, rather than as an icon in a
list. The reward is seeing them look at you differently, so that is what the game shows you.

### The loop is endless

`AffectionLevel` still gates story scenes and stops at Beloved, because a story has an end. **Bond
ranks** continue past it forever on a quadratic curve — the story tiers first, then numbered
devotion ranks. Once someone's written scenes run out, spending a **moment** with them still moves
the bond and still earns faces.

Moments regenerate from a stored timestamp rather than a ticking timer, so they come back with the
app closed and there is no background service to get wrong.

### What money buys, and what it does not

**Money buys time and cosmetics. It never buys affection.** Every scene, every character and every
expression is reachable by playing, for free. A test asserts the catalogue contains nothing that
even reads as content:

```kotlin
@Test fun `nothing purchasable unlocks a story scene or a character`()
@Test fun `a player who never pays still reaches every face`()
@Test fun `warmth depends on the bond and never on what was spent`()
```

That last one matters most: the same rank produces the same warmth whether the player has spent
nothing or everything. No character in this game withholds affection pending a transaction.

This is a design decision before it is an ethical one. Emotional pressure attached to payment is
what draws regulator attention and store takedowns for games in this genre — and it converts worse
than the alternative, because people who feel handled stop playing. The loop that earns money is
*wanting more time with someone you already like*.

Also built in: prices shown up front with no countdown timers or fake scarcity, bigger tiers that
are genuinely better value (asserted by a test), purchases restored on launch so a crash mid-payment
never loses what someone paid for, and a **monthly spending cap players can set on themselves**,
enforced before any payment sheet opens.

Real billing sits behind a `PurchaseGateway` interface. The shipped stub takes no money, so the
whole economy is playable and reviewable now; going live is one implementation of that interface
plus a Play Console account.

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
  world/                  WorldAtlas (the maps), WorldEngine (movement, interaction)
  store/                  Catalogue, Wallet, boosts, PurchaseGateway
  game/                   StoryEngine, GameState, Cast, StoryLibrary,
                          Bond (endless ranks), Stamina, Companionship

app/                      Android + Jetpack Compose
  photo/                  ML Kit adapter, bitmap colour sampling, EXIF handling
  ui/avatar/              Parametric portrait renderer (Compose Canvas)
  ui/overworld/           Tile art, pixel sprites, the walk-around screen
  ui/store/               Support tiers, Starlight offers, spend limit
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
| `core/` — world, economy, character, photo and game logic | 130 unit tests, run on the JVM |
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
3. **A living world.** Characters currently stand in one place; the natural next step is a daily
   schedule, so who you find at the lodge depends on the time of day.
4. **Real billing.** Implement `PurchaseGateway` against Play Billing, wire the product ids in the
   Play Console, and take prices from the platform rather than the fallback labels.
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
