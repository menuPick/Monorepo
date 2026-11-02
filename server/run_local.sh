#!/usr/bin/env bash
set -euo pipefail

# 기본값은 프로젝트별 로컬 파일 저장소입니다. 생성되는 data/는 각자의 컴퓨터에 남습니다.
# MySQL을 사용하려면 DB_URL/DB_USER/DB_PASSWORD를 설정하세요.

PORT=${PORT:-18080}
DATA_DIR=${DATA_DIR:-"$(cd "$(dirname "$0")/.." && pwd)/data"}
export PORT DATA_DIR

echo "[INFO] MenuPick server starting: port=$PORT, storage=$DATA_DIR"

cd "$(dirname "$0")"

mvn -q exec:java -Dexec.args="--port=$PORT"
