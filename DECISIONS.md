# MouthBand — Decisions

Choices made where the build prompt was silent, ambiguous or in conflict with the toolchain.
Each entry: what, why.

## Build & toolchain
- **compileSdk 37 / targetSdk 37.** The prompt says "latest stable (36)". Compose BOM 2026.09.00 and
  Navigation 2.10 refuse to build below compileSdk 37 (AAR metadata `minCompileSdk=37`), and lint's
  `OldTargetApi` reports 37 as the latest stable target. "Latest stable" is the instruction; 36 was
  the value when the prompt was written. Behaviour changes for target 37 are covered by the device
  checks in MANUAL_STEPS.md.
- **AGP 9.4.1 with built-in Kotlin 2.4.21, Gradle 9.8.2, KSP 2.3.12.** Same toolchain as other
  projects on this machine, all latest stable at build time.
- **detekt 1.23.8** (latest stable; 2.0 is alpha). Its classpath is pinned to the Kotlin 2.0.21
  compiler it embeds (resolution rule in `app/build.gradle.kts`); it analyses sources without type
  resolution. Config `config/detekt/detekt.yml` builds on detekt's defaults with these Compose/DSP
  conventions, set in Phase 1 before any code existed:
  - `FunctionNaming`, `LongParameterList`, `LongMethod`, `MagicNumber` ignore `@Composable`
    functions (PascalCase composables, layout dp values, state + slot parameters).
  - `TopLevelPropertyNaming` accepts PascalCase constants (Compose tokens like `ButtonPadding`).
  - `MagicNumber` ignores property/companion declarations, named arguments, enums and -1, 0, 1, 2, 0.5.
  - `ReturnCount` max 3, lambdas excluded.
  - Added in Phase 3: `TooManyFunctions` counts only non-private, non-override functions (the
    threshold of 11 is unchanged). The rule guards a class's API; private helpers extracted to
    satisfy `CyclomaticComplexMethod`/`NestedBlockDepth` and lifecycle overrides are not API.
- **Lint** uses default rules. One per-string `tools:ignore="Typos"` on the coach copy "dum, dum"
  (onomatopoeia required by the prompt), one `tools:ignore` on the portrait lock (required by §6).
- **Package / application id:** `com.mouthband` / `com.mouthband.app`.

## Design system
- No colors were invented. Derived tokens are alpha variants of fixed ones (`GridBeatOverlay*`,
  `Scrim`); "White" text on Indigo/Bass/Chords is `Paper` (#FFFFFF).
- The "green Headphones connected chip": the palette has no green; the chip uses the closest
  palette tone, Melody #24E3DC, with black text (passes contrast).
- Variable font files (Fraunces, Inter, JetBrains Mono from google/fonts) are bundled once each;
  weights are selected with `FontVariation` (supported from API 26 = minSdk).
- Done segments of non-track coach steps (Vibe, Arrange, Export) fill Indigo; recording steps fill
  in their track color.
- The Record button's "rounded square" uses the card radius (10 dp) — the only non-pill radius.
- Drum grid cells are smaller than 48 dp (16 columns must fit a phone width); each cell is its own
  TalkBack node with a "Kick, step 1, on" label and a checkbox role.
- Loops longer than one bar show one 16-step bar at a time with a "Bar 1 / Bar 2" toggle (the spec
  defines the grid as 3 × 16).
- Dynamic type is capped at 1.3× by overriding `LocalDensity.fontScale` in the theme.
- Song actions (rename / duplicate / delete) live in a bottom sheet behind a "more" button instead
  of a dropdown (dropdowns carry elevation shadows).
- XML colors (`res/values/theme_colors.xml`) mirror three tokens for the window background and the
  launcher icon, which cannot read Kotlin tokens.

## Motion and smoothness
- **Screen transitions.** Going forward, the new screen slides in from the end while the old one
  drifts a quarter width the other way (parallax); back reverses it. 220 ms ease-out (the top of
  spec §3's 150–220 ms), instant when the system "remove animations" setting is on. Home is the
  exception: it stays still under the next screen and when it comes back, because its banner is a
  separate view beneath it (see Ads and release) that would otherwise have to follow every frame.
- **Predictive back.** `android:enableOnBackInvokedCallback="true"` opts in on Android 13–15;
  targeting API 36+ already makes it the default on Android 16+. `tools:targetApi="33"` marks the
  attribute as API 33+ for lint (it is ignored below 33). The pop transitions follow the back
  gesture.
  **At the root (Onboarding, Home) the app handles Back itself** with `moveTaskToBack` instead of
  leaving it to the system's back-to-home preview: on the Android 16 emulator image that
  cross-task transition sometimes left the launcher started but never resumed, no window got focus,
  and Android reported "MouthBand isn't responding" with the app's main thread idle (monkey seed 777:
  ANR 3 of 3 times before the change, 0 of 2 after; the same stall had shown up as 'SystemUI' and
  'launcher' ANRs in other runs). The cost is only the preview of the home screen while swiping back
  at the very root; every in-app back keeps the predictive animation.
- **Inside a coach step** coach card → recording → review is one `AnimatedContent`: Recording
  fades in and grows from 96 %, Review fades in and rises by 1/12 of its height, and the Indigo
  recording screen fades out in half the time so it does not linger.
- **One curve.** All UI motion goes through `easeOut` / `motionTween` (`ui/components/Motion.kt`)
  at 150–220 ms. Exceptions: the record button's 900 ms breathing pulse (a loop, not a
  transition) and press feedback (a no-bounce spring to 97 % scale). Beat boxes light up
  instantly on the click and fade out over 150 ms, so they never lag the beat.
