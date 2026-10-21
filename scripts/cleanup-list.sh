#!/usr/bin/env bash
# cleanup-list.sh — show what could be disabled on this TV, from catalog/debloat.tsv, plus any other
# preinstalled apps that aren't in your liftoff.json. Changes nothing. Show this to the user and wait for OK.
source "$(dirname "$0")/lib.sh"
need_tv
installed="$(sh_tv pm list packages | sed 's/^package://' | sort)"
disabled="$(sh_tv pm list packages -d | sed 's/^package://' | sort)"
keep="$(conf '[a["package"] for a in c.get("apps",[])]' ; conf '(c.get("nowPlaying") or {}).get("packages")')"
printf '%-11s %-9s %-34s %-48s %s\n' CATEGORY RISK APP PACKAGE WHY
while IFS=$'\t' read -r pkg name cat risk why; do
  [ "$pkg" = package ] && continue
  grep -qx "$pkg" <<<"$installed" || continue
  grep -qx "$pkg" <<<"$disabled" && continue
  grep -qx "$pkg" <<<"$keep" && continue
  printf '%-11s %-9s %-34s %-48s %s\n' "$cat" "$risk" "$name" "$pkg" "$why"
done < "$ROOT/catalog/debloat.tsv"
echo
echo "Other apps you installed or that came preinstalled (not in the catalog, not in your apps list):"
known="$(cut -f1 "$ROOT/catalog/debloat.tsv")"
for p in $(sh_tv pm list packages -3 -e | sed 's/^package://' | sort); do
  grep -qx "$p" <<<"$known" || grep -qx "$p" <<<"$keep" || [ "$p" = app.liftoff.home ] || echo "  $p"
done
