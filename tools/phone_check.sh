#!/usr/bin/env bash
# The checks that only a real phone can answer, in one command: cold start time, frame timing while
# navigating, optional device tests, and the live-recording checklist. Run it with the phone
# connected over USB, USB debugging on and the screen unlocked.
#
#   tools/phone_check.sh                      # first physical device, builds the release APK
#   tools/phone_check.sh --serial SERIAL      # a specific device (from `adb devices`)
#   tools/phone_check.sh --no-build           # reuse app/build/outputs/apk/release/app-release.apk
#   tools/phone_check.sh --device-tests       # also run the device test suite on the phone
#   tools/phone_check.sh --allow-emulator     # dry run on the emulator (timings are not meaningful)
#
# Targets (MANUAL_STEPS.md §2): cold start under 1.5 s, at most 10 % janky frames (counted against the
# display's own deadline: 8.3 ms on a 120 Hz screen) and a 95th percentile frame time of 22 ms or less
# while navigating. `tools/phone_record_perf.sh` measures the recording screen the same way. Exit status 1 when a target is missed.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
APP=com.mouthband.app
SERIAL=""; BUILD=1; TESTS=0; EMULATOR=0
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift ;;
    --no-build) BUILD=0 ;;
    --device-tests) TESTS=1 ;;
    --allow-emulator) EMULATOR=1 ;;
    *) echo "unknown option $1"; exit 2 ;;
  esac
  shift
done
if [ -z "$SERIAL" ]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | { if [ "$EMULATOR" = 1 ]; then cat; else grep -v '^emulator-'; fi; } | head -1)"
fi
[ -n "$SERIAL" ] || { echo "No physical device found. Connect the phone (USB debugging on) or use --allow-emulator."; exit 2; }
ADB="adb -s $SERIAL"
OUT="$ROOT/build/phone-check"; mkdir -p "$OUT"
FAILS=0
ok()   { echo "  ✓ $1"; }
miss() { echo "  ✗ $1"; FAILS=$((FAILS + 1)); }
note() { echo "  · $1"; }

echo "Phone check on $SERIAL: $($ADB shell getprop ro.product.model | tr -d '\r') (Android $($ADB shell getprop ro.build.version.release | tr -d '\r'))"

# The screen must be on and unlocked, or every number below is wrong.
for i in $(seq 1 24); do
  $ADB shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
  if $ADB shell dumpsys window 2>/dev/null | grep -q 'isKeyguardShowing=false'; then break; fi
  [ "$i" = 1 ] && echo "Unlock the phone (waiting up to 2 minutes)…"
  sleep 5
done
$ADB shell dumpsys window 2>/dev/null | grep -q 'isKeyguardShowing=false' || { echo "The phone stayed locked."; exit 2; }

if [ "$BUILD" = 1 ]; then
  echo "Building the release APK (test ads allowed)…"
  ./gradlew --console=plain assembleRelease -PallowTestAdIds=true > "$OUT/build.log" 2>&1 || { echo "Build failed, see $OUT/build.log"; exit 2; }
fi
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "$APK is missing"; exit 2; }
$ADB install -r "$APK" > "$OUT/install.log" 2>&1 || { echo "Install failed:"; cat "$OUT/install.log"; exit 2; }
$ADB shell pm grant "$APP" android.permission.RECORD_AUDIO
$ADB shell cmd package compile -m "${COMPILE_MODE:-speed-profile}" -f "$APP" > /dev/null 2>&1
ok "release APK installed ($(( $(stat -c %s "$APK") / 1048576 )) MB), microphone granted, profile compiled"

echo "Cold start"
TIMES=()
for i in 1 2 3 4 5 6; do
  $ADB shell am force-stop "$APP"; sleep 1.5
  t="$($ADB shell am start -W -n "$APP/com.mouthband.MainActivity" | awk '/TotalTime/ {print $2}' | tr -d '\r')"
  [ -n "$t" ] && TIMES+=("$t")
done
MEDIAN="$(printf '%s\n' "${TIMES[@]}" | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')"
note "TotalTime of ${#TIMES[@]} starts: ${TIMES[*]} ms (median ${MEDIAN:-?} ms)"
if [ -n "${MEDIAN:-}" ] && [ "$MEDIAN" -le 1500 ]; then ok "cold start median $MEDIAN ms (target ≤ 1500 ms)"; else miss "cold start median ${MEDIAN:-?} ms (target ≤ 1500 ms)"; fi

