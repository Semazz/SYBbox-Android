#!/usr/bin/env bash
# Builds every config in the matrix with the real Xray core.
#
# Parsing a config does not prove the core will run it. Transports and outbounds are
# constructed when the instance is built, not when the JSON is read, so a config that
# loads can still fail the moment it starts. This builds each one for real.
#
#   ./gradlew :app:testDebugUnitTest --tests '*ConfigMatrixDumpTest*'
#   tools/validate-configs.sh
set -eu

DIR="${1:-app/build/config-matrix}"
ASSETS="${XRAY_ASSETS:-app/src/main/assets}"

if [ ! -d "$DIR" ]; then
    echo "no configs in $DIR — run the matrix dump test first:"
    echo "  ./gradlew :app:testDebugUnitTest --tests '*ConfigMatrixDumpTest*'"
    exit 2
fi

BIN="$(mktemp -d)/validate"
trap 'rm -rf "$(dirname "$BIN")"' EXIT

echo "building the validator"
(cd libcore && go build -o "$BIN" ./cmd/validate)

if [ -f "$ASSETS/geoip.dat" ]; then
    XRAY_LOCATION_ASSET="$(cd "$ASSETS" && pwd)"
    export XRAY_LOCATION_ASSET
    echo "geo data: $XRAY_LOCATION_ASSET"
else
    echo "warning: no geo data in $ASSETS, geo rules will fail"
fi

exec "$BIN" "$DIR"
