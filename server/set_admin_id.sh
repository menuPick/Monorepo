#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SECRETS_DIR="$ROOT_DIR/secrets"
OUT_FILE="$SECRETS_DIR/admin_id_hash.txt"

mkdir -p "$SECRETS_DIR"

ADMIN_ID="${1:-}"
if [[ -z "$ADMIN_ID" ]]; then
  echo -n "Enter new admin id: "
  IFS= read -r ADMIN_ID
fi

if [[ -z "$ADMIN_ID" ]]; then
  echo "[ERROR] admin id is empty"
  exit 1
fi

HASH=$(printf "%s" "$ADMIN_ID" | shasum -a 256 | awk '{print $1}')

printf "%s\n" "$HASH" > "$OUT_FILE"

echo "[OK] admin id hash saved: $OUT_FILE"
echo "     (For cloud deploy, set env: ADMIN_ID_HASH=$HASH)"

