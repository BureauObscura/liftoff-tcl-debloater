#!/usr/bin/env bash
# key.sh <key>... — press remote buttons: home back ok up down left right play vol+ vol- mute sleep wake
source "$(dirname "$0")/lib.sh"
need_tv
for k in "$@"; do
  case "$k" in
    home) c=HOME;; back) c=BACK;; ok|enter|select) c=DPAD_CENTER;; up|down|left|right) c="DPAD_$(echo "$k" | tr a-z A-Z)";;
    play|pause) c=MEDIA_PLAY_PAUSE;; vol+) c=VOLUME_UP;; vol-) c=VOLUME_DOWN;; mute) c=VOLUME_MUTE;;
    sleep|off) c=SLEEP;; wake|on) c=WAKEUP;; *) c="$(echo "$k" | tr a-z A-Z)";;
  esac
  sh_tv input keyevent "KEYCODE_$c"; sleep 0.3
done
