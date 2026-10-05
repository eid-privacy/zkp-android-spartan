#!/usr/bin/env bash
# Compile the benchmark circuits in the spartan-backend submodule and generate
# their witnesses (target/<circuit>.json and target/<circuit>.gz). Keep the
# list in sync with BENCHMARK_CIRCUITS in MainActivity.kt.
#
# The submodule does not version circuits/*/target/ anymore, so a fresh
# checkout has no circuit artifacts. Runs inside the submodule's own devbox
# environment, which pins the matching nargo-t256.
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
if [ "${1:-}" != "--inner" ]; then
  cd "$(dirname "$SCRIPT")/../app/src/main/assets/spartan-backend"
  exec devbox run -- bash "$SCRIPT" --inner
fi

for c in c0200_swiyu_jwt c0202_sicpa_backend_constant; do
  (cd "circuits/$c" && nargo-t256 compile && nargo-t256 execute)
done
