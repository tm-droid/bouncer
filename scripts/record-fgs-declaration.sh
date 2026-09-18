#!/bin/bash
#
# Record the Play Console declaration clip for FOREGROUND_SERVICE_DATA_SYNC.
#
# The clip must show the in-app steps that start the foreground service, so it
# records the real debug build driven over adb: follow a live match from Home,
# leave the app, show the ongoing Live Update notification the service posts,
# then Stop it.
#
#   scripts/record-fgs-declaration.sh preflight   # measure tap targets
#   scripts/record-fgs-declaration.sh take        # record and pull the mp4
#
# Two phases because a `uiautomator dump` takes seconds and competes with
# screenrecord: takes that located their taps while recording came out
# truncated and mistimed. preflight measures the coordinates and leaves the
# device unfollowed; take replays them with no dumps inside the recording.
#
# Requires: a live match in the app's Live tab (the service only tracks matches
# present in the cached live list), adb on PATH or the SDK in the default place,
# ffprobe for the duration check.
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}
OUT_DIR=${OUT_DIR:-distribution/declarations}
WORK=${TMPDIR:-/tmp}/fgs-declaration
PKG=com.tanush.bouncer
ACTIVITY=$PKG/.BouncerActivity
mkdir -p "$WORK" "$OUT_DIR"

step() { echo "[$(date +%T)] $*"; }
dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB pull /sdcard/ui.xml "$WORK/ui.xml" >/dev/null 2>&1
}
services() { $ADB shell dumpsys activity services $PKG 2>/dev/null | grep -c LiveUpdateService; }

wait_for_list() {
  for _ in $(seq 1 15); do
    sleep 4
    dump
    grep -q 'live updates"' "$WORK/ui.xml" && { step "live list ready"; return 0; }
  done
  echo "the live list never loaded — is anything live right now?" >&2
  return 1
}

# Echo "x y" for the first node matching desc=<exact> or text=<substring>.
node_at() {
  python3 - "$WORK/ui.xml" "$1" "$2" <<'PY'
import re
import sys
path, kind, want = sys.argv[1:4]
xml = open(path, encoding='utf-8').read()
for n in re.findall(r'<node [^>]*/?>', xml):
    attr = lambda k: (re.search(r'%s="([^"]*)"' % k, n) or [None, ''])[1]
    hit = attr('content-desc') == want if kind == 'desc' else want in attr('text')
    if hit:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', attr('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

# The bell of the first live row whose BOTH sides carry runs. A rained-off row
# reads "Knights versus Lions." with no figures and its card is a blank
# scoreboard, which demonstrates nothing.
scored_row_bell() {
  python3 - "$WORK/ui.xml" <<'PY'
import re
import sys
xml = open(sys.argv[1], encoding='utf-8').read()
nodes = re.findall(r'<node [^>]*/?>', xml)
attr = lambda n, k: (re.search(r'%s="([^"]*)"' % k, n) or [None, ''])[1]
box = lambda n: list(map(int, re.findall(r'\d+', attr(n, 'bounds'))))
bells = [n for n in nodes if attr(n, 'content-desc') == 'Follow live updates']
rows = [n for n in nodes if ' versus ' in attr(n, 'content-desc')]
for b in bells:
    bx1, by1, bx2, by2 = box(b)
    for r in rows:
        _, ry1, _, ry2 = box(r)
        desc = attr(r, 'content-desc')
        if ry1 <= by1 and by2 <= ry2 and re.search(r'\d.*\bversus\b[^.]*\d', desc):
            print((bx1 + bx2) // 2, (by1 + by2) // 2, desc[:70])
            raise SystemExit
PY
}

restart_app() {
  $ADB shell am force-stop $PKG
  sleep 1
  $ADB shell am start -n $ACTIVITY >/dev/null 2>&1
}

preflight() {
  step "restarting the app"
  restart_app
  wait_for_list || exit 1

  # A follow survives force-stop: it lives in prefs and the service is re-armed
  # on the next foreground. Clear it or the take opens already subscribed.
  for _ in 1 2 3 4; do
    dump
    old=$(node_at desc "Stop live updates")
    [ -z "$old" ] && break
    step "clearing a leftover follow at $old"
    $ADB shell input tap $old
    sleep 3
  done

  sleep 3
  dump
  bell=$(scored_row_bell)
  [ -z "$bell" ] && { echo "no live row with runs on both sides" >&2; exit 1; }
  bx=$(echo "$bell" | awk '{print $1}')
  by=$(echo "$bell" | awk '{print $2}')
  step "bell ($bx,$by) on: $(echo "$bell" | cut -d' ' -f3-)"

  step "probing the card so the take knows where Stop sits"
  $ADB shell input tap "$bx" "$by"
  sleep 4
  dump
  grep -q 'Stop live updates' "$WORK/ui.xml" || { echo "the bell did not flip" >&2; exit 1; }
  $ADB shell input keyevent 3
  sleep 3
  $ADB shell cmd statusbar expand-notifications
  sleep 6
  dump
  stop=$(node_at text Stop)
  [ -z "$stop" ] && { echo "no Stop action on the card" >&2; exit 1; }
  step "Stop at $stop"
  $ADB shell input tap $stop
  sleep 4
  $ADB shell cmd statusbar collapse

  cat > "$WORK/coords.env" <<EOF
BX=$bx
BY=$by
SX=$(echo "$stop" | awk '{print $1}')
SY=$(echo "$stop" | awk '{print $2}')
EOF
  step "wrote $WORK/coords.env; services left: $(services)"
}

take() {
  [ -f "$WORK/coords.env" ] || { echo "run preflight first" >&2; exit 1; }
  . "$WORK/coords.env"

  step "opening the app before the recording starts"
  restart_app
  wait_for_list || exit 1
  sleep 3

  $ADB shell settings put system show_touches 1
  $ADB shell rm -f /sdcard/fgs.mp4
  step "recording"
  $ADB shell screenrecord --bit-rate 8000000 --time-limit 45 /sdcard/fgs.mp4 >/dev/null 2>&1 &
  rec=$!

  sleep 5                                       # 0-5   Home, live matches
  $ADB shell input tap "$BX" "$BY"              # 5     follow this match
  sleep 5                                       # 5-10  the bell fills
  $ADB shell input keyevent 3                   # 10    leave the app
  sleep 3
  $ADB shell cmd statusbar expand-notifications  # 13    the service's notification
  sleep 15                                      # 13-28 card on screen, readable
  $ADB shell input tap "$SX" "$SY"              # 28    Stop
  sleep 6                                       # 28-34 card gone, service stopped
  $ADB shell cmd statusbar collapse
  sleep 8

  $ADB shell settings put system show_touches 0
  wait $rec 2>/dev/null
  sleep 3
  $ADB pull /sdcard/fgs.mp4 "$OUT_DIR/fgs-datasync.mp4" >/dev/null 2>&1
  step "$OUT_DIR/fgs-datasync.mp4 — $(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$OUT_DIR/fgs-datasync.mp4")s"
  step "services left: $(services)"
}

case "${1:-}" in
  preflight) preflight ;;
  take) take ;;
  *) echo "usage: $0 preflight|take" >&2; exit 2 ;;
esac
