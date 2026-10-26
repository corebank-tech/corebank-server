# ADR-0010. 아웃박스는 Spring Modulith Event Publication Registry로 구현한다

- 상태: 수락됨 (2026-10-21)
- 관련: 이슈 #437(PH-96-①) · #706~#709(PH-96-②) · #436(PH-32), PR #505 · #723,
  [ADR-0005](0005-no-spring-batch-mq-api-gateway.md) §2 · [ADR-0003](0003-batch-api-for-cross-domain-lock-reuse.md),
  `docs/phase2/README.md` §3-3 · `tasks.md` §PH-96

**요약:** 업무가 커밋된 직후 리스너가 실패해도 이벤트가 사라지지 않게 한다. outbox를 직접 만들지 않고
Spring 공식 라이브러리의 발행 기록 테이블을 쓴다.

## 맥락

ADR-0005는 MQ 대신 아웃박스를 쓰기로 했다. 구현은 `BEFORE_COMMIT` 리스너가 `outbox` 테이블에 INSERT하고,
릴레이가 `BatchExecutionLockPort`로 폴링하는 방식이었다. 착수 전에 문제 세 가지를 찾았다.

| 문제 | 내용 |
|---|---|
| 이미 있는 기능을 다시 만든다 | Spring Modulith가 테이블 · 직렬화 · 상태 · 시도 횟수 · 재제출을 모두 제공한다 |
| 릴레이가 6시간 멈출 수 있다 | 릴레이가 락을 쥔 채 죽으면 stale 임계값 6시간 동안 락이 풀리지 않는다 |
| 유니크 키가 업무를 롤백시킨다 | INSERT가 업무 트랜잭션 안에 있어, 같은 이벤트가 두 번 발행되면 키 중복으로 업무까지 실패한다 |

## 결정

**`spring-modulith-starter-jdbc` 2.0.8을 쓴다.** 동작은 이렇다.

1. 업무 트랜잭션 안에서 AFTER_COMMIT 리스너마다 `EVENT_PUBLICATION` 1행을 함께 커밋한다.
2. 리스너가 성공하면 행을 지운다(`completion-mode=delete`).
3. 실패하면 행이 남고, 재제출로 다시 보낸다. **남은 행 = 아직 전달되지 않은 이벤트**다.

| 항목 | 결정 |
|---|---|
| 테이블 | jar의 `schemas/v2/schema-mysql.sql`을 Flyway로 그대로 옮긴다. 라이브러리 자동 생성은 끈다 |
| 재전달 | `IncompleteEventPublications` 재제출로 한다(PH-96-②) |
| 멱등 | 받는 쪽에서 막는다. 기록 테이블에는 유니크를 걸지 않는다 |
| 기존 `BEFORE_COMMIT` 리스너 · `DomainEventSink` | 그대로 둔다. 다른 도메인 5개의 테스트 7개가 sink를 `@MockitoBean`으로 쓰고 있어 정리는 따로 한다 |

ADR-0005의 「MQ 대신 아웃박스」는 그대로다. 바뀌는 것은 §2의 구현 방법이다.

## 검토한 대안

- **A. 직접 구현** — 위 문제 2 · 3을 고친 뒤 직렬화 · 상태 전이 · 재제출까지 다시 만들고 테스트해야 한다. 채택하지 않는다.
- **B. Spring Modulith (채택)** — main 코드는 의존성 · Flyway · 설정만 바뀐다.

## 검증

업무가 커밋된 직후 리스너가 예외로 끝나는 상황을 같은 기준 커밋 `61446b99`에서 도입 전 · 후로 돌렸다.
리눅스 테스트 서버의 Testcontainers MySQL에서 실행했다.

| 항목 | 도입 전 | 도입 후 |
|---|---|---|
| 업무 행 | 1 (커밋됨) | 1 (커밋됨) |
| 리스너 실패 직후 | 다시 꺼낼 기록이 없다 — **유실** | `EVENT_PUBLICATION` **1행** 남음 |
| 재제출 후 | 재제출할 대상이 없다 | **전달 1건** · 기록 0행 |

