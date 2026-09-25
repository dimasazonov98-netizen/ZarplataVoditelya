#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/assets app/src/main/res/drawable
cat scripts/assets/bg.part1 scripts/assets/bg.part2 scripts/assets/bg.part3 scripts/assets/bg.part4 scripts/assets/bg.part5 | tr -d '\n\r ' | base64 -d > app/src/main/assets/bg-car-premium.webp
tr -d '\n\r ' < scripts/assets/icon.b64 | base64 -d > app/src/main/res/drawable/ic_launcher_luxe.webp
test -s app/src/main/assets/bg-car-premium.webp
test -s app/src/main/res/drawable/ic_launcher_luxe.webp