- **Per-frame values are drawn, not recomposed.** Level meters, the live waveform, live hits,
  live pitch and every playhead are read inside `Canvas` / `drawWithContent` / `graphicsLayer`
  from `() -> T` providers, driven by a frame clock (`ui/common/FrameClock.kt`). View models no
  longer push levels, peaks or playheads through `StateFlow` at frame rate, so a 60 Hz update
  redraws one canvas instead of recomposing the screen.
- **Live recording view (`audio/LiveTake`).** The capture consumer writes each sound at the loop
  position where it was heard. It writes a peak per column (480 columns across the loop) and the
  live YIN pitch per column; gaps between 32 ms hops are filled, and unvoiced hops clear their
  columns so pauses stay visible. Drum hits keep their exact frame (at most 256). The UI reads
  the same arrays every frame, with no copies and no allocation after construction. Hits pop in
  over 160 ms. The playhead is the transport frame reaching the speaker now (frames written minus
  two buffers). Callbacks on this path are `fun interface`s (`LoopClock`, `PitchListener`), so no
  value is boxed per block.
- **Legato conversion (`convert/Legato`).** After pitch → notes, fragments of the same pitch split
  by vocal wobble are merged, breaks shorter than one 16th close up to the next note, overlaps are
  trimmed, and the last note can join the first across the loop end. A sung line plays back
  connected, as it was heard; real rests (a 16th or longer) stay.

## Product
- **Vocals are two lanes** (`VOCALS_VERSE`, `VOCALS_CHORUS`), both coach step 6, recorded one
  after the other.
- **Tempo, key and loop length lock after the first kept take** — raw takes cannot be re-timed, and
  converted notes were snapped to the key at conversion time.
- **Loop length** (2 or 4 bars) is chosen in the Vibe step; default 2.
- **Select, then delete.** In Review, a tap selects a note (on the chord track, its whole chord)
  instead of deleting it at once. "Delete note" / "Delete chord" removes it, and Undo (up to 30
  steps per take) brings back added notes, deleted notes and chords, and toggled drum hits. TalkBack
  users get a "Delete" custom action on each note.
- **Kept takes stay editable.** Arrange shows an edit button on every recorded track except
  vocals, and a coach step that already has a kept take offers "Edit the take you kept". Both open
  Review on the kept take. "Save changes" writes the edit back; Back leaves without saving. The
  raw take is untouched, so A/B still compares against the original performance. Vocals are raw
  audio with no notes, so they have no editor.
