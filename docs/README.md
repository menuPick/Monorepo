# MenuPick Monorepo

> 문서 기준일: 2026-11-10

Flutter 사용자 앱, 관리자 앱, API 서버를 함께 관리하는 저장소입니다.

## 현재 기준선

- 사용자 Flutter 진입점: `lib/main.dart`
- 관리자 Flutter 진입점: `lib/admin_main.dart`
- 현재 API 서버: `server/`의 Spring Boot 서버 (`server/run_local.ps1` 또는 `run_local.sh`로 실행, 확인 페이지 `/`, Swagger `/swagger-ui.html`)
- 개발용 저장소: 개인별 로컬 H2 DB `data/menupick.mv.db` (`DB_URL`이 없을 때, MySQL 호환 모드). `DB_URL`을 주면 MySQL/MariaDB를 씁니다.
- 기본 로컬 서버: `http://localhost:18080` (로컬 관리자 ID: `test`, `server/run_local.*`에서 설정)
- Android 에뮬레이터 접근 주소: `http://10.0.2.2:18080`
- AI 서버(선택): `ai/`의 Python 서버 (`ai/run_local.ps1`, `http://localhost:18090`, 문서 `/docs`). Java 서버를 `AI_BASE_URL=http://localhost:18090`으로 실행하면 연결됩니다. 기본은 그래프 엔진(키워드 검색 O(N), N=K×L + 메뉴 그래프 BFS, 즉시 응답)이고, `AI_ENGINE=llm`이면 무료 로컬 LLM(Ollama `qwen2.5:7b`)을 씁니다. `server/run_local.*`은 AI 서버에 자동 연결하고 월 1회 추천 제한을 끈 상태로 실행합니다.
- 사용자 Web과 관리자 Web은 서로 다른 Flutter 엔트리포인트를 사용합니다.
- 관리자 웹 바로가기: `scripts/build_admin_web_local.ps1`로 빌드하면 API 서버가 `/admin/`에서 제공합니다(`http://localhost:18080/admin/`, 에뮬레이터 `http://10.0.2.2:18080/admin/`). 사용자 앱 문의 화면의 "관리자 계정 이메일"을 누르면 이 주소가 브라우저로 열립니다(`ADMIN_WEB_URL`로 변경 가능).
- `data/`는 Git에 포함하지 않습니다.
- 실제 Android 기기 흐름 테스트는 `integration_test/`에서 관리합니다.

## 저장소 구조

- `lib/`: 사용자 Flutter 앱
- `lib/admin/`: 관리자 Flutter 기능
- `lib/admin_main.dart`: 관리자 앱 엔트리포인트
- `server/`: Spring Boot API 서버와 테스트
- `ai/`: Python AI 서버(메뉴 추천·위험 메뉴 판별)와 테스트
- `scripts/`: 사용자·관리자 Web 실행·빌드 스크립트
- `data/`: 개인 로컬 서버 데이터
- `docs/`: 프로젝트 문서와 날짜별 기록

## Web 진입 기준

- 사용자 Web 첫 진입에는 관리자 코드와 관리자 자산을 포함하지 않습니다.
- 관리자 Web은 `lib/admin_main.dart`를 별도로 빌드합니다.
- Web은 모바일 앱의 기본 실행 대상이 아닙니다.
- Web 배포가 필요해질 때 사용자 경로와 관리자 경로를 분리합니다.

## Roadmap

| # | 항목 | 상태 (2026-11-10) |
|---|---|---|
| 1 | API 계약 문서화·고정 | 완료 → [`api-contract.md`](api-contract.md) |
| 2 | Go 서버 전환 필요성 판단 | 전환하지 않음 → [`ai-design.md`](ai-design.md#결정-기록) |
| 3 | AI 추천·위험 메뉴 판별 입출력 계약 | 완료·구현 → [`ai-design.md`](ai-design.md) |
| 4 | AI 서비스 분리·관리자 승인 흐름 | 완료·구현 (Python `ai/`, 관리자 앱 표시) |
| 5 | 일일 퀘스트 상태·중복 완료 모델 | **하지 않음** (2026-11-10 결정) → [`quest-design.md`](quest-design.md)는 참고용으로만 남김 |
| 6 | 설계 확정 후 코드 변경 | 완료 (3·4 반영, 5는 하지 않음) |

## 변경 보류 원칙

Go 전환, 운영 DB(MySQL/MariaDB) 교체는 임의로 시작하지 않습니다. 일일 퀘스트는 만들지 않기로 했습니다. 에이전트 코딩 규칙은 [`AGENTS.md`](AGENTS.md), 날짜별 작업 기록은 [`records/`](records/)에서 확인합니다.
