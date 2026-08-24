#!/usr/bin/env bash
# Starts every config in the matrix with the real sing-box built from libcore/singbox-fork.
#
# Parsing a config does not prove the core will run it. `detour to an empty direct outbound
# makes no sense` is raised when transports start, not when the JSON is read, and it shipped
# because nothing here ever ran the core. `sing-box check` does not catch it either.
#
#   cd libcore/singbox-fork && go build -tags "with_gvisor,with_quic,with_utls,with_clash_api" \
#       -o /tmp/sing-box ./cmd/sing-box
#   ./gradlew :app:testDebugUnitTest --tests '*ConfigMatrixDumpTest*'
#   tools/validate-configs.sh /tmp/sing-box
set -u

SB="${1:?usage: validate-configs.sh <sing-box binary> [config dir]}"
DIR="${2:-app/build/config-matrix}"
# Eight cores starting at once on a busy machine do not all finish inside a short
# cap, which showed up as failures that passed when re-run alone.
JOBS="${JOBS:-4}"
CAP="${CAP:-8}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# python here may be a native Windows build, which cannot open msys-style paths.
winpath() { command -v cygpath >/dev/null 2>&1 && cygpath -w "$1" || printf '%s' "$1"; }

# The tun inbound needs a device and privileges the validator does not have. Swapping it for
# a loopback listener leaves dns, outbounds and route — where the bugs were — untouched.
python -c "
import json, os, sys, glob
src_dir, out_dir = sys.argv[1], sys.argv[2]
for path in glob.glob(os.path.join(src_dir, '*.json')):
    cfg = json.load(open(path, encoding='utf-8'))
    cfg['inbounds'] = [{'type': 'mixed', 'tag': 'in', 'listen': '127.0.0.1', 'listen_port': 0}]
    json.dump(cfg, open(os.path.join(out_dir, os.path.basename(path)), 'w', encoding='utf-8'))
" "$(winpath "$DIR")" "$(winpath "$WORK")" || { echo "failed to prepare configs"; exit 1; }

SB_W="$(winpath "$SB")"
export SB_W CAP
export RESULTS="$WORK/results"
mkdir -p "$RESULTS"

run_one() {
  cfg="$1"
  name="$(basename "$cfg" .json)"
  win_cfg="$(command -v cygpath >/dev/null 2>&1 && cygpath -w "$cfg" || printf '%s' "$cfg")"

  case "$name" in
    # Remote rule sets are fetched over the network at start; validate their schema only.
    *--rs--*) out="$("$SB_W" check -c "$win_cfg" 2>&1)"; rc=$?; mode=check ;;
    *)
      out="$(timeout "$CAP" "$SB_W" run -c "$win_cfg" 2>&1)"; rc=$?
      # 124 means the timeout killed a core that was running happily: the success case.
      [ $rc -eq 124 ] && rc=0
      mode=run
      ;;
  esac

  if [ $rc -ne 0 ] || printf '%s' "$out" | grep -qiE "FATAL|start service:"; then
    {
      printf 'FAIL [%s] %s\n' "$mode" "$name"
      printf '%s' "$out" | grep -iE 'FATAL|ERROR|start service:' | head -2 | sed 's/^/       /'
    } > "$RESULTS/$name.fail"
  else
    : > "$RESULTS/$name.ok"
  fi
}
export -f run_one

find "$WORK" -maxdepth 1 -name '*.json' -print0 | xargs -0 -P "$JOBS" -I{} bash -c 'run_one "$@"' _ {}

pass=$(find "$RESULTS" -name '*.ok' | wc -l)
fail=$(find "$RESULTS" -name '*.fail' | wc -l)
echo "passed: $pass   failed: $fail"
if [ "$fail" -gt 0 ]; then
  cat "$RESULTS"/*.fail
  exit 1
fi
