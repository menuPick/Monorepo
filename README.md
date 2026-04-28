# MenuPick (MVP)

오늘 뭐 먹을지 매번 고민돼서, **메뉴 추천을 모아두고 관리자(선생님/운영자)가 최종 메뉴를 공개**할 수 있게 만든 간단한 MVP입니다.

이 레포는 하나의 프로젝트 안에 다음이 같이 들어있습니다.
- **사용자 PWA** (Flutter Web)
- **관리자 PWA** (Flutter Web)
- **API 서버** (Java + MySQL)

## MVP에서 되는 것
- 사용자가 메뉴 추천/이유를 올리기
- 관리자가 추천 목록을 보고 **결정 메뉴 + 공개 시각** 설정
- 공개 전: 사용자 화면에는 **“관리자가 결정 중입니다”**
- 공개 후: 사용자 화면에 결정 메뉴 노출

## 엔트리포인트
- 사용자 앱: `lib/main.dart`
- 관리자 앱: `lib/admin_main.dart`
- 서버: `server/`

## 로컬 실행

### 1) 서버
```bash
cd server
./run_local.sh
```

### 2) 사용자 웹
```bash
./scripts/run_user_web.sh
# 서버 주소를 바꾸려면
# API_BASE_URL="http://localhost:8080" ./scripts/run_user_web.sh
```

### 3) 관리자 웹
```bash
./scripts/run_admin_web.sh
# 서버 주소를 바꾸려면
# API_BASE_URL="http://localhost:8080" ./scripts/run_admin_web.sh
```

## 배포(요약)

### 웹: Vercel (사용자/관리자 분리 권장)
Vercel 프로젝트를 **2개(사용자/관리자)** 로 나눠 배포하는 걸 권장합니다.

- 사용자 빌드: `./scripts/build_user_web.sh`
- 관리자 빌드: `./scripts/build_admin_web.sh`

두 프로젝트 모두 Vercel 환경변수로 `API_BASE_URL`을 설정해 **AWS에 올린 서버 주소**를 주입하세요.

> `vercel.json`에 SPA rewrite가 포함되어 있어 새로고침 404를 방지합니다.

### 서버: AWS
서버는 컨테이너로 올리기 쉽게 `server/Dockerfile`을 포함합니다.

AWS(ECS/Fargate, EC2, Elastic Beanstalk 등) 어디든 가능하고, 필수 환경변수는 아래입니다.
- `PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `ADMIN_ID_HASH` (관리자 로그인용 SHA-256 해시)

> 참고: `server/secrets/` 및 `server/README.md` 같은 로컬용 문서는 GitHub에 커밋되지 않도록 `.gitignore`로 제외되어 있습니다.

