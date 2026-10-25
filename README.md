# MouthBand

An Android app that turns your voice into a song: beatbox becomes drums, humming becomes bass,
chords and melody, and your singing stays as it is. A coach walks you through it in 8 steps
(vibe, beat, bass, chords, melody, vocals, arrange, export). Everything is processed on the phone;
the only network use is ads (Google Mobile Ads with the UMP consent form).

Kotlin, Jetpack Compose, Room, DataStore, ONNX Runtime (SwiftF0 pitch model). minSdk 26, targetSdk 37.

## Build

```bash
./gradlew assembleDebug                              # debug build, Google's test ads
./gradlew assembleRelease -PallowTestAdIds=true      # local release build with test ads
./gradlew assembleRelease -PallowTestAdIds=true -PsideloadPhone=true   # one small arm64 APK for a phone
./gradlew bundleRelease                              # Play bundle: needs real AdMob IDs and an upload key
```

Release builds stop without real AdMob IDs in `local.properties` unless `-PallowTestAdIds=true` is
given; without `keystore.properties` they are signed with the debug key. See
[MANUAL_STEPS.md](MANUAL_STEPS.md).

## Checks

```bash
tools/full_check.sh 1      # clean build, lint, detekt, unit and device tests, security, monkey (emulator)
tools/phone_check.sh       # cold start and frame timing on a connected phone
tools/phone_mic_test.sh    # live recording through a real phone microphone
```

## Documents

- [PROGRESS.md](PROGRESS.md): what was built and verified, phase by phase, with measurements
- [DECISIONS.md](DECISIONS.md): design and engineering decisions and why
- [MANUAL_STEPS.md](MANUAL_STEPS.md): what needs a person (AdMob, signing, Play Console, voice checks)
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): licenses of fonts, the model and libraries
