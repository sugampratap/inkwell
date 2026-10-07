#!/bin/bash
# Polish-gate frame check (PRD §8.1): scripted journeys on the connected device, janky-frame stats
# from `dumpsys gfxinfo`, appended to docs/review-b2/<label>.txt.
#   Usage (repo root): tools/polish/gate.sh <label> [package]
# Gate: under 1% janky frames per journey and no frame over 33 ms (99th percentile) on the S8.
# The emulator's numbers are only good for before/after comparisons on the same emulator.
set -u
LABEL=${1:?usage: tools/polish/gate.sh <label> [package]}
PKG=${2:-app.inkwell.debug}
A=${ADB:-adb}
export MSYS_NO_PATHCONV=1
OUT="docs/review-b2/$LABEL.txt"
mkdir -p docs/review-b2

read -r W H < <($A shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
if [ "$H" -gt "$W" ]; then T=$W; W=$H; H=$T; fi   # the tablet works in landscape
px() { echo $(( $1 * $2 / 100 )); }
swipe() { $A shell input swipe "$(px "$W" "$1")" "$(px "$H" "$2")" "$(px "$W" "$3")" "$(px "$H" "$4")" "${5:-250}"; }
reset() { $A shell dumpsys gfxinfo "$PKG" reset > /dev/null; }
stats() {
  echo "== $1" | tee -a "$OUT"
  $A shell dumpsys gfxinfo "$PKG" \
    | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" \
    | tee -a "$OUT"
}

echo "# $LABEL · $(date '+%Y-%m-%d %H:%M') · $($A shell getprop ro.product.model | tr -d '\r')" | tee -a "$OUT"

# Cold start, three runs.
for i in 1 2 3; do
  $A shell am force-stop "$PKG"; sleep 1
  $A shell am start -W -n "$PKG/com.xnotes.MainActivity" | grep -E "TotalTime" | sed 's/^/cold start /' | tee -a "$OUT"
  sleep 3
done
sleep 7   # let the library finish loading first: it takes ~5 s after a cold start on the emulator

# 1. Library scroll (the library must hold at least ~30 notes so it scrolls).
reset
for i in 1 2 3 4 5; do swipe 60 80 60 25; sleep 0.6; swipe 60 25 60 80; sleep 0.6; done
stats "library scroll"

# 2. Page scroll in a note.
read -r -p "Open a note with 10+ pages, then press Enter and don't touch the screen… " _
reset
for i in 1 2 3 4 5; do swipe 50 80 50 20 200; sleep 0.5; done
for i in 1 2 3 4 5; do swipe 50 20 50 80 200; sleep 0.5; done
stats "page scroll"

# 3. Popovers: a second tap on the armed pen opens its card, a third closes it.
read -r -p "Pen button position in device px as 'x y' (from a screenshot): " PX PY
$A shell input tap "$PX" "$PY"; sleep 0.5
reset
for i in 1 2 3 4 5 6 7 8 9 10; do $A shell input tap "$PX" "$PY"; sleep 0.8; done
stats "pen popover open/close"

# 4. Pages side panel.
read -r -p "Pages-panel button position as 'x y': " SX SY
reset
for i in 1 2 3 4 5 6; do $A shell input tap "$SX" "$SY"; sleep 0.8; done
stats "side panel open/close"
echo | tee -a "$OUT"
