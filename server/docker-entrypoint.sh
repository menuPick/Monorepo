#!/usr/bin/env sh
set -e

PORT="${PORT:-80}"
DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_PROTOCOL="${DB_PROTOCOL:-mysql}" # mysql | mariadb

# Railway MySQL 플러그인(또는 유사 환경) 호환:
# DB_URL을 직접 주지 않아도 MYSQLHOST/MYSQLPORT/MYSQLDATABASE/... 로부터 조합합니다.
if [ -z "${DB_URL}" ] && [ -n "${MYSQLHOST:-}" ]; then
  MYSQL_PORT="${MYSQLPORT:-3306}"
  MYSQL_DB="${MYSQLDATABASE:-railway}"
  DB_URL="jdbc:mysql://${MYSQLHOST}:${MYSQL_PORT}/${MYSQL_DB}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC"

  if [ "${DB_USER}" = "root" ] && [ -n "${MYSQLUSER:-}" ]; then
    DB_USER="${MYSQLUSER}"
  fi
  if [ -z "${DB_PASSWORD}" ] && [ -n "${MYSQLPASSWORD:-}" ]; then
    DB_PASSWORD="${MYSQLPASSWORD}"
  fi
fi

# Cloudtype/일반 PaaS 호환:
# 아래 변수들 중 하나 세트가 있는 경우 DB_URL을 조합합니다.
# - DB_HOST/DB_PORT/DB_DATABASE 또는 DB_USERNAME/DB_PASSWORD
# - DB_url/DB_port/DB_dataBaseName 또는 DB_name/DB_password (일부 가이드에서 쓰는 형태)
if [ -z "${DB_URL}" ]; then
  DB_HOST_FALLBACK="${DB_HOST:-${DB_url:-}}"
  DB_PORT_FALLBACK="${DB_PORT:-${DB_port:-}}"
  DB_NAME_FALLBACK="${DB_DATABASE:-${DB_dataBaseName:-}}"

  if [ -n "${DB_HOST_FALLBACK}" ]; then
    DB_PORT_FALLBACK="${DB_PORT_FALLBACK:-3306}"
    DB_NAME_FALLBACK="${DB_NAME_FALLBACK:-menupick}"

    if [ "${DB_PROTOCOL}" = "mariadb" ]; then
      DB_URL="jdbc:mariadb://${DB_HOST_FALLBACK}:${DB_PORT_FALLBACK}/${DB_NAME_FALLBACK}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    else
      DB_URL="jdbc:mysql://${DB_HOST_FALLBACK}:${DB_PORT_FALLBACK}/${DB_NAME_FALLBACK}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    fi

    # 사용자/비밀번호 변수명이 다른 경우 자동 매핑
    if [ "${DB_USER}" = "root" ] && [ -n "${DB_USERNAME:-}" ]; then
      DB_USER="${DB_USERNAME}"
    fi
    if [ "${DB_USER}" = "root" ] && [ -n "${DB_name:-}" ]; then
      DB_USER="${DB_name}"
    fi

    if [ -z "${DB_PASSWORD}" ] && [ -n "${DB_password:-}" ]; then
      DB_PASSWORD="${DB_password}"
    fi
  fi
fi

# IMPORTANT:
# - ADMIN_ID_HASH 환경변수(권장) 또는 컨테이너 내부 파일(ADMIN_ID_HASH_FILE)을 통해 관리자 해시가 제공되어야 합니다.

if [ -z "${ADMIN_ID_HASH:-}" ] && [ -z "${ADMIN_ID_HASH_FILE:-}" ]; then
  echo "[ERROR] ADMIN_ID_HASH (권장) 또는 ADMIN_ID_HASH_FILE 환경변수가 필요합니다." >&2
  exit 1
fi

export DB_URL DB_USER DB_PASSWORD

# DB 접속 정보는 환경변수로 전달해 프로세스 목록에 비밀번호가 노출되지 않게 합니다.
exec java -jar /app/menu-pick-server.jar "--port=${PORT}"
