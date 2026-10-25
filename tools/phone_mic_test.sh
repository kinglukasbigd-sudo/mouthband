#!/usr/bin/env bash
# Live-recording check through the phone's REAL microphone, without a voice: this computer plays a
# generated hum (a bass line of four notes, harmonics + vibrato) or a beatbox-like pattern through
# its speakers (--kind none plays nothing: shows what the phone hears of its own playback), the phone records it in the app's coach card, and the Review screen is read back
# with uiautomator. Put the phone next to the speakers (screen on, unlocked, nothing else running).
# A throwaway song "Perf song" is created and removed again; apps are only replaced (adb install -r).
#
#   tools/phone_mic_test.sh [--serial SERIAL] [--kind hum|beat|none] [--volume 0.3..1.0] [--base 48|60]
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
APP=com.mouthband.app
SERIAL=""; KIND=hum; VOLUME=""; BASE=48
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift ;;
    --kind) KIND="$2"; shift ;;
    --volume) VOLUME="$2"; shift ;;
    --base) BASE="$2"; shift ;;   # lowest hummed MIDI note (48 = C3, 60 = C4)
    *) echo "unknown option $1"; exit 2 ;;
  esac
  shift
done
[ -n "$SERIAL" ] || SERIAL="$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}' | head -1)"
[ -n "$SERIAL" ] || { echo "No physical device connected."; exit 2; }
ADB="adb -s $SERIAL"
OUT="${OUT_DIR:-$ROOT/build/phone-check}"; mkdir -p "$OUT"
# APKS=<dir> uses copies (app-release.apk, app-debug.apk, app-debug-androidTest.apk), e.g. while a
# full check rebuilds app/build in parallel.
if [ -n "${APKS:-}" ]; then
  RELEASE_APK="$APKS/app-release.apk"; DEBUG_APK="$APKS/app-debug.apk"; TEST_APK="$APKS/app-debug-androidTest.apk"
else
  RELEASE_APK=app/build/outputs/apk/release/app-release.apk
  DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
  TEST_APK="$(ls app/build/outputs/apk/androidTest/debug/*.apk 2>/dev/null | head -1)"
fi
[ -f "$RELEASE_APK" ] && [ -f "$DEBUG_APK" ] && [ -f "$TEST_APK" ] || { echo "Build first: assembleRelease -PallowTestAdIds=true assembleDebug assembleDebugAndroidTest"; exit 2; }
WAV="$OUT/mic-$KIND.wav"
python3 - "$KIND" "$WAV" "$BASE" <<'PY'
import math, random, struct, sys, wave
kind, path, base = sys.argv[1], sys.argv[2], int(sys.argv[3])
rate = 48000; random.seed(3)
buf = [0.0] * (rate * 16)
def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
if kind == "none":
    pass
elif kind == "hum":
    t0 = 0.3
    for _ in range(4):
        for off, length in ((0, .55), (0, .55), (7, .55), (9, .8)):
            midi = base + off
            start, n, phase = int(t0 * rate), int(length * rate), 0.0
            for i in range(n):
                if start + i >= len(buf): break
                phase += 2 * math.pi * hz(midi) * (1 + .004 * math.sin(2 * math.pi * 5.5 * i / rate)) / rate
                env = min(1.0, i / (.02 * rate), (n - i) / (.02 * rate))
                buf[start + i] += env * sum(math.sin(k * phase) / k for k in range(1, 9))
            t0 += length + .2
else:
    t0 = 0.3
    for _ in range(8):
        for kindhit in ("boom", "tss", "pff", "tss"):
            start = int(t0 * rate)
            n = int({"boom": .25, "tss": .12, "pff": .1}[kindhit] * rate)
            for i in range(n):
                if start + i >= len(buf): break
                t = i / rate
                if kindhit == "boom": v = math.sin(2 * math.pi * (110 - 60 * t) * t) * math.exp(-t * 18)
                elif kindhit == "tss": v = random.uniform(-1, 1) * math.exp(-t * 45) * (1 if i % 2 else -1)
                else: v = random.uniform(-1, 1) * math.exp(-t * 70)
                buf[start + i] += v
            t0 += .45
peak = max(abs(x) for x in buf) or 1.0
with wave.open(path, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
    w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.8 * 32767)) for x in buf))
