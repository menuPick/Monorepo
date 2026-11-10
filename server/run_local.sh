#!/usr/bin/env bash
set -euo pipefail

# 기본값은 프로젝트별 로컬 파일 저장소입니다. 생성되는 data/는 각자의 컴퓨터에 남습니다.
# MySQL을 사용하려면 DB_URL/DB_USER/DB_PASSWORD를 설정하세요.
# 확인 페이지: http://localhost:$PORT/  (Swagger UI: /swagger-ui.html)

cd "$(dirname "$0")"

PORT=${PORT:-18080}
DATA_DIR=${DATA_DIR:-"$(cd .. && pwd)/data"}
# 로컬 개발: 월 1회 추천 제한 끔, 10분 요청 제한 완화, AI 서버(ai/run_local.sh) 자동 연결.
MONTHLY_RECOMMENDATION_LIMIT=${MONTHLY_RECOMMENDATION_LIMIT:-false}
RECOMMENDATION_RATE_LIMIT=${RECOMMENDATION_RATE_LIMIT:-100}
AI_BASE_URL=${AI_BASE_URL:-http://127.0.0.1:18090}
# 로컬 관리자 로그인 ID: test (ADMIN_ID_HASH가 있으면 그쪽이 우선)
ADMIN_ID=${ADMIN_ID:-test}
export PORT DATA_DIR MONTHLY_RECOMMENDATION_LIMIT RECOMMENDATION_RATE_LIMIT AI_BASE_URL ADMIN_ID

echo "[INFO] MenuPick server starting: http://localhost:$PORT  storage=$DATA_DIR  monthlyLimit=$MONTHLY_RECOMMENDATION_LIMIT  ai=$AI_BASE_URL"

exec ./mvnw -q spring-boot:run
