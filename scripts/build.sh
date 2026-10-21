#!/usr/bin/env bash
# build.sh — build build/Liftoff.apk from app/ and your liftoff.json. Plain Android build tools, no Gradle.
# Needs JDK 17+ and an Android SDK with build-tools and a platform >= 30 (scripts/setup-toolchain.sh fetches both).
source "$(dirname "$0")/lib.sh"
[ -f "$CONFIG" ] || die "No liftoff.json. Copy liftoff.example.json to liftoff.json and edit it."
python3 -I -c 'import json,sys; json.load(open(sys.argv[1]))' "$CONFIG" || die "liftoff.json isn't valid JSON."

# Java
JH="${JAVA_HOME:-}"
for c in "$TOOLCHAIN"/jdk*/Contents/Home "$TOOLCHAIN"/jdk*; do [ -n "$JH" ] || { [ -x "$c/bin/javac" ] && JH="$c"; }; done
[ -n "$JH" ] || JH="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
[ -n "$JH" ] && [ -x "$JH/bin/javac" ] || die "No JDK found. Run scripts/setup-toolchain.sh, or set JAVA_HOME to a JDK 17+."
export JAVA_HOME="$JH" PATH="$JH/bin:$PATH"

# Android SDK
SDK=""
for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$TOOLCHAIN/sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  [ -z "$SDK" ] && [ -n "$c" ] && [ -d "$c/build-tools" ] && SDK="$c"
done
[ -n "$SDK" ] || die "No Android SDK found. Run scripts/setup-toolchain.sh, or set ANDROID_HOME."
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
JAR="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar"
[ -f "$JAR" ] || die "No Android platform in $SDK/platforms. Install one: sdkmanager \"platforms;android-30\""

OUT="$ROOT/build"; rm -rf "$OUT"; mkdir -p "$OUT"/{classes,dex,gen,res}
say "Staging resources and your config"
cp -R "$ROOT/app/res" "$OUT/res-src"; cp -R "$ROOT/app/assets" "$OUT/assets"
# The app only needs these keys; the TV address and tweaks stay on your computer.
python3 -I - "$CONFIG" "$OUT/assets/liftoff.json" <<'PY'
import json, sys
c = json.load(open(sys.argv[1]))
keep = {k: c[k] for k in ("apps", "weather", "nowPlaying", "lights", "background", "clock") if k in c}
json.dump(keep, open(sys.argv[2], "w"), indent=1)
PY
img="$(conf 'c.get("background",{}).get("image")')"
if [ -n "$img" ]; then
  case "$img" in /*) ;; *) img="$ROOT/$img";; esac
  [ -f "$img" ] || die "background.image not found: $img"
  ext="$(echo "${img##*.}" | tr A-Z a-z)"; [ "$ext" = jpeg ] && ext=jpg
  [ "$ext" = png ] || [ "$ext" = jpg ] || die "background.image must be a .png or .jpg"
  rm -f "$OUT"/res-src/drawable-nodpi/home_bg.*; cp "$img" "$OUT/res-src/drawable-nodpi/home_bg.$ext"
  say "Background image: $(basename "$img")"
fi

say "Compiling (build-tools $(basename "$BT"), $(basename "$(dirname "$JAR")"))"
"$BT/aapt2" compile --dir "$OUT/res-src" -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$ROOT/app/AndroidManifest.xml" -A "$OUT/assets" \
  -o "$OUT/unsigned.apk" --java "$OUT/gen" "$OUT/res/res.zip"
STUBS=""; [ -f "$BT/core-lambda-stubs.jar" ] && STUBS=":$BT/core-lambda-stubs.jar"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR$STUBS" -classpath "$JAR" \
  -d "$OUT/classes" $(find "$ROOT/app/src" "$OUT/gen" -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 28 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/unsigned.apk" "$OUT/with-dex.apk"; (cd "$OUT/dex" && zip -q ../with-dex.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/with-dex.apk" "$OUT/aligned.apk"

# A signing key made once on this computer. Keep it: Android only accepts updates signed with the same key.
KS="$ROOT/.keystore/liftoff.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass liftoff -keypass liftoff -alias liftoff -keyalg RSA \
    -keysize 2048 -validity 36500 -dname "CN=Liftoff local build" >/dev/null 2>&1
  say "Made a signing key at .keystore/liftoff.keystore (keep it for future updates)"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:liftoff --out "$OUT/Liftoff.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/Liftoff.apk"
rm -f "$OUT"/Liftoff.apk.idsig
say "Built build/Liftoff.apk ($(du -h "$OUT/Liftoff.apk" | cut -f1 | tr -d ' '))"
