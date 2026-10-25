#!/usr/bin/env bash
# Phase-gate code audit (build prompt §7 step 5). Prints every hit; exits 1 if any are found.
set -u
cd "$(dirname "$0")/.."
SRC="app/src"
fail=0

check() {
  local title="$1"; shift
  local out
  out="$("$@" 2>/dev/null)"
  if [ -n "$out" ]; then
    echo "✗ $title"
    echo "$out" | sed 's/^/    /'
    fail=1
  else
    echo "✓ $title"
  fi
}

kt() { find "$SRC" -name '*.kt'; }

check "TODO / FIXME / stub / NotImplementedError" \
  grep -rnE '\bTODO\b|\bFIXME\b|\b[Ss]tub\b|NotImplementedError' $(kt)
check "empty catch blocks" \
  grep -rnE 'catch \([^)]*\) *\{ *\}' $(kt)
check "multi-line empty catch blocks" \
  grep -rnPzo 'catch \([^)]*\) *\{\s*\n\s*\}' $(kt)
check "!! without a justification comment on the same line" \
  bash -c "grep -rn '!!' $(kt | tr '\n' ' ') | grep -v '// !!:'"
check "color literals outside ui/theme" \
  bash -c "grep -rnE 'Color\(0x|Color\.(Red|Green|Blue|White|Black|Gray|Yellow|Cyan|Magenta)' $(kt | tr '\n' ' ') | grep -v '/ui/theme/'"
check "string literals in Text() outside resources" \
  bash -c "grep -rnE 'Text\(\s*\"[A-Za-z]' $(kt | tr '\n' ' ')"
check "runBlocking in app code" \
  grep -rn 'runBlocking' $(find "$SRC/main" -name '*.kt')
check "logging / allocation / locks inside audio loops (marked // RT-LOOP regions)" \
  bash -c "for f in \$(grep -rl 'RT-LOOP-BEGIN' $SRC/main); do awk '/RT-LOOP-BEGIN/{f=1} /RT-LOOP-END/{f=0} f && /(Log\\.|println|synchronized|ReentrantLock|FloatArray\\(|IntArray\\(|ArrayList|listOf|mutableListOf|\\.emit\\(|tryEmit|\\.value =|String\\.format|\"\\\$)/{print FILENAME\":\"NR\": \"\$0}' \$f; done"
check "AudioRecord / AudioTrack / MediaCodec created without release()" \
  bash -c "for f in \$(grep -rlE 'AudioRecord\\(|AudioTrack\\.Builder|MediaCodec\\.create' $SRC/main); do grep -q 'release()' \$f || echo \$f; done"
check "files over 400 lines" \
  bash -c "for f in \$(find $SRC -name '*.kt'); do n=\$(wc -l < \$f); [ \$n -gt 400 ] && echo \"\$f: \$n\"; done; true"

exit $fail
