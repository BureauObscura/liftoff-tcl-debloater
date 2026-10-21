#!/usr/bin/env bash
# undo.sh — put the TV back the way it was before Liftoff: re-enable every package Liftoff disabled,
# restore every setting and app-op to its recorded old value, give Home back, and uninstall Liftoff.
# Safe to run more than once.
source "$(dirname "$0")/lib.sh"
need_tv
D="$STATE/$(serial)"; [ -d "$D" ] || die "Nothing recorded for this TV (no state/$(serial)). Nothing to undo."

if [ -f "$D/disabled.txt" ]; then
  while read -r p; do [ -n "$p" ] && sh_tv pm enable --user 0 "$p" | tail -1; done < "$D/disabled.txt"
fi
if [ -f "$D/settings.tsv" ]; then
  while IFS=$'\t' read -r ns k old; do
    if [ "$old" = null ] || [ -z "$old" ]; then sh_tv settings delete "$ns" "$k" >/dev/null; else sh_tv settings put "$ns" "$k" "$old"; fi
    say "$ns $k → ${old:-unset}"
  done < "$D/settings.tsv"
fi
if [ -f "$D/appops.tsv" ]; then
  while IFS=$'\t' read -r p op old; do sh_tv cmd appops set "$p" "$op" "$old"; say "$p $op → $old"; done < "$D/appops.tsv"
fi
if [ -f "$D/home-before.txt" ] && [ -s "$D/home-before.txt" ]; then
  h="$(cat "$D/home-before.txt")"; sh_tv cmd package set-home-activity "$h" | tail -1; say "Home → $h"
fi
if [ -f "$D/installed.txt" ]; then
  while read -r p; do [ -n "$p" ] && { adbtv uninstall "$p" | tail -1; }; done < "$D/installed.txt"
fi
for f in disabled.txt settings.tsv appops.tsv home-before.txt installed.txt; do [ -f "$D/$f" ] && mv "$D/$f" "$D/$f.undone-$(date +%Y%m%d-%H%M%S)"; done
sh_tv input keyevent KEYCODE_HOME
say "Undone. Backups are still in state/$(basename "$D")/."
