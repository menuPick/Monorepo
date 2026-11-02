# MenuPick Monorepo

> 문서 기준일: 2026-11-02

Flutter 사용자 앱, 관리자 앱, API 서버를 함께 관리하는 저장소입니다.

## 현재 기준선

- 사용자 Flutter 진입점: `lib/main.dart`
- 관리자 Flutter 진입점: `lib/admin_main.dart`
- 현재 API 서버: `server/`의 Java 서버
- 개발용 저장소: 개인별 로컬 `data/` 파일 저장소
- 기본 로컬 서버: `http://localhost:18080`
- Android 에뮬레이터 접근 주소: `http://10.0.2.2:18080`
- 사용자 Web과 관리자 Web은 서로 다른 Flutter 엔트리포인트를 사용합니다.
- `data/`는 Git에 포함하지 않습니다.
- 실제 Android 기기 흐름 테스트는 `integration_test/`에서 관리합니다.

## 저장소 구조

- `lib/`: 사용자 Flutter 앱
- `lib/admin/`: 관리자 Flutter 기능
- `lib/admin_main.dart`: 관리자 앱 엔트리포인트
- `server/`: Java API 서버와 테스트
- `scripts/`: 사용자·관리자 Web 실행·빌드 스크립트
- `data/`: 개인 로컬 서버 데이터
- `docs/`: 프로젝트 문서와 날짜별 기록

## Web 진입 기준

- 사용자 Web 첫 진입에는 관리자 코드와 관리자 자산을 포함하지 않습니다.
- 관리자 Web은 `lib/admin_main.dart`를 별도로 빌드합니다.
- Web은 모바일 앱의 기본 실행 대상이 아닙니다.
- Web 배포가 필요해질 때 사용자 경로와 관리자 경로를 분리합니다.

## 다음 Roadmap

현재 코드를 기준선으로 유지한 뒤 다음 작업 시 검토합니다.

1. 현재 Flutter 앱과 Java 서버의 API 계약을 문서화하고 고정합니다.
2. Go 서버 전환이 실제로 필요한지 성능 측정 결과를 기준으로 판단합니다.
3. AI 추천·위험 메뉴 판별 기능의 입력과 출력 계약을 정합니다.
4. AI 서비스 분리 여부와 관리자 승인 흐름을 설계합니다.
5. 일일 퀘스트의 진행 상태와 중복 완료 처리 모델을 설계합니다.
6. 설계가 확정된 뒤에만 서버 언어·DB·AI 코드를 변경합니다.

## 변경 보류 원칙

다음 검토 시점 전에는 Go 전환, AI 서버 추가, 퀘스트 구현, DB 교체를 임의로 시작하지 않습니다. 에이전트 코딩 규칙은 [`AGENTS.md`](AGENTS.md), 날짜별 작업 기록은 [`records/`](records/)에서 확인합니다.
