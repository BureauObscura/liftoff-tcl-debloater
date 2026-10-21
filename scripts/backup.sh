#!/usr/bin/env bash
# backup.sh — snapshot the TV before changing anything: every package and its state, all settings,
# and the current Home app. Saved to state/<tv-serial>/backup-<time>/; the first one is also kept as original/.
source "$(dirname "$0")/lib.sh"
need_tv
D="$(tvdir)"; B="$D/backup-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$B"
say "Backing up $TV to ${B#$ROOT/}"
sh_tv 'echo "$(getprop ro.product.manufacturer) $(getprop ro.product.model) / Android $(getprop ro.build.version.release) / $(getprop ro.build.fingerprint)"' > "$B/device.txt"
sh_tv pm list packages -f   > "$B/packages-all.txt"
sh_tv pm list packages -d   > "$B/packages-disabled.txt"
sh_tv pm list packages -3   > "$B/packages-user.txt"
for ns in global secure system; do sh_tv settings list "$ns" > "$B/settings-$ns.txt"; done
sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 > "$B/home.txt"
[ -d "$D/original" ] || { cp -R "$B" "$D/original"; say "Kept as the original snapshot too (state/$(basename "$D")/original)"; }
say "$(wc -l < "$B/packages-all.txt" | tr -d ' ') packages, $(wc -l < "$B/packages-disabled.txt" | tr -d ' ') already disabled, Home is $(cat "$B/home.txt")"
