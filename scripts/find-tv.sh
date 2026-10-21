#!/usr/bin/env bash
# find-tv.sh — look for TVs on your home network that accept adb (port 5555) and print their model.
# Only scans your own /24 subnet. Usage: scripts/find-tv.sh [subnet-prefix like 192.168.1]
source "$(dirname "$0")/lib.sh"
need_adb
prefix="${1:-}"
if [ -z "$prefix" ]; then
  ip="$( (ipconfig getifaddr en0 || ipconfig getifaddr en1 || hostname -I 2>/dev/null | awk '{print $1}') 2>/dev/null | head -1)"
  [ -n "$ip" ] || die "Couldn't work out your network. Pass it: scripts/find-tv.sh 192.168.1"
  prefix="${ip%.*}"
fi
say "Scanning $prefix.1-254 for adb (port 5555)…"
hosts="$(python3 -I - "$prefix" <<'PY'
import socket, sys
from concurrent.futures import ThreadPoolExecutor
def probe(i):
    h = f"{sys.argv[1]}.{i}"
    try:
        with socket.create_connection((h, 5555), timeout=0.6): return h
    except OSError: return None
with ThreadPoolExecutor(64) as ex:
    for h in ex.map(probe, range(1, 255)):
        if h: print(h)
PY
)"
[ -n "$hosts" ] || die "No TV answered on port 5555. Turn on Settings > System > Developer options > USB debugging (tap Settings > System > About > Android TV OS build 7 times to unlock it), and make sure the TV and this computer are on the same network."
for h in $hosts; do
  "$ADB" connect "$h:5555" >/dev/null 2>&1 || true
  state="$("$ADB" -s "$h:5555" get-state 2>/dev/null || true)"
  if [ "$state" = device ]; then
    m="$("$ADB" -s "$h:5555" shell 'echo "$(getprop ro.product.manufacturer) $(getprop ro.product.model), Android $(getprop ro.build.version.release)"' | tr -d '\r')"
    echo "  $h:5555   $m"
  else
    echo "  $h:5555   waiting — press Allow on the TV's \"Allow USB debugging?\" prompt, then run this again"
  fi
done
