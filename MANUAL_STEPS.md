# MouthBand — Manual steps

Things that need a real device, an account, or files only you can provide. Everything else is
built, tested and documented in `PROGRESS.md`.

## 1. Before the first release (accounts and keys)
1. **AdMob app and ad units.** In AdMob, create the Android app `com.mouthband.app`, one
   *Banner* unit (Home) and one *Interstitial* unit (after export). Put the IDs in
   `local.properties` (git-ignored):
   ```
   ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   ADMOB_BANNER_ID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
   ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
   ```
   Release builds (`bundleRelease`, `assembleRelease`) **stop with an error** until all three
   are real AdMob IDs, so a release can never ship with test ads by mistake. For a local test of
   the release variant you can run `./gradlew assembleRelease -PallowTestAdIds=true` (shows
   Google's test ads). Debug builds, `lint` and `test` always work without the IDs.
2. **Consent message.** In AdMob → *Privacy & messaging*, create and publish a **GDPR (European
   regulations) message** for the app. The UMP form in the app only appears once a message is
   published. Optionally add a US state regulations message too.
3. **Upload key.** Create an upload keystore and a git-ignored `keystore.properties` in the
   project root:
   ```
   storeFile=upload-keystore.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
   Without it, `bundleRelease` signs with the debug key (fine for testing, refused by Play).
   Enrol in Play App Signing when creating the app in Play Console.
4. **Privacy policy.** In `store/PRIVACY_POLICY.md` replace `[YOUR_EMAIL]` and `[DATE]`, publish it
   at a public URL (for example GitHub Pages) and enter that URL in Play Console.
5. **Play Console forms.** Fill *Data safety* from `store/DATA_SAFETY.md`, *Content rating* and
   *Target audience* (13+) from `store/CONTENT_RATING.md`, the listing from
   `store/short_description.txt` and `store/full_description.txt`, the icon `store/icon-512.png`.
6. **Feature graphic and screenshots.** Design the 1024 × 500 feature graphic and take the six
   phone screenshots described in `store/LISTING_GRAPHICS.md` (on a real phone, with real takes).

## 2. Device checks (cannot run on the emulator — it has no working microphone)
Run `tools/phone_check.sh` first, with the phone connected (USB debugging on) and unlocked: it builds
and installs the release APK, measures the cold start and the frame timing while navigating, can run
the device tests on the phone (`--device-tests`) and prints the voice checklist below. Add
`--no-build` to reuse the last release APK. `tools/phone_record_perf.sh` measures the recording screen
with the real microphone open (a throwaway song, removed afterwards). `tools/phone_mic_test.sh --kind
hum|beat` plays a generated hum or beat through the computer's speakers next to the phone and checks
what the app records from it. Measured on the SM-S731B: cold
start 134–209 ms, recording screen 0.39 % janky at 120 Hz (see PROGRESS.md). The checks that need your
voice or hands follow.
1. **Latency within ±10 ms (Phase 2).** Settings → Re-run calibration (phone on a table, volume
   up, no headphones). Then record a Beat take clapping on the clicks; in Review the claps must sit
   on the grid columns (Timing slider at "Human" shows raw timing).
2. **Beatbox recognition (Phase 3).** Open a song → Beat. The first take opens Teach your sounds;
   say "boom", "pff", "tss" 4 times each. Then record boom · tss · pff · tss for two bars: Review
   must show kicks on 1 and 3, snares on 2 and 4, hi-hats in between. Reset the sounds in Settings
   and record again to check the rule fallback.
3. **Humming to notes (Phase 4).** Bass step → hum a slow line; the piano roll shows the notes in
   the bass range, snapped to the song key. Chords step → hum one root per bar; each bar shows a
   triad. Melody step → hum a tune; A/B "Converted" plays it back.
4. **Quality bar (spec §8), on at least 2 real phones, one of them low-end:**
   - [ ] No audio glitches during a 3-minute playback with 5 tracks (Arrange → Play).
   - [ ] Recording stays in sync after 10 consecutive takes.
   - [ ] Rotation, backgrounding and a phone call stop recording cleanly and the app resumes.
   - [ ] Mic permission denied → the "MouthBand needs the microphone" screen, no crash.
   - [ ] Bluetooth headphones → the warning sheet appears on a recording step.
   - [ ] Silence or noise recorded → "didn't hear anything" state, no crash, nothing saved.
   - [ ] An exported M4A and WAV play correctly in another app (WhatsApp, Files).
   - [ ] Cold start under 1.5 s on the low-end phone; no ANRs; memory under 250 MB during export
         (Android Studio profiler).
   - [ ] TalkBack can complete the Coach flow.
   - [ ] No ads on recording or review screens (ads appear only on Home and after an export).
   - [ ] The release build has no "Create a demo song" tool: long-pressing the MouthBand title on
         Home does nothing.
   - [ ] If you have a tablet or a foldable: open the app unfolded, rotate it, and check the
         layout stays centred and readable (Android ignores the portrait lock on large screens).

5. **Smoothness, live recording and editing (latest round):**
   - [ ] Recording: while you beatbox, every hit appears in its row at the moment you hear it.
         While you hum, the pitch line follows your voice with gaps where you pause. The waveform
         fills in under the moving playhead.
   - [ ] Hum a connected line (no breaks): in Review it plays back connected, not choppy.
   - [ ] Back gesture: swipe slowly from the screen edge on any inner screen. The previous
         screen follows your finger, and letting go completes or cancels it. On Home (and on the
         first onboarding page) Back returns to the launcher without a preview animation; the app
         stays alive and reopens where it was.
   - [ ] Keep a Bass take. On the Chords coach card press Back (the Bass Review appears), then
         Back again (the Bass coach card). Tap "Edit the take you kept", select a note, delete it
         and tap "Save changes": you are back on the coach card, and Arrange plays the line
         without that note. Opening the editor again and pressing Back changes nothing.
   - [ ] Arrange → pencil on Chords: tapping a chord selects all of its notes; "Delete chord"
         removes it and Undo brings it back.
   - [ ] On the low-end phone, turn on Developer options → Profile HWUI rendering. Navigating,
         recording and playing back in Review should keep the bars under the green line
         (16 ms).

6. **Loud and quiet voices (clarity round):**
   - [ ] Record the Bass step twice: once humming very softly (arm's length from the phone), once
         close to the microphone and loud. Both takes show their notes in Review, and A/B
         "Original" plays at a comfortable level (the app brings every take to a common level).
   - [ ] Beatbox the Beat step very quietly and then very loudly: kick / snare / hi-hat land in
         the right rows both times. (Teach your sounds once at a normal volume.)
   - [ ] Record in a silent room without making a sound: you get "didn't hear anything", not notes.
   - [ ] Live view while recording: a whisper still draws its waveform across the full lane height.
7. **Finished songs (editing and mixing):**
   - [ ] Open a song whose coach is done: it opens in Arrange (not Export).
   - [ ] Tap "Bass ›": the sheet shows the three bass sounds. Tap each: it plays the bass alone
         with that sound, and the row shows the chosen name afterwards. Same for Chords, Melody
         and, for Beat, the five drum kits. Set the pan and play the song: the part moves in the
         stereo field.
   - [ ] "Record again" on a part: after keeping the new take you land back in Arrange, not in the
         next coach step. "Edit notes" opens the editor on the kept take.

## 3. Expected Play pre-launch warnings
- **Touch target size** on the drum grid cells (16 steps across a phone width are narrower than
  48 dp). Each cell has its own TalkBack label; this is a warning, not a rejection.

## 4. Optional
1. **Instrument samples.** Every preset already plays through its synth fallback. For real
   sampled instruments follow `tools/SAMPLES.md` (FluidR3_GM.sf2, MIT, exported with Polyphone) and
   add the FluidR3 MIT notice as described there.
2. **16 KB page size on a device.** All native libraries are already 16 KB aligned (checked in the
   release APK, see `PROGRESS.md`); optionally confirm on a 16 KB system image or a phone running
   with 16 KB pages.
