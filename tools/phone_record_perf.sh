#!/usr/bin/env bash
# Frame timing of the RECORDING screen on a real phone, with the real microphone open (it hears the
# room, or you if you hum during the count-in and the take). A throwaway song "Perf song" is created
# by an instrumentation helper and removed again; your own songs are never touched and the app is
# never uninstalled (apps are only replaced with `adb install -r`, so data stays).
#
#   tools/phone_record_perf.sh [--serial SERIAL] [--seconds 9]
#
# Needs the phone connected, unlocked and awake, and tools/ui_tap.py (uses uiautomator).
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
APP=com.mouthband.app
SERIAL=""; SECONDS_REC=9
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift ;;
    --seconds) SECONDS_REC="$2"; shift ;;
    *) echo "unknown option $1"; exit 2 ;;
  esac
  shift
done
[ -n "$SERIAL" ] || SERIAL="$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}' | head -1)"
[ -n "$SERIAL" ] || { echo "No physical device connected."; exit 2; }
ADB="adb -s $SERIAL"
OUT="$ROOT/build/phone-check"; mkdir -p "$OUT"
RELEASE_APK=app/build/outputs/apk/release/app-release.apk
DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK="$(ls app/build/outputs/apk/androidTest/debug/*.apk 2>/dev/null | head -1)"
[ -f "$RELEASE_APK" ] && [ -f "$DEBUG_APK" ] && [ -f "$TEST_APK" ] || { echo "Build first: assembleRelease -PallowTestAdIds=true assembleDebug assembleDebugAndroidTest"; exit 2; }
FAILS=0
ok()   { echo "  ✓ $1"; }
miss() { echo "  ✗ $1"; FAILS=$((FAILS + 1)); }
tap()  { python3 tools/ui_tap.py "$SERIAL" "$@" > /dev/null 2>&1; }

helper() { # seed | remove
  $ADB install -r -t "$DEBUG_APK" > "$OUT/perf-install.log" 2>&1 && $ADB install -r -t "$TEST_APK" >> "$OUT/perf-install.log" 2>&1 \
    || { miss "could not install the helper build over the app (nothing was uninstalled)"; return 1; }
  $ADB shell am instrument -w -e perfSong "$1" -e class "com.mouthband.perf.PerfSongHelper" \
    "$APP.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/perf-$1.log" 2>&1
  $ADB uninstall "$APP.test" > /dev/null 2>&1
  grep -q '^OK (' "$OUT/perf-$1.log"
}
restore() { $ADB install -r "$RELEASE_APK" > /dev/null 2>&1 && ok "release build restored (data kept)" || miss "could not restore the release build: adb install -r $RELEASE_APK"; }

echo "Recording-screen frame timing on $SERIAL ($($ADB shell getprop ro.product.model | tr -d '\r'))"
$ADB shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
$ADB shell dumpsys window 2>/dev/null | grep -q 'isKeyguardShowing=false' || { echo "Unlock the phone first."; exit 2; }

helper seed && ok "throwaway song \"Perf song\" created" || { miss "could not create the throwaway song, see $OUT/perf-seed.log"; restore; exit 1; }
restore
$ADB shell pm grant "$APP" android.permission.RECORD_AUDIO
$ADB shell cmd package compile -m speed-profile -f "$APP" > /dev/null 2>&1
$ADB shell am force-stop "$APP"; $ADB shell am start -n "$APP/com.mouthband.MainActivity" > /dev/null; sleep 3

reached=1
tap "Perf song" --wait 15            || reached=0   # Home → Arrange
[ $reached = 1 ] && { tap "Synth Bass" --wait 12 || reached=0; }    # the Bass row → its sheet
[ $reached = 1 ] && { tap "Record again" --wait 12 || reached=0; }  # → the Bass coach card
if [ $reached = 1 ]; then
  sleep 1.5
  $ADB shell dumpsys gfxinfo "$APP" reset > /dev/null
  python3 tools/ui_tap.py "$SERIAL" "Start recording" --desc --wait 12 > /dev/null 2>&1 || reached=0
fi
if [ $reached = 1 ]; then
  sleep "$SECONDS_REC"
  $ADB shell dumpsys gfxinfo "$APP" > "$OUT/gfxinfo-recording.txt"
  $ADB shell dumpsys gfxinfo "$APP" framestats > "$OUT/framestats-recording.txt"
  tap "Cancel" --wait 3 || $ADB shell input keyevent KEYCODE_BACK
  sleep 1
else
  miss "could not reach the recording screen by itself (screen content differs); no gestures were sent in the wrong place"
fi
$ADB shell am force-stop "$APP"

if [ $reached = 1 ]; then
  TOTAL="$(awk '/Total frames rendered/ {print $4}' "$OUT/gfxinfo-recording.txt")"
  JANKPCT="$(awk '/Janky frames:/ {gsub(/[()%]/, "", $4); print $4}' "$OUT/gfxinfo-recording.txt" | head -1)"
  P50="$(awk '/^50th percentile/ {gsub(/ms/, "", $3); print $3}' "$OUT/gfxinfo-recording.txt" | head -1)"
  P90="$(awk '/^90th percentile/ {gsub(/ms/, "", $3); print $3}' "$OUT/gfxinfo-recording.txt" | head -1)"
  P95="$(awk '/^95th percentile/ {gsub(/ms/, "", $3); print $3}' "$OUT/gfxinfo-recording.txt" | head -1)"
  P99="$(awk '/^99th percentile/ {gsub(/ms/, "", $3); print $3}' "$OUT/gfxinfo-recording.txt" | head -1)"
  HZ="$($ADB shell dumpsys display | grep -o 'renderFrameRate [0-9.]*' | head -1)"
  echo "  · ${TOTAL:-0} frames in ${SECONDS_REC} s of count-in and recording ($HZ), janky ${JANKPCT:-?} %, median ${P50:-?} ms, 90th ${P90:-?} ms, 95th ${P95:-?} ms, 99th ${P99:-?} ms"
  if [ -n "${TOTAL:-}" ] && [ "$TOTAL" -ge 100 ]; then
    awk -v p="${P95:-999}" 'BEGIN {exit !(p <= 22)}' && ok "95th percentile frame time ${P95} ms (target ≤ 22 ms)" || miss "95th percentile frame time ${P95} ms (target ≤ 22 ms)"
    awk -v j="${JANKPCT:-100}" 'BEGIN {exit !(j <= 5.0)}' && ok "janky frames ${JANKPCT} % (target ≤ 5 %)" || echo "  · janky frames ${JANKPCT} % at the display's own deadline (a 120 Hz screen counts every frame over 8.3 ms)"
  else
    miss "too few frames (${TOTAL:-0}) while recording; keep the screen on and rerun"
  fi
fi

helper remove && ok "throwaway song removed" || miss "could not remove the throwaway song, see $OUT/perf-remove.log (delete \"Perf song\" in the app)"
restore
echo
[ "$FAILS" -eq 0 ] && echo "Recording-screen check: done." || { echo "Recording-screen check: $FAILS problem(s)."; exit 1; }
