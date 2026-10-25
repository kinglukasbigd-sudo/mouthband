#!/usr/bin/env bash
# One whole pass of every check MouthBand has: build quality, errors and issues, security, data
# ("backend": there is no server, so this is storage, schema and the no-network guarantee) and
# audio clarity. Usage: tools/full_check.sh [run-number]
#
# Needs: the Android SDK (local.properties), an emulator or device in ADB (EMU=<serial>, default
# emulator-5554; the phone is never touched) with the microphone permission left alone.
# Output: build/checks/run-N/*.log and a summary on stdout. Exit status 1 when anything failed.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
RUN="${1:-1}"
EMU="${EMU:-emulator-5554}"
OUT="$ROOT/build/checks/run-$RUN"
SDK="$(grep '^sdk.dir=' local.properties | cut -d= -f2)"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
APP=com.mouthband.app
ADB="adb -s $EMU"
rm -rf "$OUT"; mkdir -p "$OUT"
export ANDROID_SERIAL="$EMU"

PASSED=0; FAILED=0; SECTION=""
RESULTS="$OUT/results.txt"; : > "$RESULTS"

section() { SECTION="$1"; echo; echo "━━ $1"; }
pass() { echo "  ✓ $1"; echo "PASS|$SECTION|$1" >> "$RESULTS"; PASSED=$((PASSED + 1)); }
fail() { echo "  ✗ $1${2:+ — $2}"; echo "FAIL|$SECTION|$1|${2:-}" >> "$RESULTS"; FAILED=$((FAILED + 1)); }
slug() { echo "$1" | tr -c 'A-Za-z0-9' '_' | cut -c1-60; }

# run "label" cmd...: PASS when the command exits 0; its output goes to the step log.
run() {
  local label="$1"; shift
  local log="$OUT/$(slug "$label").log"
  if "$@" > "$log" 2>&1; then pass "$label"; else fail "$label" "see $log"; fi
}
# none_of "label" regex path...: PASS when the pattern matches nowhere.
none_of() {
  local label="$1" pattern="$2"; shift 2
  local hits
  hits="$(grep -rnE --include='*.kt' --include='*.xml' --include='*.kts' --include='*.java' "$pattern" "$@" 2>/dev/null | grep -v 'xmlns\|schemas.android.com\|www.w3.org' || true)"
  if [ -z "$hits" ]; then pass "$label"; else fail "$label" "$(echo "$hits" | head -3 | tr '\n' ' ')"; fi
}
# expect "label" "condition description" test-command...: PASS when the command succeeds.
expect() { local label="$1"; shift; if "$@" > /dev/null 2>&1; then pass "$label"; else fail "$label"; fi; }

gradle() { ./gradlew --console=plain "$@"; }

test_total() { # dir glob → "tests failures errors"
  python3 - "$@" <<'PY'
import glob, re, sys
t = f = 0
for path in glob.glob(sys.argv[1], recursive=True):
    m = re.search(r'tests="(\d+)".*?failures="(\d+)" errors="(\d+)"', open(path).read())
    if m:
        t += int(m.group(1)); f += int(m.group(2)) + int(m.group(3))
print(t, f)
PY
}
class_tests() { # class name → number of passing tests in the XML (0 when absent)
  python3 - "$1" <<'PY'
import glob, re, sys
n = 0
for path in glob.glob(f"app/build/test-results/testDebugUnitTest/TEST-*{sys.argv[1]}.xml"):
    m = re.search(r'tests="(\d+)".*?failures="(\d+)" errors="(\d+)"', open(path).read())
    if m and m.group(2) == "0" and m.group(3) == "0":
        n += int(m.group(1))
print(n)
PY
}
instrumented_test() { # name → ok / missing / failed
  python3 - "$1" <<'PY'
import glob, re, sys
for path in glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True):
    text = open(path).read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', text, re.S):
        if m.group(1) == sys.argv[1]:
            print("failed" if "<failure" in (m.group(3) or "") else "ok"); sys.exit()
print("missing")
PY
}

START=$(date +%s)
echo "MouthBand full check, run $RUN, $(date '+%Y-%m-%d %H:%M')"

# ─────────────────────────────────────────────────────────────────────────────────────────────
section "1. Build, errors and issues"
SCHEMA_BEFORE="$(find app/schemas -name '*.json' -exec sha256sum {} + | sort | sha256sum)"
run "clean" gradle clean
run "compile (main, unit tests, device tests) from scratch" \
  gradle :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin
