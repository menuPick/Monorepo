#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

DART_DEFINES=()
if [[ -n "${API_BASE_URL:-}" ]]; then
  DART_DEFINES+=(--dart-define=API_BASE_URL="$API_BASE_URL")
fi

"$ROOT_DIR/scripts/use_user_web_assets.sh"
cd "$ROOT_DIR"
if (( ${#DART_DEFINES[@]} )); then
  flutter run -d chrome -t lib/main.dart "${DART_DEFINES[@]}"
else
  flutter run -d chrome -t lib/main.dart
fi

