# MenuPick Monorepo

Flutter Web 기반 **사용자 PWA** + **관리자 PWA**와, 이를 위한 **Java(MySQL) API 서버**가 함께 들어있는 모노레포입니다.

## 구성
- 사용자 웹: `lib/main.dart`
- 관리자 웹: `lib/admin_main.dart`
- 서버(Java): `server/`

## 로컬 실행

### 서버
```zsh
cd server
./run_local.sh
```

### 사용자 웹
```zsh
./scripts/run_user_web.sh
# 서버 주소를 바꾸려면
# API_BASE_URL="http://localhost:8080" ./scripts/run_user_web.sh
```

### 관리자 웹
```zsh
./scripts/run_admin_web.sh
# 서버 주소를 바꾸려면
# API_BASE_URL="http://localhost:8080" ./scripts/run_admin_web.sh
```

## 배포 개요

### Vercel (웹)
Vercel 프로젝트를 **2개(사용자/관리자)** 로 분리해서 배포하는 방식을 권장합니다.

- 사용자 빌드: `./scripts/build_user_web.sh`
- 관리자 빌드: `./scripts/build_admin_web.sh`

둘 다 Vercel 환경변수로 `API_BASE_URL`을 설정해 서버(Railway) 주소를 주입하세요.

> `vercel.json`에 SPA rewrite가 포함되어 있어 새로고침 404를 방지합니다.

### Railway (서버)
서버는 컨테이너 배포가 쉽도록 `server/Dockerfile`을 포함합니다.

서버 필수 환경변수(권장):
- `PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `ADMIN_ID_HASH` (관리자 로그인용 SHA-256 해시)

> 참고: `server/secrets/` 및 `server/README.md` 같은 로컬용 문서는 GitHub에 커밋하지 않도록 `.gitignore`로 제외되어 있습니다.