- **A finished song opens in Arrange.** The Home card of a song whose coach is done goes to Arrange
  (not Export), because that is where it is edited and mixed; Export is one tap away. In Arrange
  every part has a sheet (tap its name): hear the part alone, try each sound of that phase (a tap
  applies it, saves it and plays the part for two loops, so sounds can be compared by ear; Beat
  chooses among the five drum kits, which belong to the song, the others among their instruments),
  set its pan, edit its notes or chords, or record it again. Recording again from Arrange returns
  to Arrange after the take is kept (or the step is skipped) instead of walking on through the
  coach. Tempo, key and loop length stay locked (see above): raw takes cannot be re-timed.
- Every song is created with all 6 track rows; a row without a kept take is "not recorded".
- Each mood also sets default instruments: Chill (Electric Bass, Soft Keys, Flute), Pop (Synth Bass,
  Soft Keys, Piano), Hype (Deep Sub, Warm Pad, Bright Lead), Sad (Electric Bass, Warm Pad, Piano),
  Rock (Electric Bass, Organ, Bright Lead).

## Audio
- **Latency mapping.** When both streams report hardware timestamps, record frame 0 is placed on
  the transport from the timestamps (median of up to 15 readings) and calibration measures the
  small residual; otherwise the frames-written counter at `startRecording()` is used and the
  calibrated value is the full round trip. The method used by calibration is stored with the
  latency (`LatencySource`); the 150 ms fallback always uses the counter mapping, where 150 ms is a
  realistic round trip. The nudge adds its offset to whichever value is stored.
- **Count-in** plays clicks only (no backing) so beginners hear a clean "1-2-3-4"; backing tracks
  start on the first recorded bar. Clicks continue during the take unless Settings → Click is off.
- A 300 ms silent lead precedes the first count-in click so the mic is already capturing.
- Aligned takes keep 100 ms before the loop and the 250 ms tail so an early downbeat or a hit
  on the next "one" can be folded back into the loop by the converters.
- Calibration bursts use a different noise seed each, so a neighbouring burst inside the ±400 ms
  window cannot correlate.
- Loop buffers are always interleaved stereo (pan and chord width are baked at render time).
- **Live pitch line smoothing.** The Recording screen's pitch line is a centred median over 5 YIN
  estimates (`PitchSmoother`, 160 ms span): a single window can lock on a harmonic or on the phone's
  own playback, and the median removes such one- or two-estimate spikes while a step to a new note
  stays exact. Unvoiced estimates are never filled. Cost: the line is drawn 64 ms behind the sound.
- **No re-trigger guard on live drum hits.** Ignoring quieter onsets within 300 ms of a hit removed
  some room noise in a real recording, but would also drop a soft hi-hat right after a kick, which is
  how many people beatbox; the live lane keeps the plain jump + 120 ms refractory rule.
- **Recording timeouts.** A take ends with "Another app is using the microphone" when the mic
  delivers no new frames for 1.5 s (`MicWatchdog`), instead of waiting forever. Storage failures
  while writing the take report the storage error.
- **Every take is conditioned before it is converted (`convert/TakeConditioner`).** Phone
  microphones differ by 30 dB and people hum from a whisper to a shout, so a fixed threshold either
  drops quiet takes or confuses loud ones. Before analysis each take gets: clipped peaks rebuilt
  (`Declip`: cubic Hermite through the good samples on both sides, only for runs of 3 samples up to
  3 ms), a 40 Hz second-order high-pass in double precision (DC offset, handling thumps, wind), and
  one gain that puts the loud parts (99th percentile of 10 ms RMS) at −20 dBFS, between −36 dB and
  +60 dB and with the loudest peak kept under 0.9. A take already within 1 dB of that is left
  alone. Non-finite samples (a driver glitch) become zero. The converted loop that is kept as the
  raw take is the conditioned one, so A/B and vocals play at a clear level too.
  Below −78 dBFS (after the high-pass) nothing was performed: that is `SILENCE`, and nothing is
  boosted, so room noise is never turned into notes or hits. Proven with synthetic takes from −66
  to +6 dBFS (clipped) on the JVM (YIN) and on a device with the real SwiftF0 model.
