#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/26.1.10909125}"
OUT="${1:-../app/libs/sybbox_core.aar}"
TARGETS="${TARGETS:-android/arm64,android/arm,android/amd64}"

export ANDROID_HOME="$SDK_DIR"
export ANDROID_NDK_HOME="$NDK_DIR"

LDFLAGS="-s -w -buildid= -checklinkname=0"

mkdir -p "$(dirname "$OUT")"

exec gomobile bind -v \
  -target="$TARGETS" \
  -androidapi 24 \
  -javapkg=com.sybbox \
  -o "$OUT" \
  -trimpath \
  -ldflags "$LDFLAGS" \
  ./core
