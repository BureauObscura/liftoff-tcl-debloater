#!/usr/bin/env bash
# tweaks.sh — apply the "tweaks" from liftoff.json, recording every old value for undo.sh:
#   animationScale      window/transition/animator speed (0.5 = snappier menus)
#   privateDns          Private DNS hostname, e.g. dns.adguard-dns.com (blocks ad and tracker domains)
#   tclPrivacySwitches  TCL only: turn off viewing-data sharing and diagnostics
#   noBackground        packages that may not run in the background (e.g. YouTube)
source "$(dirname "$0")/lib.sh"
need_tv
D="$(tvdir)"; ls -d "$D"/backup-* >/dev/null 2>&1 || die "No backup for this TV yet. Run scripts/backup.sh first."
SREC="$D/settings.tsv"; AREC="$D/appops.tsv"; touch "$SREC" "$AREC"

# put <namespace> <key> <value>: remember the value from before Liftoff's first change, then set it.
put() {
  local old; old="$(sh_tv settings get "$1" "$2")"
  grep -q "^$1	$2	" "$SREC" || printf '%s\t%s\t%s\n' "$1" "$2" "$old" >> "$SREC"
  sh_tv settings put "$1" "$2" "$3" >/dev/null; say "$1 $2: $old → $3"
}

a="$(conf 'c.get("tweaks",{}).get("animationScale")')"
if [ -n "$a" ]; then for k in window_animation_scale transition_animation_scale animator_duration_scale; do put global "$k" "$a"; done; fi

dns="$(conf 'c.get("tweaks",{}).get("privateDns")')"
if [ -n "$dns" ]; then put global private_dns_mode hostname; put global private_dns_specifier "$dns"; fi

if [ "$(conf 'c.get("tweaks",{}).get("tclPrivacySwitches")')" = true ]; then
  if [ "$(sh_tv getprop ro.product.manufacturer | tr A-Z a-z)" = tcl ] || sh_tv pm list packages | grep -q '^package:com\.tcl\.'; then
    put global tcl_app_ssm_switch 0; put global tcl_diagnostic_status 0
  else say "Not a TCL TV, skipping TCL privacy switches"; fi
fi

for p in $(conf 'c.get("tweaks",{}).get("noBackground")'); do
  sh_tv pm list packages "$p" | grep -qx "package:$p" || continue
  old="$(sh_tv cmd appops get "$p" RUN_ANY_IN_BACKGROUND | sed -n 's/.*: \([a-z]*\).*/\1/p' | head -1)"
  grep -q "^$p	" "$AREC" || printf '%s\tRUN_ANY_IN_BACKGROUND\t%s\n' "$p" "${old:-allow}" >> "$AREC"
  sh_tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore; say "$p may no longer run in the background"
done