- **A vocal take must contain a voice (`convert/VoicePresence`).** Pitched and beat takes get a
  reason from their converters when only noise was recorded, but a raw vocal take has no converter,
  so noise recorded at −76 … −20 dBFS used to be boosted into a loud hiss and kept. The loudest
  20 % of the loop (1024-point frames) are averaged and the spectral flatness of the 100 Hz – 4 kHz
  band is measured: noise is ≥ 0.5 (white noise ≈ 0.9), a sung or hummed voice stays well under it
  at any level. Noise now reports "didn't hear anything". A breathy whisper without pitch counts as
  noise.
- **Absolute loudness is not a drum feature.** The classifier's distance ignores the hit's
  peak-RMS dimension: sounds are taught at one level and performed at another, and takes are
  brought to a common level. Older taught sounds keep working.
- **Live views follow the voice, not fixed gates.** The waveform scales to the loudest column of
  the take (never beyond a 0.001 reference = −60 dBFS, so a silent room stays flat). Live drum hits
  trigger when a 5 ms frame jumps 12 dB above the background of the last 100 ms and is above
  −80 dBFS; the first 100 ms of a take only establish that background (before, the first noise
  frames looked like a jump and drew a false hit for any room noise above −78 dBFS). The live pitch
  line needs a window peak of 0.0003 (−70 dBFS) and YIN's own voicing decision, so noise draws no
  line at any level. All of it is tested with the real live sinks on realistic hum and beatbox
  audio from −60 to 0 dBFS (`LiveSinksTest`).
- **Positions stay as performed.** Converters store performed frames; the Timing slider quantizes
  at render time, so moving it never loses information. Notes sung a few ms before the downbeat
  wrap to the end of the (cyclic) loop; chord roots that wrap belong to bar 1.
- **Pitch frames** are mapped as `frame × 256` samples at 16 kHz (both trackers); onsets are
  therefore placed up to one hop (16 ms) early, which the 16th grid absorbs.
- **SwiftF0 output types** are read at runtime (the shipped model returns pitch as DOUBLE and
  confidence as FLOAT). Bare sine waves get 0.5–0.85 confidence from the model (it is trained on
  voices); voice-like hums get ≥ 0.95, so the spec's 0.9 threshold is kept.
- **Resampling** uses one tabulated 16-tap Blackman-windowed sinc (512 phases, linear
  interpolation) for takes (→ 16 kHz), WAV assets and export (→ 44.1 kHz).
- **Mixer processing.** Per-lane gain/pan/mute, the 15 % vocal reverb send and the −1 dBFS
  limiter always run; auto-mix switches on the bass 2 kHz low-pass, the ±15 % chord width, vocal
  ducking (−2 dB while vocal RMS > −30 dBFS) and the 2:1 bus compressor. The limiter runs at the
  engine rate; resampling to 44.1 kHz for export can add inter-sample overshoot of a few tenths of
  a dB (measured: −0.87 dBFS peak on the demo song).
- **Auto-mix is a toggle.** Turning it on writes the gain/pan table to the tracks (sliders stay
  editable) and enables the processing; tapping again turns the processing off and keeps levels.
- **Faders** span −30 … +6 dB; the bottom of the travel is silence (−60 dB).
- **One equal-power pan law** (`PanLaw`, unity at centre) is shared by the coach player, the kit
  renderer, the pitched renderer and the song mixer.

## Security and privacy
- **ONNX Runtime's telemetry is switched off three times.** ONNX Runtime 1.31 for Android ships
  Microsoft's 1DS usage telemetry (events to `mobile.events.data.microsoft.com/OneCollector`),
  started by a `TelemetryInitializer` content provider it merges into the app's manifest. MouthBand's
  only network use is ads and consent, so: the provider is removed from the manifest
  (`tools:node="remove"`, and `tools/manifest_check.py` fails if it ever comes back),
  `ORT_DISABLE_TELEMETRY=1` is set in `MouthBandApp.onCreate` before the library loads, and
  `OrtEnvironment.setTelemetry(false)` is called before the model session is created. Found on
  2026-10-24 in a device log (`ORTTelemetry`); in that build the client failed to start only because
  of a missing native method in the library, not by design.
