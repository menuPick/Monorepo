# API 계약

> 기준일: 2026-11-10 · 서버 `server/` (Spring Boot) · `menupick.api-version: 2026-11-10`

Flutter 앱이 사용하는 경로·JSON 필드·상태 코드입니다. **필드를 지우거나 이름을 바꾸지 않고, 추가만 합니다.**
실행 중인 서버의 전체 스키마는 Swagger `/swagger-ui.html`에서 확인합니다.

## 공통

- 본문은 UTF-8 JSON, 최대 16KB.
- 오류: `{"error": "<code>", "message": "<사람이 읽는 메시지>"}`. 429는 `Retry-After` 헤더를 포함할 수 있습니다.
- 목록 응답: `{"items": [...]}`, 최신순. `limit` 쿼리 기본 50.
- 관리자 API는 `Authorization: Bearer <token>` (로그인으로 발급, 8시간).
- `GET /health`의 `storage`: `h2`(로컬 내장 DB) 또는 `database`(MySQL/MariaDB).

## 데이터 형식

| 이름 | 필드 |
|---|---|
| Recommendation | `id` number, `menuName`, `reason`, `recommendedMenu`, `category`(호환용, 자동 분류 없음·보통 `""`), `createdAt`(ISO-8601) |
| Inquiry | `id`, `message`, `adminEmail`, `createdAt` |
| AiAssessment | `recommendationId`, `riskScore`(0~1), `riskLevel`(`low`/`medium`/`high`), `flags`[], `reasons`[], `engine`(`graph`/`llm`), `assessedAt` |

## 사용자 API (`lib/main.dart`)

| 메서드·경로 | 요청 | 성공 | 주요 오류 |
|---|---|---|---|
| `POST /api/recommendations` | `menuName`(필수, ≤255), `reason`(필수, ≤2000), `recommendedMenu`(선택, 비우면 서버가 추천), `category`(선택) | 201 Recommendation | 400 `bad_request`, 429 `monthly_limit_exceeded`(네트워크당 월 1회, `MONTHLY_RECOMMENDATION_LIMIT=false`면 끔 — 로컬 실행 기본값), 429 `rate_limited` |
| `POST /api/recommendations/reason-draft` *(2026-11-10 추가)* | `menuName`, `reason`(선택) | 200 `{reason, added, engine}` — 사용자가 쓴 말 뒤에 AI 문장을 덧붙인 이유(저장 안 함) | 429, 503 `ai_unavailable` |
| `GET /api/recommendations/latest` | — | 200 Recommendation | 404 `not_published` |
| `POST /api/inquiries` | `message`(≤4000) | 201 Inquiry | 400, 429 `rate_limited` |
| `GET /health` | — | 200 | — |

## 관리자 API (`lib/admin_main.dart`)

| 메서드·경로 | 요청 | 성공 | 주요 오류 |
|---|---|---|---|
| `POST /api/admin/login` | `id` | 200 `{token, adminEmail, expiresAt}` | 401 `unauthorized`, 429 `rate_limited` |
| `GET /api/admin/recommendations?limit=` | — | 200 `{items: Recommendation[]}` | 401 |
| `POST`/`DELETE /api/admin/recommendations` | `{"ids":[1,2]}` | 200 `{deleted}` | 400, 401 |
| `POST /api/admin/decision` | `recommendationId`, `publishAt`(ISO-8601) | 200 `{ok:true}` | 400, 404 |
| `GET /api/admin/inquiries?limit=` | — | 200 `{items: Inquiry[]}` | 401 |
| `POST`/`DELETE /api/admin/inquiries` | `{"ids":[...]}` | 200 `{deleted}` | 400, 401 |
| `GET /api/inquiries/latest` | — | 200 Inquiry | 401, 404 |
| `GET /api/admin/ai-assessments?limit=` *(2026-11-10 추가)* | — | 200 `{enabled, items: AiAssessment[]}` (최근 추천 `limit`건 기준) | 401 |
| `POST /api/admin/recommendations/{id}/ai-assessment` *(2026-11-10 추가)* | — | 200 AiAssessment | 404, 502 `ai_unavailable`, 503 `ai_disabled` |

## 내부 API: Java 서버 → Python AI 서버 (`ai/`)

외부에 공개하지 않습니다. 자세한 내용은 [`ai-design.md`](ai-design.md).

| 메서드·경로 | 요청 | 응답 |
|---|---|---|
| `POST /v1/recommend` | `menuName`, `reason` | `recommendedMenu`, `confidence`, `reasons`[], `engine` |
| `POST /v1/elaborate` | `menuName`, `reason` | `reason`(사용자 말 + 덧붙인 문장), `added`, `engine` |
| `POST /v1/assess` | `menuName`, `reason`, `recommendedMenu` | `riskScore`, `riskLevel`, `flags`[], `reasons`[], `engine` |
| `GET /health` | — | `ok`, `engine`(`graph`/`llm`), `graph`{`nodes`,`edges`}, (llm일 때) `llm`, `model` |

`AI_SERVICE_TOKEN`을 설정하면 `X-AI-Token` 헤더가 필요합니다.