echo "Frame timing while navigating (Home ⇄ Settings, scrolling)"
# Every gesture below is only sent while MouthBand has the focus; if it lost it (a different screen
# than expected, the app left the foreground) the app is started again, and after three misses the
# step stops, so no tap or swipe ever lands in another app.
in_app() { $ADB shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | grep -q "$APP"; }
ensure_app() {
  in_app && return 0
  $ADB shell am start -n "$APP/com.mouthband.MainActivity" > /dev/null 2>&1; sleep 2
  in_app
}
SIZE="$($ADB shell wm size | awk -F'[ x]+' '/Physical/ {print $3" "$4}' | tr -d '\r')"
W="${SIZE% *}"; H="${SIZE#* }"
$ADB shell am force-stop "$APP"; sleep 1
$ADB shell am start -n "$APP/com.mouthband.MainActivity" > /dev/null; sleep 3
MISSES=0
if ensure_app; then
  # One warm-up pass first: the very first composition of each screen is a one-off cost that says
  # nothing about how the app moves in use.
  $ADB shell input tap $((W * 88 / 100)) $((H * 10 / 100)); sleep 1.5
  ensure_app && $ADB shell input keyevent KEYCODE_BACK; sleep 1.5
  $ADB shell dumpsys gfxinfo "$APP" reset > /dev/null
  for round in 1 2 3 4 5 6 7 8 9 10 11 12; do
    ensure_app || { MISSES=$((MISSES + 1)); [ "$MISSES" -ge 3 ] && break; continue; }
    $ADB shell input tap $((W * 88 / 100)) $((H * 10 / 100)); sleep 1.2     # settings button (top right)
    ensure_app || { MISSES=$((MISSES + 1)); continue; }
    $ADB shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 30 / 100)) 250; sleep 0.6
    $ADB shell input swipe $((W / 2)) $((H * 30 / 100)) $((W / 2)) $((H * 70 / 100)) 250; sleep 0.6
    # Back from Settings to Home only when the focused screen is not Home already: a second BACK would leave the app.
    $ADB shell input keyevent KEYCODE_BACK; sleep 1.2
  done
  $ADB shell dumpsys gfxinfo "$APP" > "$OUT/gfxinfo.txt"
else
  echo "No gestures sent: MouthBand did not get the focus (unlock the phone and keep the screen on)." > "$OUT/gfxinfo.txt"
fi
TOTAL="$(awk '/Total frames rendered/ {print $4}' "$OUT/gfxinfo.txt")"
JANKY="$(awk '/Janky frames:/ {print $3}' "$OUT/gfxinfo.txt" | head -1)"
JANKPCT="$(awk '/Janky frames:/ {gsub(/[()%]/, "", $4); print $4}' "$OUT/gfxinfo.txt" | head -1)"
P95="$(awk '/^95th percentile/ {gsub(/ms/, "", $3); print $3}' "$OUT/gfxinfo.txt" | head -1)"
note "$TOTAL frames, $JANKY janky ($JANKPCT %), 95th percentile ${P95:-?} ms"
if [ -n "${TOTAL:-}" ] && [ "$TOTAL" -ge 60 ]; then
  awk -v j="${JANKPCT:-100}" 'BEGIN {exit !(j <= 10.0)}' && ok "janky frames $JANKPCT % (target ≤ 10 % at the display's own deadline; a 120 Hz screen counts every frame over 8.3 ms)" || miss "janky frames $JANKPCT % (target ≤ 10 %)"
  awk -v p="${P95:-999}" 'BEGIN {exit !(p <= 22)}' && ok "95th percentile frame time ${P95} ms (target ≤ 22 ms)" || miss "95th percentile frame time ${P95} ms (target ≤ 22 ms)"
else
  miss "too few frames rendered (${TOTAL:-0}) to judge: finish the app's onboarding by hand (a fresh install starts there), keep the screen on, and rerun with --no-build"
fi

if [ "$TESTS" = 1 ]; then
  echo "Device tests on the phone (installed over the app, never uninstalling: your songs stay)"
  # Gradle's connected task uninstalls the app after the run, which deletes its data. The tests are
  # installed with adb and run with `am instrument` instead; the app is only ever replaced (-r).
  if ! ./gradlew --console=plain assembleDebug assembleDebugAndroidTest > "$OUT/test-build.log" 2>&1; then miss "building the test APKs failed, see $OUT/test-build.log"
  else
    DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
    TEST_APK="$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -1)"
    if $ADB install -r -t "$DEBUG_APK" > "$OUT/test-install.log" 2>&1 && $ADB install -r -t "$TEST_APK" >> "$OUT/test-install.log" 2>&1; then
      $ADB shell am instrument -w -r "$APP.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/device-tests.log" 2>&1
      RAN="$(grep -c 'INSTRUMENTATION_STATUS_CODE: 0' "$OUT/device-tests.log")"
      BAD="$(grep -cE 'INSTRUMENTATION_STATUS_CODE: (-1|-2)' "$OUT/device-tests.log")"
      if grep -q '^OK (' "$OUT/device-tests.log" 2>/dev/null || { [ "$RAN" -gt 0 ] && [ "$BAD" -eq 0 ] && ! grep -q 'FAILURES' "$OUT/device-tests.log"; }; then ok "device tests passed on the phone ($RAN tests)"
      else miss "device tests failed on the phone ($BAD failing), see $OUT/device-tests.log"; fi
      $ADB uninstall "$APP.test" > /dev/null 2>&1   # only the test package; the app and its data stay
      $ADB install -r "$APK" > /dev/null 2>&1 && ok "release APK restored over the test build (data kept)" || miss "could not restore the release APK; reinstall it with adb install -r $APK"
    else
      miss "the test APKs could not be installed over the app (different signature?): nothing was uninstalled, see $OUT/test-install.log"
    fi
  fi
fi

cat <<'EOF'

By hand (needs your voice; the numbers above cannot see it):
  1. Start a song, open Bass, press record and hum three notes: the waveform fills in under the moving
     playhead and a pitch line follows your voice, with gaps where you pause.
  2. Do the same very quietly and then very loudly: both takes show their notes in Review.
  3. Beatbox boom / tss / pff in the Beat step: every hit appears in its row while you perform.
  4. Swipe back slowly from the edge on any inner screen: the previous screen follows your finger.
  Then tick the matching boxes in MANUAL_STEPS.md §2.
EOF

echo
if [ "$FAILS" -eq 0 ]; then echo "Phone check: all targets met."; else echo "Phone check: $FAILS target(s) missed."; exit 1; fi
