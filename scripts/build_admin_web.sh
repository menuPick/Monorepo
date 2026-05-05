#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

DART_DEFINES=()
if [[ -n "${API_BASE_URL:-}" ]]; then
  DART_DEFINES+=(--dart-define=API_BASE_URL="$API_BASE_URL")
fi

BASE_ARGS=()
if [[ -n "${BASE_HREF:-}" ]]; then
  # 예: "/Monorepo/" 또는 "/Monorepo/admin/" (끝에 / 권장)
  BASE_ARGS+=(--base-href "$BASE_HREF")
fi

"$ROOT_DIR/scripts/use_admin_web_assets.sh"
cd "$ROOT_DIR"
if (( ${#DART_DEFINES[@]} )); then
  flutter build web -t lib/admin_main.dart --release "${BASE_ARGS[@]}" "${DART_DEFINES[@]}"
else
  flutter build web -t lib/admin_main.dart --release "${BASE_ARGS[@]}"
fi

# Vercel CLI로 build/web를 직접 배포하는 경우(vercel deploy build/web)
# 이 디렉터리가 프로젝트 루트로 인식되므로, SPA rewrite 설정이 필요합니다.
if [[ -f "$ROOT_DIR/vercel.json" ]]; then
  cp "$ROOT_DIR/vercel.json" "$ROOT_DIR/build/web/vercel.json"
fi

echo "[OK] build/web generated for ADMIN"

