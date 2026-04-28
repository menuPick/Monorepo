#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

DART_DEFINES=()
if [[ -n "${API_BASE_URL:-}" ]]; then
  DART_DEFINES+=(--dart-define=API_BASE_URL="$API_BASE_URL")
fi

"$ROOT_DIR/scripts/use_user_web_assets.sh"
cd "$ROOT_DIR"
flutter build web -t lib/main.dart --release "${DART_DEFINES[@]}"

echo "[OK] build/web generated for USER"