if grep -qE '^w: ' "$OUT/$(slug 'compile (main, unit tests, device tests) from scratch').log"; then
  fail "no compiler warnings" "$(grep -E '^w: ' "$OUT/$(slug 'compile (main, unit tests, device tests) from scratch').log" | head -2 | tr '\n' ' ')"
else pass "no compiler warnings"; fi
run "assembleDebug lintDebug detekt test" gradle assembleDebug lintDebug detekt test
run "lintRelease (with test ads allowed)" gradle lintRelease -PallowTestAdIds=true
run "detekt" gradle detekt
for variant in debug release; do
  report="app/build/reports/lint-results-$variant.txt"
  if [ -f "$report" ] && head -1 "$report" | grep -q 'No issues found'; then pass "lint $variant: No issues found"
  else fail "lint $variant: No issues found" "$(head -2 "$report" 2>/dev/null | tr '\n' ' ')"; fi
done
read -r UNIT_TOTAL UNIT_FAILED <<< "$(test_total 'app/build/test-results/testDebugUnitTest/*.xml')"
if [ "$UNIT_FAILED" = "0" ] && [ "$UNIT_TOTAL" -ge 125 ]; then pass "unit tests: $UNIT_TOTAL run, 0 failed"
else fail "unit tests" "$UNIT_TOTAL run, $UNIT_FAILED failed (expected ≥ 125 and 0)"; fi
run "code audit (tools/audit.sh)" bash tools/audit.sh
none_of "no disabled tests" '@Ignore|@Disabled|assumeTrue\(false' app/src/test app/src/androidTest
SUPPRESSIONS="$(grep -rnE '@Suppress|SuppressLint|tools:ignore|noinspection|ignoreWarnings|abortOnError = false' app/src app/build.gradle.kts | grep -v 'values/strings.xml:69\|AndroidManifest.xml:41' || true)"
if [ -z "$SUPPRESSIONS" ]; then pass "no new suppressed lint or detekt rules"; else fail "no new suppressed lint or detekt rules" "$(echo "$SUPPRESSIONS" | head -2 | tr '\n' ' ')"; fi
expect "no detekt rule switched off" bash -c "! grep -q 'active: false' config/detekt/detekt.yml"
SCHEMA_AFTER="$(find app/schemas -name '*.json' -exec sha256sum {} + | sort | sha256sum)"
[ "$SCHEMA_BEFORE" = "$SCHEMA_AFTER" ] && pass "Room schema export unchanged by the build" || fail "Room schema export unchanged by the build"

run "release build with test ads (R8 + resource shrinking)" gradle assembleRelease -PallowTestAdIds=true
RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$RELEASE_APK" ] && [ "$(stat -c %s "$RELEASE_APK")" -lt 150000000 ]; then pass "release APK under 150 MB ($(( $(stat -c %s "$RELEASE_APK") / 1048576 )) MB)"
else fail "release APK under 150 MB"; fi
if grep -qE '^ADMOB_(APP|BANNER|INTERSTITIAL)_ID=' local.properties 2>/dev/null; then
  run "release build accepts real AdMob IDs" gradle :app:checkReleaseAdIds
else
  if gradle :app:checkReleaseAdIds > "$OUT/checkReleaseAdIds.log" 2>&1; then fail "release build refuses a missing AdMob ID"
  else pass "release build refuses a missing AdMob ID"; fi
fi

# ─────────────────────────────────────────────────────────────────────────────────────────────
section "2. Security"
run "release manifest: permissions, exported components, cleartext, cloud backup" \
  python3 tools/manifest_check.py app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml app/src/main/res/xml
none_of "no web views, JS bridges, shell calls or dynamic code" \
  'WebView|addJavascriptInterface|setJavaScriptEnabled|Runtime\.getRuntime|ProcessBuilder|DexClassLoader|PathClassLoader|System\.load' app/src/main
none_of "no world-readable files, trust-all TLS or hostname bypass" \
  'MODE_WORLD|TrustManager|HostnameVerifier|SSLSocketFactory|setSSLSocketFactory|ALLOW_ALL' app/src/main
none_of "no cleartext URLs in the app" 'http://' app/src/main/java app/src/main/res
none_of "no external storage or media store access" \
  'getExternalStorage|READ_EXTERNAL|WRITE_EXTERNAL|MANAGE_EXTERNAL|MediaStore|[^a-zA-Z]Environment\.' app/src/main