PY
FAILS=0
ok()   { echo "  ✓ $1"; }
miss() { echo "  ✗ $1"; FAILS=$((FAILS + 1)); }
tap()  { python3 tools/ui_tap.py "$SERIAL" "$@" > /dev/null 2>&1; }
STEP=BASS; STEP_NUMBER=3; [ "$KIND" = beat ] && { STEP=BEAT; STEP_NUMBER=2; }
helper() {
  $ADB install -r -t "$DEBUG_APK" > "$OUT/mic-install.log" 2>&1 && $ADB install -r -t "$TEST_APK" >> "$OUT/mic-install.log" 2>&1 || return 1
  $ADB shell am instrument -w -e perfSong "$1" -e perfStep "$STEP" -e class "com.mouthband.perf.PerfSongHelper" "$APP.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/mic-$1.log" 2>&1
  $ADB uninstall "$APP.test" > /dev/null 2>&1
  grep -q '^OK (' "$OUT/mic-$1.log"
}
restore() { $ADB install -r "$RELEASE_APK" > /dev/null 2>&1 && ok "release build restored (data kept)" || miss "could not restore the release build"; }
[ -n "$VOLUME" ] && wpctl set-volume @DEFAULT_AUDIO_SINK@ "$VOLUME" > /dev/null 2>&1
echo "Real-microphone $KIND test on $SERIAL, speakers at $(wpctl get-volume @DEFAULT_AUDIO_SINK@ 2>/dev/null)"
$ADB shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
$ADB shell dumpsys window 2>/dev/null | grep -q 'isKeyguardShowing=false' || { echo "Unlock the phone first."; exit 2; }
helper seedEmpty && ok "throwaway song created (empty, at the $STEP step: only the click plays)" || { miss "could not create the throwaway song"; restore; exit 1; }
restore
$ADB shell pm grant "$APP" android.permission.RECORD_AUDIO
$ADB shell am force-stop "$APP"; $ADB shell am start -n "$APP/com.mouthband.MainActivity" > /dev/null; sleep 3
reached=1
tap "Perf song" --wait 15 || reached=0
if [ $reached = 1 ]; then
  sleep 1.5
  python3 tools/ui_tap.py "$SERIAL" "Start recording" --desc --wait 12 > /dev/null 2>&1 || reached=0
fi
if [ $reached = 1 ]; then
  pw-play "$WAV" > "$OUT/mic-play.log" 2>&1 &
  PLAYER=$!
  sleep 3.5; $ADB exec-out screencap -p > "$OUT/mic-$KIND-$BASE-recording-1.png"
  sleep 2;   $ADB exec-out screencap -p > "$OUT/mic-$KIND-$BASE-recording-2.png"
  if python3 tools/ui_tap.py "$SERIAL" "How did it sound" --exists --wait 25 > /dev/null 2>&1; then
    sleep 1.5
    kill "$PLAYER" 2> /dev/null
    $ADB exec-out screencap -p > "$OUT/mic-$KIND-$BASE-review.png"
    $ADB shell uiautomator dump --compressed /sdcard/mb_ui.xml > /dev/null 2>&1
    $ADB exec-out cat /sdcard/mb_ui.xml > "$OUT/mic-$KIND-$BASE-review.xml"
    tap "Keep it" --wait 5 && ok "take kept" || miss "could not press Keep it"
    # The take is converted, rendered and stored before the next step opens; wait for that screen.
    python3 tools/ui_tap.py "$SERIAL" "STEP 0$((STEP_NUMBER + 1))" --exists --wait 30 > /dev/null 2>&1 && ok "saved (next step opened)" || miss "the next step did not open after Keep it"
    sleep 1
    $ADB logcat -c
    $ADB install -r -t "$DEBUG_APK" > /dev/null 2>&1 && $ADB install -r -t "$TEST_APK" > /dev/null 2>&1
    $ADB shell am instrument -w -e perfSong dump -e class "com.mouthband.perf.PerfSongHelper" "$APP.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/mic-dump.log" 2>&1
    $ADB logcat -d -s PerfDump:I | sed 's/^.*PerfDump *: *//' | sed 's/^I *//' > "$OUT/mic-$KIND-$BASE-notes.txt"
    $ADB uninstall "$APP.test" > /dev/null 2>&1
    echo "  · what the app made of it:"; sed 's/^/      /' "$OUT/mic-$KIND-$BASE-notes.txt"
  else
    kill "$PLAYER" 2> /dev/null
    miss "the Review screen never appeared"
  fi
else
  $ADB exec-out screencap -p > "$OUT/mic-$KIND-$BASE-stuck.png"
  miss "could not reach the recording screen by itself (screen saved as mic-$KIND-$BASE-stuck.png)"
fi
helper remove && ok "throwaway song removed" || miss "could not remove the throwaway song (delete \"Perf song\" in the app)"
$ADB shell am force-stop "$APP"
restore
[ "$FAILS" -eq 0 ] && echo "Mic test: done (read the saved screens)." || { echo "Mic test: $FAILS problem(s)."; exit 1; }
