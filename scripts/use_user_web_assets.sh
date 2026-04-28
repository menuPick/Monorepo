#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cp "$ROOT_DIR/web/index.user.html" "$ROOT_DIR/web/index.html"
cp "$ROOT_DIR/web/manifest.user.json" "$ROOT_DIR/web/manifest.json"

echo "[OK] Switched web assets to USER (web/index.html, web/manifest.json)"

