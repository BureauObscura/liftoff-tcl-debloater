#!/usr/bin/env bash
# install.sh — install build/Liftoff.apk, grant what the overlays need, and make it the Home app.
#   --no-home   install and grant, but leave the current Home app alone (for trying it out)
source "$(dirname "$0")/lib.sh"
set_home=1; [ "${1:-}" = --no-home ] && set_home=0
need_tv
APK="$ROOT/build/Liftoff.apk"; [ -f "$APK" ] || "$ROOT/scripts/build.sh"
D="$(tvdir)"; ls -d "$D"/backup-* >/dev/null 2>&1 || die "No backup for this TV yet. Run scripts/backup.sh first."
PKG=app.liftoff.home; SVC="$PKG/$PKG.OverlayService"

[ -f "$D/home-before.txt" ] || sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 > "$D/home-before.txt"
say "Installing Liftoff"
adbtv install -r "$APK" | tail -1
grep -qx "$PKG" "$D/installed.txt" 2>/dev/null || echo "$PKG" >> "$D/installed.txt"

say "Granting overlay and notification access (for weather and Now Playing)"
sh_tv appops set "$PKG" SYSTEM_ALERT_WINDOW allow
sh_tv appops set "$PKG" APP_AUTO_START allow 2>/dev/null || true   # TCL firmware: without it the listener never binds
# After every install -r the listener has to be re-bound, so toggle it.
sh_tv cmd notification disallow_listener "$SVC" >/dev/null 2>&1 || true
sh_tv cmd notification allow_listener "$SVC"

if [ $set_home = 1 ]; then
  sh_tv cmd package set-home-activity "$PKG/.HomeActivity" | tail -1
  sh_tv input keyevent KEYCODE_HOME
  say "Liftoff is the Home app. Press Home on the remote."
else
  sh_tv am start -n "$PKG/.HomeActivity" >/dev/null
  say "Installed without changing Home. Opened it once so you can look."
fi