- **Nothing leaves the device, backups included.** Android cloud backup (Google Drive) receives none
  of the app's data (`backup_rules.xml`, `data_extraction_rules.xml` exclude files, database and
  settings); only a direct phone-to-phone transfer carries songs over. `usesCleartextTraffic` is
  explicitly false. The privacy policy says so.
- **Manifest policy (`tools/manifest_check.py`, run on the merged release manifest).** Permissions
  are an allow list (microphone, internet, network state, and what the ad SDKs add); anything for
  storage, media, location, camera, contacts, phone or Bluetooth fails. The launcher activity is
  the only exported component without a protecting permission (WorkManager's job service and
  receiver and the profile installer are exported by their libraries, each behind a system
  permission). The FileProvider is not exported and shares only `cache/exports/`.
- **Library policy.** Release dependencies are AndroidX, Kotlin, Google Mobile Ads + UMP, ONNX
  Runtime, okio and Guava. No GPL-family code, no analytics or crash reporter, no HTTP client. The
  four native libraries in the APK are known and 16 KB aligned.
- **Damaged data is survivable.** A corrupted settings or taught-sounds file is replaced by defaults
  instead of crashing at start; a take file with a damaged header or absurd size is refused instead
  of allocating gigabytes; saved latency and count-in values are clamped to sane ranges; a take
  whose database row cannot be written does not leave its file behind; scratch files left by an
  interrupted recording are swept at start.
- **Stability runs wait for the emulator to settle and compile the app first.** Cold-booting the
  emulator and then installing a debug build made monkey seed 7 report an ANR at event 16 (reproduced
  once; the same seed passed 5 of 5 times on a warm emulator, with or without ahead-of-time
  compilation). The main-thread samples of a first launch show only ordinary start-up work
  (resource configuration, `Paint`, the first draw, building the icon vectors), then an idle main
  thread, so the cause is the system's post-boot optimisation load plus an interpreted debug build,
  not an app defect. `tools/full_check.sh` therefore waits for the load average to drop below 2.5
  and runs `cmd package compile -m speed` before the monkey (as the Play Store does on a phone).
- **`tools/phone_check.sh`** answers what only a phone can: cold start (median of 6, target ≤ 1.5 s),
  janky-frame share and 95th-percentile frame time while navigating (≤ 5 % and ≤ 22 ms), optionally
  the device test suite on the phone (`--device-tests`), and prints the voice checklist. It was
  dry-run against the emulator to check its mechanics.
- **`tools/full_check.sh`** is the whole-repo check (build from scratch without warnings, lint
  debug and release, detekt, every unit and device test, the code audit, suppression and disabled
  test scan, security, data and clarity checks, 6 monkey runs on debug and release). It was run
  three times in a row before this round was closed; see PROGRESS.md.

