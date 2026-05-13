# Cloudtype 배포 가이드 (MenuPick)

이 문서는 **Vercel 없이 GitHub Pages(사용자/관리자 웹)** + **Cloudtype(API 서버 + DB)** 조합으로 배포하는 빠른 가이드입니다.

## 1) Cloudtype에 DB 만들기
Cloudtype 콘솔에서 DB(MySQL 또는 MariaDB)를 먼저 생성하고 아래 값을 확인하세요.

- DB Host
- DB Port
- DB Database name
- DB Username
- DB Password

> DB가 MariaDB만 제공되는 환경도 있으므로, 서버는 `jdbc:mariadb://...` 도 지원하도록 구성되어 있습니다.

## 2) Cloudtype에 서버(API) 배포 (Dockerfile 권장)
1. Cloudtype에서 새 App 생성
2. GitHub 연동 후 레포 선택: `menuPick/Monorepo`
3. **서브 디렉터리(Sub directory)**: `server`
4. 빌드 방식: Dockerfile(컨테이너) 선택

### 필수 환경변수
Cloudtype App의 Environment variables에 아래를 추가합니다.

- `ADMIN_ID_HASH` : 로컬에서 `server/set_admin_id.sh "원하는관리자ID"` 실행 후 출력되는 해시
- `DB_URL` : JDBC URL
- `DB_USER` : DB 유저
- `DB_PASSWORD` : DB 비밀번호
- `CORS_ALLOWED_ORIGINS` : GitHub Pages origin 허용

#### DB_URL 예시
MySQL:
```
jdbc:mysql://<HOST>:<PORT>/<DB>?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true
```

MariaDB:
```
jdbc:mariadb://<HOST>:<PORT>/<DB>?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
```

#### CORS_ALLOWED_ORIGINS 예시
GitHub Pages가 프로젝트 페이지라면(예: `https://menupick.github.io/Monorepo/`) **origin은 host만** 입니다.

```
http://localhost:*,https://menupick.github.io
```

### 포트(PORT)
Cloudtype가 `PORT` 환경변수를 자동 주입하는 경우가 많습니다. 서버는 `PORT`를 사용해 해당 포트로 바인딩합니다.

## 3) 서버 배포 확인
Cloudtype가 제공하는 서버 URL로 아래를 확인하세요.

- `GET /health`

예:
- `https://<cloudtype-app-domain>/health`

## 4) GitHub Pages(웹)에서 API_BASE_URL 연결
레포 Settings → Secrets and variables → Actions에서 `API_BASE_URL` 값을 Cloudtype 서버 URL로 설정한 뒤,
GitHub Actions에서 Pages 배포 워크플로우를 다시 실행하면 웹이 새 API 주소로 빌드됩니다.

