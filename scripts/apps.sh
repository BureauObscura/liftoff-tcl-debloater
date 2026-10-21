#!/usr/bin/env bash
# apps.sh — list every app on the TV that has a launcher entry, so you can match the user's app names
# to package names for liftoff.json. Names for common apps come from catalog/apps.tsv.
source "$(dirname "$0")/lib.sh"
need_tv
pk="$( { sh_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
         sh_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER; } \
       | grep / | sed 's#/.*##; s/^ *//' | sort -u)"
for p in $pk; do
  n="$(awk -F'\t' -v p="$p" '$2==p {print $1}' "$ROOT/catalog/apps.tsv")"
  printf '  %-26s %s\n' "${n:-?}" "$p"
done