none_of "no hard-coded keys, secrets or private keys" \
  'AIza[0-9A-Za-z_-]{35}|BEGIN (RSA |EC )?PRIVATE KEY|(api[_-]?key|secret|password)[[:space:]]*=[[:space:]]*"[^"]{6,}"' app/src/main app/build.gradle.kts
REAL_ADS="$(grep -rnE 'ca-app-pub-[0-9]{10,}' app/src app/build.gradle.kts store 2>/dev/null | grep -v '3940256099942544' || true)"
[ -z "$REAL_ADS" ] && pass "no real AdMob IDs in tracked files (only Google's test IDs)" || fail "no real AdMob IDs in tracked files" "$(echo "$REAL_ADS" | head -2)"
none_of "no logging outside AudioLog, no printStackTrace or println" \
  'printStackTrace|System\.out|System\.err|println\(|[^a-zA-Z]Log\.[dviwe]\(' app/src/main/java --exclude=AudioLog.kt
expect "every PendingIntent is immutable" bash -c "! grep -rn 'PendingIntent\.get' app/src/main -A4 | grep -q 'FLAG_MUTABLE'; grep -rn 'PendingIntent\.get' app/src/main -A4 | grep -q 'FLAG_IMMUTABLE'"
expect "receivers are registered not-exported (also across line breaks)" python3 -c "
import re, glob, sys
bad = []
for path in glob.glob('app/src/main/**/*.kt', recursive=True):
    text = open(path).read()
    for m in re.finditer(r'(?<!un)registerReceiver\\(', text):
        if 'RECEIVER_NOT_EXPORTED' not in text[m.end():m.end() + 400]:
            bad.append(path)
sys.exit(1 if bad else 0)"
DEPS="$OUT/dependencies.txt"
gradle :app:dependencies --configuration releaseRuntimeClasspath > "$DEPS" 2>&1
UNEXPECTED_DEPS="$(grep -E '^[+\\|]|---' "$DEPS" | sed -E 's/^[| +\\-]+//; s/ \(\*\)//; s/ -> .*//' | awk -F: '{print $1":"$2}' | sort -u | grep -vE '^(androidx\.|com\.google\.android\.gms:|com\.google\.android\.ump:|com\.google\.android\.play:|com\.google\.guava:|com\.google\.errorprone:|com\.google\.code\.findbugs:|com\.google\.j2objc:|com\.microsoft\.onnxruntime:|com\.squareup\.okio:|org\.jetbrains|org\.checkerframework:|org\.jspecify:|:$)' || true)"
[ -z "$UNEXPECTED_DEPS" ] && pass "dependencies: only AndroidX, Kotlin, Google Ads/UMP, ONNX Runtime, okio, Guava" || fail "dependencies: unexpected libraries" "$(echo "$UNEXPECTED_DEPS" | tr '\n' ' ')"
expect "no GPL/AGPL/LGPL, TarsosDSP, FFmpeg or LAME code" bash -c "! grep -rniE 'tarsos|ffmpeg|libmp3lame|\\bgpl\\b|agpl' '$DEPS' app/src/main/java app/build.gradle.kts gradle/libs.versions.toml"
expect "no analytics, crash-reporting or network client libraries" bash -c "! grep -qiE 'firebase|crashlytics|analytics|sentry|bugsnag|amplitude|mixpanel|okhttp|retrofit|volley|ktor|apollo' '$DEPS'"
NATIVE="$(unzip -l "$RELEASE_APK" | awk '{print $4}' | grep '\.so$' | xargs -n1 basename | LC_ALL=C sort -u | tr '\n' ' ')"
[ "$NATIVE" = "libandroidx.graphics.path.so libdatastore_shared_counter.so libonnxruntime.so libonnxruntime4j_jni.so " ] && pass "native libraries are exactly the expected four" || fail "native libraries are exactly the expected four" "$NATIVE"
run "release APK signature verifies (apksigner)" "$BUILD_TOOLS/apksigner" verify --verbose "$RELEASE_APK"
run "native libraries 16 KB aligned (zipalign -P 16)" "$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$RELEASE_APK"
expect "debug-only gallery removed by R8 (no class in the release dex or mapping)" bash -c "! unzip -p '$RELEASE_APK' 'classes*.dex' | grep -aq 'mouthband/ui/gallery' && ! grep -q '^com.mouthband.ui.gallery' app/build/outputs/mapping/release/mapping.txt"
expect "release APK has no demo-song string" bash -c "! unzip -p '$RELEASE_APK' 'classes*.dex' resources.arsc | grep -aq 'Create a demo song'"
expect "release APK is not debuggable" bash -c "! '$BUILD_TOOLS/aapt2' dump badging '$RELEASE_APK' | grep -q 'application-debuggable'"

