# Shared helpers, sourced by every script in scripts/. Not meant to be run on its own.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="${LIFTOFF_CONFIG:-$ROOT/liftoff.json}"
STATE="$ROOT/state"            # backups and change records, one folder per TV (gitignored)
TOOLCHAIN="$ROOT/.toolchain"   # JDK + Android build tools from scripts/setup-toolchain.sh (gitignored)

# adb: on PATH, then the Android SDK, then our own toolchain.
ADB="$(command -v adb || true)"
for c in "${ANDROID_HOME:-/nonexistent}/platform-tools/adb" "$TOOLCHAIN/sdk/platform-tools/adb"; do
  [ -n "$ADB" ] || { [ -x "$c" ] && ADB="$c"; }
done

# conf <python expression on c>  — read a value from liftoff.json, prints nothing if missing.
conf() {
  [ -f "$CONFIG" ] || return 0
  python3 -I -c 'import json,sys
c=json.load(open(sys.argv[1]))
try: v=eval(sys.argv[2],{"c":c})
except Exception: v=None
if v is None: pass
elif isinstance(v,bool): print("true" if v else "false")
elif isinstance(v,list): print("\n".join(str(x) for x in v))
else: print(v)' "$CONFIG" "$1"
}

# The TV's address: $TV, else "tv" in liftoff.json. A bare IP gets :5555.
TV="${TV:-$(conf 'c.get("tv")')}"
[ -z "$TV" ] || [[ "$TV" == *:* ]] || TV="$TV:5555"

need_adb() { [ -n "$ADB" ] || die "adb not found. Run scripts/setup-toolchain.sh, or install Android platform-tools."; }
need_tv() {
  need_adb
  [ -n "$TV" ] || die "No TV address. Put \"tv\": \"<ip>:5555\" in liftoff.json, or run scripts/find-tv.sh."
  "$ADB" connect "$TV" >/dev/null 2>&1 || true
  local s; s="$("$ADB" -s "$TV" get-state 2>/dev/null || true)"
  [ "$s" = device ] || die "Can't reach $TV over adb (state: ${s:-none}). Is Developer options > USB debugging on, and did you press Allow on the TV?"
}
adbtv() { "$ADB" -s "$TV" "$@" </dev/null; }
sh_tv() { "$ADB" -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
serial() { sh_tv getprop ro.serialno; }
tvdir() { local d="$STATE/$(serial)"; mkdir -p "$d"; echo "$d"; }
say() { printf '\033[1;36m›\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m✗\033[0m %s\n' "$*" >&2; exit 1; }

# Packages that no script here will ever disable: Play Services, Play Store, keyboards, input
# methods, TV inputs/HDMI, remote pairing, Bluetooth, Settings, system UI and providers.
PROTECTED_RE='^(android|com\.android\.(systemui|tv\.settings.*|providers\..*|bluetooth.*|inputdevices|settings.*|shell|phone|externalstorage|packageinstaller|permissioncontroller|networkstack.*|wifi.*)|com\.google\.android\.(gms.*|gsf.*|inputmethod\..*|leanback\.ime|tv\.remote\.service|ext\..*|permissioncontroller|packageinstaller|networkstack.*|tvrecommendations|onetimeinitializer|partnersetup|apps\.tv\.dreamx)|com\.android\.vending|.*\.(ime|inputmethod|keyboard)(\..*)?|.*tvinput.*|com\.mediatek\.(wwtv.*|tv.*|Airplay.*)|com\.realtek\.airplay2|com\.tcl\.(airplay2|settings|tvinput|initsetup|suspension|factory\..*|tv|tcl_bt_rcu_service|micmanager|externaldevice\.update|UpdatePeripheral))$'
protected() { [[ "$1" =~ $PROTECTED_RE ]]; }
