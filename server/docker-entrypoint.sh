#!/usr/bin/env sh
set -e

PORT="${PORT:-8080}"
DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

# IMPORTANT:
# - ADMIN_ID_HASH 환경변수(권장) 또는 컨테이너 내부 파일(ADMIN_ID_HASH_FILE)을 통해 관리자 해시가 제공되어야 합니다.

ARGS="--port=${PORT}"

if [ -n "${DB_URL}" ]; then
  ARGS="$ARGS --db-url=${DB_URL}"
fi

if [ -n "${DB_USER}" ]; then
  ARGS="$ARGS --db-user=${DB_USER}"
fi

# 빈 문자열 비밀번호도 허용 (로컬 개발 등)
ARGS="$ARGS --db-password=${DB_PASSWORD}"

exec java -jar /app/menu-pick-server.jar $ARGS