# ─────────────────────────────────────────────────────────────────────────────────────────────
section "3. Backend and data (no server: storage, schema, no-network guarantee)"
none_of "no network code in the app (java.net, sockets, HTTP clients)" \
  'java\.net\.|HttpURLConnection|okhttp3|retrofit2|URL\(|Socket\(|InetAddress' app/src/main/java
expect "network permissions exist only for the ad and consent SDKs" bash -c "grep -c 'android.permission.INTERNET' app/src/main/AndroidManifest.xml | grep -q 1 && grep -rln 'com.google.android.gms.ads\|com.google.android.ump' app/src/main/java | grep -q ."
expect "no storage permissions requested" bash -c "! grep -qE 'STORAGE|MANAGE_MEDIA|READ_MEDIA' app/src/main/AndroidManifest.xml"
expect "Room schema versions have exported JSON" bash -c "find app/schemas -name '*.json' | grep -q ."
# Device tests: migrations, kept takes surviving a restart, editing, re-recording, copy, delete.
run "device tests on $EMU (SwiftF0 model, migrations, song flow, editing, clarity)" gradle connectedDebugAndroidTest
for t in migratesEveryVersionToTheCurrentSchema keptTakesSurviveARestartAndTheSongExports \
         everySoundOfAPartIsSavedAndSoundsDifferent everyDrumKitIsSavedWithTheSongAndSoundsDifferent \
         panLevelAndMuteSurviveAndReachTheMix editedNotesReplaceTheOldOnesAndRecordingAgainKeepsOnlyTheNewFile \
         everySoundOfEveryPartIsAudibleCleanAndTheRightLength aMutedPartIsSilentInTheMixButCanBeAuditionedUnmuted aCopyIsIndependentAndDeletingASongRemovesItsFiles \
         choosingASoundSavesItAndPlaysThePartAlone aDrumKitBelongsToTheSongAndIsSaved \
         aSoundThatDoesNotBelongToThePartIsRefused closingTheSheetEndsTheAudition \
         aMutedPartCanStillBeHeardAndStaysMuted panIsClampedHeardAndSavedOnRelease \
         aFinishedSongOpensInArrangeAndEverySoundOfAPartCanBeChosen theVoiceTrackCanBeHeardAndStopped \
         editingFromTheCoachCardSavesBackToTheCoachCard leavingTheEditorFromArrangeKeepsTheTakeAsItWas \
         everySampleArrivesInOrderAndTheHummedLineIsDrawnOnItsNotes theMeterVerdictFollowsHowLoudThePerformanceIs \
         aFastBurstOfSamplesLongerThanTheRingIsNeverReorderedOrDuplicated; do
  [ "$(instrumented_test "$t")" = "ok" ] && pass "device test $t" || fail "device test $t"
done
for t in TakeFileTest TakeStoreSweepTest ExportTest; do
  [ "$(class_tests "$t")" -ge 1 ] && pass "unit tests $t ($(class_tests "$t"))" || fail "unit tests $t"
done

# ─────────────────────────────────────────────────────────────────────────────────────────────
section "4. Audio clarity (every loudness gets its notes)"
for pair in ClarityTest:11 VocalNoiseTest:3 RobustnessTest:6 LiveTakeTest:10 LiveSinksTest:6 LegatoTest:6 PitchTest:14 BeatboxTest:8; do
  name="${pair%%:*}"; min="${pair##*:}"; n="$(class_tests "$name")"
  [ "$n" -ge "$min" ] && pass "$name: $n tests pass (≥ $min)" || fail "$name" "$n passing, expected ≥ $min"
done
[ "$(instrumented_test theModelKeepsTheThreeNotesFromWhisperToClipping)" = "ok" ] \
  && pass "SwiftF0 keeps the three notes at -66 … +6 dBFS (whisper to clipped)" || fail "SwiftF0 level sweep"
[ "$(instrumented_test bundledModelTracksSines)" = "ok" ] && pass "SwiftF0 tracks pitch on the device" || fail "SwiftF0 tracks pitch on the device"

