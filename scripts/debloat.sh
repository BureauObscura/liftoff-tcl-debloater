#!/usr/bin/env bash
# debloat.sh <package>... — DISABLE (never uninstall) the given packages, recording each one so undo.sh
# can bring it back. Refuses protected system packages. Risky ones (see catalog) need --risky.
#   scripts/debloat.sh com.tcl.messagebox tv.samba.ssm.ui
#   scripts/debloat.sh --risky com.google.android.apps.tv.launcherx
source "$(dirname "$0")/lib.sh"
risky_ok=0; [ "${1:-}" = --risky ] && { risky_ok=1; shift; }
[ $# -gt 0 ] || die "usage: scripts/debloat.sh [--risky] <package>..."
need_tv
D="$(tvdir)"
ls -d "$D"/backup-* >/dev/null 2>&1 || die "No backup for this TV yet. Run scripts/backup.sh first."
REC="$D/disabled.txt"; touch "$REC"
home="$(sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
for p in "$@"; do
  if protected "$p"; then warn "Skipping $p: protected (Play Services, Play Store, keyboard, inputs, Settings or system)."; continue; fi
  if ! sh_tv pm list packages "$p" | grep -qx "package:$p"; then warn "Skipping $p: not installed."; continue; fi
  if sh_tv pm list packages -d | grep -qx "package:$p"; then say "$p is already disabled."; continue; fi
  risk="$(awk -F'\t' -v p="$p" '$1==p {print $4}' "$ROOT/catalog/debloat.tsv")"
  if [ "$risk" = risky ] && [ $risky_ok = 0 ]; then
    warn "Skipping $p: risky — $(awk -F'\t' -v p="$p" '$1==p {print $5}' "$ROOT/catalog/debloat.tsv") Re-run with --risky only after the user agrees."; continue
  fi
  if [[ "$home" == "$p/"* ]]; then warn "Skipping $p: it is the current Home app. Run scripts/install.sh first."; continue; fi
  if sh_tv pm disable-user --user 0 "$p" | grep -q 'new state: disabled'; then
    grep -qx "$p" "$REC" || echo "$p" >> "$REC"; say "Disabled $p"
  else warn "Couldn't disable $p (the firmware may block it)."; fi
done
