# 일일 퀘스트 설계 (만들지 않음 · 참고용)

> 기준일: 2026-11-10 · 상태: **만들지 않기로 결정 (2026-11-10)**. 나중에 다시 필요해질 때를 위한 참고용 설계입니다.

## 목표

사용자가 하루에 한 번 정해진 행동(예: 오늘의 메뉴 확인, 메뉴 추천 올리기, 문의 남기기)을 하면 완료로 기록하고 보상을 줍니다.

## 데이터 모델

| 테이블 | 키 | 필드 | 설명 |
|---|---|---|---|
| `quests` | `id` | `code`, `title`, `action`, `reward_points`, `active` | 관리자가 정하는 퀘스트 정의 |
| `quest_completions` | (`user_key`, `quest_id`, `day_key`) PK | `completed_at`, `reward_points` | 하루 1회 완료 기록 |
| `reward_ledger` | `id` | `user_key`, `points`, `source`, `source_key` UNIQUE, `created_at` | 보상 지급 장부 |

- `user_key`: 추천 월 1회 제한과 같은 방식(네트워크 키 해시). 로그인이 생기면 사용자 ID로 바꿉니다.
- `day_key`: `Asia/Seoul` 기준 `YYYY-MM-DD`. 자정 기준이 서버 시간대에 흔들리지 않게 합니다.

## 중복 완료 처리

- 완료는 `INSERT IGNORE INTO quest_completions`로 처리합니다. 0행이면 이미 완료 → 같은 응답을 다시 돌려줍니다(멱등).
- 보상은 같은 트랜잭션에서 `reward_ledger`에 `source_key = "quest:<quest_id>:<day_key>:<user_key>"`로 넣습니다.
  UNIQUE 제약으로 재시도·동시 요청·재시작 후에도 두 번 지급되지 않습니다.
- 로컬 H2와 MySQL 모두 같은 SQL(`INSERT IGNORE`, UNIQUE)로 동작합니다.

## API 초안 (추가 예정, 기존 계약 변경 없음)

| 메서드·경로 | 응답 |
|---|---|
| `GET /api/quests/today` | `[{questId, title, rewardPoints, completed}]` |
| `POST /api/quests/{questId}/complete` | 200 `{completed: true, alreadyCompleted, rewardPoints}` |

- 완료 판정은 서버가 실제 행동 기록(예: 추천 저장 성공)으로 확인할 수 있는 퀘스트부터 시작합니다. 앱이 보낸 "완료" 신호만 믿지 않습니다.

## 남은 결정

- 보상의 용도(포인트 표시만 할지, 교환할지).
- 사용자 식별: 네트워크 키는 같은 Wi-Fi 사용자끼리 겹치므로, 퀘스트 구현 전에 기기 ID나 로그인이 필요한지 결정해야 합니다.