# ─────────────────────────────────────────────────────────────────────────────────────────────
section "5. Stability on the device ($EMU): random input, crashes, ANRs"
# One monkey pass. Only a crash or ANR of MouthBand itself fails a run. The emulator's own processes
# (SystemUI, launcher) sometimes stall under 1 500 random events; such a stall is retried once with the
# same seed and noted, and fails only if it happens again.
monkey_once() { # label seed logfile
  $ADB shell am force-stop "$APP" > /dev/null 2>&1
  $ADB shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  sleep 2
  $ADB logcat -c
  $ADB shell monkey -p "$APP" --pct-syskeys 0 --throttle 60 -s "$2" -v 1500 > "$3" 2>&1
}
monkey_run() { # apk label seed
  local label="$2" seed="$3" log="$OUT/monkey-$2-$3.log"
  monkey_once "$label" "$seed" "$log"
  if grep -qE "^// CRASH: $APP|^ANR in $APP|ANR in $APP" "$log" || $ADB logcat -d -b crash | grep -q "$APP"; then
    fail "monkey $label seed $seed" "MouthBand crashed or stopped responding: $(grep -E 'CRASH|ANR in' "$log" | head -1)"
    return
  fi
  if ! grep -q 'Events injected: 1500' "$log"; then
    local other; other="$(grep -E '^ANR in |^// CRASH: ' "$log" | head -1)"
    echo "    note: $label seed $seed stopped early at $(grep -o 'Events injected: [0-9]*' "$log") (${other:-no reason logged}); retrying once"
    cp "$log" "$log.first-attempt"
    monkey_once "$label" "$seed" "$log"
    if grep -qE "ANR in $APP|^// CRASH: $APP" "$log" || $ADB logcat -d -b crash | grep -q "$APP"; then
      fail "monkey $label seed $seed" "MouthBand crashed or stopped responding on the retry"; return
    fi
    if ! grep -q 'Events injected: 1500' "$log"; then
      fail "monkey $label seed $seed: 1500 events" "stopped early twice: $(grep -E '^ANR in |Monkey aborted' "$log" | head -1)"; return
    fi
    pass "monkey $label seed $seed: 1500 events, no crash, no ANR (retried once after: ${other:-an emulator stall})"
    return
  fi
  pass "monkey $label seed $seed: 1500 events, no crash, no ANR"
}
# Right after an emulator boot the system is busy optimising apps; a fresh install is then slow
# enough to turn the monkey's first touches into an "input dispatching timed out" report. The stability
# runs wait for the load to settle and compile the app ahead of time (as the Play Store does on a phone).
settle_emulator() {
  for _ in $(seq 1 60); do
    local load; load="$($ADB shell cat /proc/loadavg | awk '{print $1}')"
    awk -v l="${load:-9}" 'BEGIN {exit !(l < 2.5)}' && return
    sleep 5
  done
}
if $ADB get-state > /dev/null 2>&1; then
  settle_emulator
  $ADB uninstall "$APP" > /dev/null 2>&1
  $ADB install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1 && $ADB shell cmd package compile -m speed -f "$APP" > /dev/null 2>&1; sleep 8
  for seed in 4242 99 2026 7 31337 777; do monkey_run debug debug "$seed"; done
  $ADB shell run-as "$APP" find files -name '*.tmp' > "$OUT/leftover-tmp.txt" 2>&1
  $ADB shell am force-stop "$APP"
  $ADB shell am start -n "$APP/com.mouthband.MainActivity" > /dev/null 2>&1; sleep 6
  if [ -z "$($ADB shell run-as "$APP" find files -name '*.tmp' 2>/dev/null)" ]; then pass "scratch files from interrupted recordings are swept at start"
  else fail "scratch files from interrupted recordings are swept at start"; fi
  $ADB uninstall "$APP" > /dev/null 2>&1
  $ADB install -r "$RELEASE_APK" > /dev/null 2>&1 && $ADB shell cmd package compile -m speed-profile -f "$APP" > /dev/null 2>&1; sleep 8
  for seed in 4242 99 2026 7 31337 777; do monkey_run release release "$seed"; done
  $ADB uninstall "$APP" > /dev/null 2>&1
else
  fail "device $EMU reachable for stability runs"
fi

# ─────────────────────────────────────────────────────────────────────────────────────────────
ELAPSED=$(( $(date +%s) - START ))
echo
echo "━━ Run $RUN finished in $((ELAPSED / 60)) min $((ELAPSED % 60)) s: $PASSED passed, $FAILED failed"
if [ "$FAILED" -ne 0 ]; then grep '^FAIL' "$RESULTS" | sed 's/^/    /'; exit 1; fi
exit 0