## Data
- **Two repositories.** `SongRepository` (songs, exports, Home overviews) and `TrackRepository`
  (track rows and take files) both expose Flows; together they are the spec's "SongRepository"
  (split to keep each class's API small). Every write is immediate (autosave); the Vibe pickers
  save after a 300 ms pause in typing/tapping.
- **Migrations.** `MouthBandDatabase.MIGRATIONS` lists every migration; `MigrationTest`
  (androidTest) validates the chain against the exported schemas in `app/schemas`.

## Export
- Exports render into `cacheDir/exports` (deleted and rewritten per export); **Save** copies the
  selected format through `ACTION_CREATE_DOCUMENT`, **Share** sends it through the FileProvider.
- An export is recorded (Home status "Exported · …", interstitial trigger) after a successful
  save, or when the user picks an app in the share sheet (the chooser's IntentSender callback); a
  dismissed share sheet is not an export.
- The play button plays the song through the same `SongMixer` that rendered the file.
- A debug-only "Create a demo song" button in the component gallery builds a complete song from the
  built-in demo patterns, to test Arrange/Export on emulators (which have no working microphone).

## Ads and release
- **The Home banner lives beneath the screens, attached for the whole session.** A phone trace showed
  that the `AdView` inside Home was detached on every navigation and its WebView rebuilt its GPU
  drawing state when Home came back (`WebViewFunctor` destroyed and created once per round trip,
  15–60 ms frames on the RenderThread). Now `HomeBannerLayer` (in `MainActivity`, beneath the
  NavHost) holds the one `AdView`; Home leaves a transparent gap of the ad's size (`HomeBannerSlot`)
  and reports where it is. While Home is not composed the view is `INVISIBLE` and paused, so it is
  never shown or measured as seen behind another screen. Screens therefore draw on a cream layer that
  lies beneath all of them; Home has no background of its own.
- **Consent first.** `AdsController.gatherConsent` runs on every activity creation: consent info is
  refreshed, the UMP form is loaded and shown when required, and the Mobile Ads SDK is initialized
  (off the main thread) only once `canRequestAds()` is true — immediately when consent from an
  earlier session already allows it.
- **No consent form and no ads while the Android UI monkey drives the app**
  (`ActivityManager.isUserAMonkey()`, checked in `AdsController.gatherConsent`). Random input clicks
  through test-ad landing pages, Play Store and sign-in flows that belong to Google's SDK; on a
  loaded emulator that made MouthBand's main thread spend more than 5 s inside the Ads SDK
  (`Uri.decode` in the SDK) or waiting on the banner WebView's render thread, which Android reports
  as an ANR of the app. Users are never a monkey, so nothing changes for them.
- **Session** = process start (`MouthBandApp.onCreate`). Counting activity creations missed the
  case where Android restores the activity after process death.
- **Interstitial** is preloaded once the SDK is ready (from the second session on) and after each
  show; it is shown only after a successful save or a chosen share target, at most every 4
  minutes, never in session 1 and never while the engine plays or the mic records (`AdPolicy`).
- **Banner:** the SDK's large anchored adaptive size (the current-orientation anchored size is
  deprecated in 25.x), Home only, paused/resumed with the lifecycle and destroyed with the view.
- **Release ad IDs** come from `local.properties`. Release builds (`assembleRelease`,
  `bundleRelease`) **fail** when an ID is missing, is one of Google's test IDs, or is not an AdMob
  ID (`checkReleaseAdIds` runs before every release build). A deliberate local QA build of the
  release variant can opt in to the test IDs with `-PallowTestAdIds=true`; `lint`, `test` and
  debug builds never need the IDs.
- **ABIs:** arm64-v8a, armeabi-v7a and x86_64. ONNX Runtime is ~35 MB per ABI and 32-bit x86 has
  no phones. Play delivers one ABI per device from the AAB.
- **Pitch model warm-up:** the SwiftF0 model is loaded and run once on silence in the background at
  app start, so the first hummed take converts faster; it also proves the ONNX keep rules in
  release builds ("SwiftF0 ready" in the log).
- **Navigation:** Export's back goes to Arrange even when the song was resumed straight at Export.
- **Large screens.** Apps targeting API 36+ cannot force portrait on large screens (smallest width
  ≥ 600 dp: tablets, unfolded foldables), so the portrait lock only applies to phones. Every screen
  keeps the phone layout centred at a 600 dp maximum width (backgrounds stay full-bleed), and the
  activity handles rotation and fold changes itself (`configChanges`), so recording and playback
  continue through a rotation. Checked on the emulator at 2560 × 1600 / 320 dpi (sw 800 dp) in
  landscape and portrait.
- **Debug tools in release.** The component gallery route and its demo-song tool are registered
  only when `BuildConfig.DEBUG`; R8 removes `com.mouthband.ui.gallery.*` and the shrinker removes
  its strings from release builds (checked in the mapping file and the release APK resources).
- **Vibe lock is explained up front.** Before the first kept take the Vibe step says that tempo,
  key and loop length can't change afterwards; once locked, it says to start a new song to change
  them.
- **Known, accepted limits.** Text scales up to 1.3× (spec §3); larger system font settings are
  capped. Drum grid cells are narrower than 48 dp because 16 steps must fit a phone width (each
  cell is still its own labelled TalkBack node); Play's pre-launch report may flag them as a
  touch-target warning.
- **Section strip labels** auto-shrink (15 → 9 sp) in narrow 4-bar blocks, because block widths
  must stay proportional to bars; TalkBack reads the full section description.
- **Store icon** `store/icon-512.png` is rendered from the launcher vector (the 72 dp visible area
  of the adaptive icon), full-bleed for Play's own mask.
