#!/usr/bin/env bash
# setup-toolchain.sh — download everything needed to build and talk to the TV into .toolchain/
# (about 600 MB): Eclipse Temurin JDK 17, Android command-line tools, platform-tools (adb),
# build-tools 30.0.3 and the Android 30 platform. Nothing is installed system-wide.
# Running sdkmanager accepts Google's Android SDK license; the script shows it to you first.
source "$(dirname "$0")/lib.sh"
mkdir -p "$TOOLCHAIN"; cd "$TOOLCHAIN"
case "$(uname -s)" in Darwin) os=mac; tos=mac;; Linux) os=linux; tos=linux;; *) die "macOS or Linux only (on Windows, use WSL).";; esac
case "$(uname -m)" in arm64|aarch64) arch=aarch64;; x86_64|amd64) arch=x64;; *) die "Unsupported CPU: $(uname -m)";; esac

if ! ls -d jdk*/ >/dev/null 2>&1; then
  say "Downloading JDK 17 (Eclipse Temurin)"
  curl -fL# -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/$os/$arch/jdk/hotspot/normal/eclipse"
  tar xzf jdk.tar.gz && rm jdk.tar.gz
fi
JH="$(ls -d "$TOOLCHAIN"/jdk*/Contents/Home 2>/dev/null || ls -d "$TOOLCHAIN"/jdk*/ | head -1)"
export JAVA_HOME="${JH%/}" PATH="${JH%/}/bin:$PATH"

if [ ! -x sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  say "Downloading Android command-line tools"
  curl -fL# -o cmdline.zip "https://dl.google.com/android/repository/commandlinetools-$tos-11076708_latest.zip"
  mkdir -p sdk/cmdline-tools && unzip -q cmdline.zip -d sdk/cmdline-tools && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest && rm cmdline.zip
fi
SM="sdk/cmdline-tools/latest/bin/sdkmanager"
say "Installing platform-tools, build-tools 30.0.3 and platform 30 (you'll be asked to accept Google's SDK license)"
"$SM" --sdk_root="$TOOLCHAIN/sdk" --licenses
"$SM" --sdk_root="$TOOLCHAIN/sdk" "platform-tools" "build-tools;30.0.3" "platforms;android-30"
say "Toolchain ready in .toolchain/ — scripts/build.sh and the adb scripts will find it."
