#!/usr/bin/env bash
# youtube.sh <youtube-url-or-id> — play a YouTube video on the TV, and press OK on the
# "Who's watching?" picker if it shows up.
source "$(dirname "$0")/lib.sh"
need_tv
id="$(echo "${1:?usage: scripts/youtube.sh <youtube-url-or-video-id>}" | sed -E 's#.*(v=|youtu\.be/|/shorts/|/live/)##; s#[?&].*##')"
[[ "$id" =~ ^[A-Za-z0-9_-]{11}$ ]] || die "Couldn't read a video id from: $1"
playing() { sh_tv dumpsys media_session | awk '/package=com.google.android.youtube.tv/{f=1} f&&/state=PlaybackState/{print; exit}' | grep -q 'state=3'; }
sh_tv input keyevent KEYCODE_WAKEUP; sleep 1
sh_tv am start -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=$id" com.google.android.youtube.tv >/dev/null
for try in 1 2 3 4 5 6 7 8; do
  sleep 3
  playing && { say "Playing"; exit 0; }
  if (( try % 2 == 0 )); then
    if "$ROOT/scripts/ui.sh" 2>/dev/null | grep -qi "who's watching"; then say "Picking the profile"; sh_tv input keyevent KEYCODE_DPAD_CENTER
    else sh_tv input keyevent KEYCODE_MEDIA_PLAY; fi
  fi
done
die "Couldn't confirm playback."
