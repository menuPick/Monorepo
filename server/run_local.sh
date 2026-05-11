#!/usr/bin/env bash
set -euo pipefail

# 필요 시 아래 환경변수를 설정해서 실행하세요.
# export DB_URL='jdbc:mysql://localhost:3306/menupick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true'
# export DB_USER='root'
# export DB_PASSWORD='YOUR_PASSWORD'
# export ADMIN_ID_HASH_FILE='secrets/admin_id_hash.txt'

PORT=${PORT:-8080}
DB_URL=${DB_URL:-"jdbc:mysql://localhost:3306/menupick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true"}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-}
export PORT DB_URL DB_USER DB_PASSWORD

echo "[INFO] Starting MenuPickServer"
echo "  PORT=$PORT"
echo "  DB_URL=$DB_URL"
echo "  DB_USER=$DB_USER"
if [[ -z "$DB_PASSWORD" ]]; then
  echo "  DB_PASSWORD=(empty)"
  echo "[WARN] MySQL root 계정에 비밀번호가 설정되어 있으면 실행이 실패합니다."
  echo "       이 경우: export DB_PASSWORD='비밀번호' 후 다시 실행하세요."
else
  echo "  DB_PASSWORD=(set)"
fi

cd "$(dirname "$0")"

# 서버는 DB_URL/DB_USER/DB_PASSWORD를 환경변수로 읽습니다.
# DB 비밀번호를 프로세스 인자에 싣지 않아 ps 목록 노출을 피합니다.
mvn -q exec:java -Dexec.args="--port=$PORT"