- 도입 후 테스트 4건(`DomainEventPublicationRegistryTest`)이 통과했다. 실패해도 기록이 남는다,
  재제출하면 전달되고 지워진다, 성공하면 바로 지워진다, 업무가 롤백되면 기록도 없다.
- 도입 전 테스트는 Modulith API를 쓰지 않는 별도 코드라 저장소에 남기지 않았다.
- 전체 테스트 2,736건 중 실패 · 오류 · 스킵 0건(7분 8초, 2026-10-21).
- 확인한 것은 **리스너가 예외로 끝나는 경우**다. `kill -9` 후 재기동해 전달되는지는 PH-96-②에서 잰다.

## 알림 최소 계약 (2026-10-26 추가, PR #723 리뷰)

**요약:** 알림 문구는 업무 도메인이 만들고, notification 은 업무를 모른 채 최소 계약 하나로 저장한다.
업무가 늘거나 채널(이메일 PH-102)이 붙어도 notification 코드와 문구가 한 벌로 유지된다.

| 항목 | 결정 |
|---|---|
| 계약 | `notification.api.NotificationRequest`(customerId · type · refId · title · content · occurredAt)와 이를 돌려주는 `NotificationRequestCarrier` |
| 문구 · 마스킹 · 고객용 실패 사유 | 발행 도메인(예: transfer 의 `TransferNotificationComposer`가 자기 `TransferErrorCode` 문구를 쓴다) |
| notification | `@ApplicationModuleListener` 하나가 `NotificationRequestCarrier`를 받는다. 저장 · 업무 키 `(type, ref_id)` 중복 방지만 맡는다 |
| 알림이 없는 결과 | `notification()`이 비어 있다(예: 예약 · 자동이체의 이체 확정 — 그 알림은 #707에서 배치 이벤트가 따로 싣는다) |
| 채택하지 않음 | 받는 쪽 Inbox · worker. 발행 기록이 리스너(채널)별로 재시도를 이미 맡는다. 예약 발송 · 채널별 SLA가 생기면 다시 본다 |

## 결과

- **AFTER_COMMIT 리스너는 모두 기록된다.** 2.0.8에는 대상을 좁히는 설정이 없다(BEFORE_COMMIT 리스너는 기록되지 않는다).
  auth의 `LoginPasswordChangedSessionListener`는 #641(PH-49c)에서 BEFORE_COMMIT으로 바뀌어 기록되지 않는다 —
  세션 삭제가 실패하면 비밀번호 변경도 함께 롤백된다. 그래서 이 결정 시점의 기록 대상은 0개이고, 첫 대상은 PH-96-②의
  알림 구독자다. 세션 정리는 발행 기록 · 재제출로 복구하는 대상이 아니다.
- **테이블은 저장소 규약의 예외다.** 이름이 대문자인 것은 라이브러리가 그 이름으로 조회하기 때문이다.
  `TIMESTAMP(6)`은 jar DDL을 그대로 옮긴 결과이고, MySQL `TIMESTAMP`는 2038-01-19까지만 담는다.
- **재기동 후에는 내용이 같은 기록이 함께 지워질 수 있다.** 진행 중이 아닌 기록은 `LISTENER_ID + SERIALIZED_EVENT`로
  찾아 지우기 때문이다. 구독자가 업무 키로 멱등하면(PH-96-②) 한 번만 실행돼도 결과가 같다.
- **다중 인스턴스 재제출**은 PH-96-② 재제출 스케줄을 만들 때 테스트로 확인한다. 어느 쪽이든 최소 한 번 전달
  (At-Least-Once)이라 받는 쪽 멱등이 필요하다.
- 직렬화는 Jackson 3이고, 버전은 Spring 공식 BOM `spring-modulith-bom` 2.0.8(Boot 4.0.x 대응)로 관리한다.
