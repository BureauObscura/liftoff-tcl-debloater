#!/usr/bin/env bash
# screenshot.sh [out.png] — grab what's on the TV screen. Video apps show black (DRM).
# Saves on the TV first and pulls it, because some MediaTek TVs print junk into `exec-out screencap`.
source "$(dirname "$0")/lib.sh"
need_tv
out="${1:-$ROOT/state/screen-$(date +%H%M%S).png}"; mkdir -p "$(dirname "$out")"
sh_tv screencap -p /sdcard/liftoff-shot.png >/dev/null
adbtv pull /sdcard/liftoff-shot.png "$out" >/dev/null 2>&1; sh_tv rm -f /sdcard/liftoff-shot.png
echo "$out"
