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

**`spring-modulith-starter-jdbc` 2.0.9를 쓴다.** 동작은 이렇다.

1. 업무 트랜잭션 안에서 AFTER_COMMIT 리스너마다 `EVENT_PUBLICATION` 1행을 함께 커밋한다.
2. 리스너가 성공하면 행을 지운다(`completion-mode=delete`).
3. 실패하면 행이 남고, 재제출로 다시 보낸다. **남은 행 = 아직 전달되지 않은 이벤트**다.

| 항목 | 결정 |
|---|---|
| 테이블 | jar의 `schemas/v2/schema-mysql.sql`을 Flyway로 그대로 옮긴다. 라이브러리 자동 생성은 끈다 |
| 재전달 | `FailedEventPublications` 재제출 + 멈춘 기록 정리(`staleness`)로 한다(#709, 아래 「구독 실패 처리」) |
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
- 위 표는 **리스너가 예외로 끝나는 경우**다. 처리 도중 프로세스가 죽는 경우는 #709에서 아래처럼 쟀다.

**`kill -9` 실측 (2026-11-03, #709).** 리눅스 테스트 서버에서 `dev` a966c78f + #709 jar를 실측 전용 MySQL에 띄웠다.
이체 알림 키 범위에 행 잠금을 걸어 알림 저장을 멈춰 두고, 이체 1건 뒤 앱을 `kill -9`로 죽였다가 다시 띄웠다.

| 시각 | 발행 기록 | 알림 |
|---|---|---|
| 13:49:30 이체 커밋 | `PROCESSING` · 시도 1, 알림 INSERT 1건이 잠금 대기 | — |
| 13:49:36 `kill -9` → 13:49:48 재기동 | `PROCESSING` · 시도 1 그대로 | 0 |
| 13:59:48 멈춤 점검 | `FAILED` (「PROCESSING older than PT10M as failed」 로그) | 0 |
| 14:04:52 재제출 뒤 | 0행 | **1** |

재기동 뒤 첫 전달까지 15분이다. 멈춤 기준 10분에 재제출 주기 5분이 더해진 값이고, 그사이 행이 남아 있어 유실은 없다.

## 알림 최소 계약 (2026-10-26 추가, PR #723 리뷰)

**요약:** 알림 문구는 업무 도메인이 만들고, notification 은 업무를 모른 채 최소 계약 하나로 저장한다.
업무가 늘거나 채널(이메일 PH-102)이 붙어도 notification 코드와 문구가 한 벌로 유지된다.

| 항목 | 결정 |
|---|---|
| 계약 | `notification.api.NotificationRequest`(customerId · type · refId · title · content · occurredAt)와 이를 돌려주는 `NotificationRequestCarrier` |
| 문구 · 마스킹 · 고객용 실패 사유 | 발행 도메인(예: transfer 의 `TransferNotificationGenerator`가 자기 `TransferErrorCode` 문구를 쓴다). 예약 · 자동이체는 문구를 자기 생성기로 만들고, 실패 사유는 이체 엔진이 판정한 `TransferResult.customerFailureReason()`을 받아 붙인다(#707) |
| notification | `@ApplicationModuleListener` 하나가 `NotificationRequestCarrier`를 받는다. 저장 · 업무 키 `(type, ref_id)` 중복 방지만 맡는다 |
| 알림이 없는 결과 | 알림 이벤트를 발행하지 않는다. `notification()`은 필수값이고 비어 있으면 검증에서 실패한다(예: 예약 · 자동이체의 이체 확정은 `TransferSettled`를 내지 않고, 그 알림은 배치 이벤트가 싣는다, #707 · PR #738 리뷰) |
| 채택하지 않음 | 받는 쪽 Inbox · worker. 발행 기록이 리스너(채널)별로 재시도를 이미 맡는다. 예약 발송 · 채널별 SLA가 생기면 다시 본다 |

## 구독 실패 처리 (2026-11-03 추가, #709)

**요약:** 알림 리스너가 실패하면 즉시 재시도 → 5분마다 재제출 → 상한을 넘으면 포기 · 경보 → 사람이 원인을 고친 뒤 재처리한다.
재시도 · 재제출 · 멈춘 기록 정리는 프레임워크가 하고, 직접 짠 것은 재제출 상한 판정과 경보다.

| 단계 | 쓰는 것 | 값 |
|---|---|---|
| 즉시 재시도 | `@Retryable` + `@EnableResilientMethods`(Spring 7) | 재시도 3번(기본값) · 간격 200ms 고정. 계약 위반(`ConstraintViolationException`)은 재시도하지 않는다 |
| 주기 재제출 | `FailedEventPublications.resubmit` — `NotificationResubmissionScheduler` | 5분마다 · 묶음 100건(기본값) |
| 포기 | 재제출 필터 | 재제출 3번을 넘긴 기록은 다시 보내지 않고 행은 남긴다. `notification.publication.abandoned` 지표와 ERROR 로그 |
| 멈춘 기록 정리 | `spring.modulith.events.staleness.*` | 처리 중 · 발행됨 · 재제출됨으로 10분 넘게 멈추면 `FAILED`. 점검 주기 1분(기본값). 재제출됨은 마지막 재제출 시각부터 잰다(2.0.9에서 고쳐짐 — 2.0.8은 처음 발행 시각이라 오래된 기록을 재제출하면 처리 중에 `FAILED`가 됐다) |
| 재기동 후 전달 | 위 두 가지로 한다. `republish-outstanding-events-on-restart`는 켜지 않는다 | — |

| 확인한 사실 (소스 · 실측) | 결과 |
|---|---|
| 시도 횟수 시작값 | 기록을 만들 때 첫 전달이 시도 1번이다(`JdbcEventPublicationRepositoryV2.create`). 재제출 횟수 = 시도 − 1 |
| 장애 중 횟수 | 시도는 재제출이 기록을 가져갈 때만 는다. DB가 죽어 있으면 늘지 않는다 |
| 다중 인스턴스 | 재제출 중인 기록은 조건부 UPDATE가 막는다. 재제출된 실행이 바로 실패하면 다른 쪽이 또 가져간다(시도 1 → 3 실측). 알림은 1건 |
| 묶음 막힘 | 실패 기록을 오래된 순으로 100건 읽은 뒤 거른다. 포기한 기록이 100건이면 새 실패 기록이 밀린다. 공개 API로 포기 기록을 치울 수 없어 이때 「읽기 묶음이 가득 찼다」 경보를 따로 낸다 |
| 배치 락 | 두지 않는다. 기존 `BatchExecutionLockPort`는 죽은 인스턴스의 락이 6시간 풀리지 않는다 |

**수동 재처리 런북** — 포기 경보가 오면:

1. 로그 · 지표로 원인을 찾아 고친다. 포기 기록은 `SELECT ID, PUBLICATION_DATE, COMPLETION_ATTEMPTS FROM EVENT_PUBLICATION WHERE STATUS = 'FAILED' AND COMPLETION_ATTEMPTS >= 4;`
2. 고친 뒤 시도 횟수를 첫 전달 상태로 되돌린다. 다음 재제출(5분 안)이 다시 보낸다.
   `UPDATE EVENT_PUBLICATION SET COMPLETION_ATTEMPTS = 1 WHERE STATUS = 'FAILED' AND COMPLETION_ATTEMPTS >= 4;`
3. 행이 사라졌는지(전달 완료) 1번 쿼리로 확인한다. 같은 알림이 다시 가도 `(type, ref_id)`로 1건이다.

## 결과

- **AFTER_COMMIT 리스너는 모두 기록된다.** 2.0.9에도 대상을 좁히는 설정이 없다(BEFORE_COMMIT 리스너는 기록되지 않는다).
  auth의 `LoginPasswordChangedSessionListener`는 #641(PH-49c)에서 BEFORE_COMMIT으로 바뀌어 기록되지 않는다 —
  세션 삭제가 실패하면 비밀번호 변경도 함께 롤백된다. 그래서 이 결정 시점의 기록 대상은 0개이고, 첫 대상은 PH-96-②의
  알림 구독자다. 세션 정리는 발행 기록 · 재제출로 복구하는 대상이 아니다.
- **테이블은 저장소 규약의 예외다.** 이름이 대문자인 것은 라이브러리가 그 이름으로 조회하기 때문이다.
  `TIMESTAMP(6)`은 jar DDL을 그대로 옮긴 결과이고, MySQL `TIMESTAMP`는 2038-01-19까지만 담는다.
- **재기동 후에는 내용이 같은 기록이 함께 지워질 수 있다.** 진행 중이 아닌 기록은 `LISTENER_ID + SERIALIZED_EVENT`로
  찾아 지우기 때문이다. 구독자가 업무 키로 멱등하면(PH-96-②) 한 번만 실행돼도 결과가 같다.
- **다중 인스턴스 재제출**은 같은 기록을 두 번 가져갈 수 있다(#709 실측, 아래 「구독 실패 처리」). 최소 한 번 전달
  (At-Least-Once)이라 받는 쪽 멱등이 필요하고, 알림은 `(type, ref_id)`로 1건이다.
- 직렬화는 Jackson 3이고, 버전은 Spring 공식 BOM `spring-modulith-bom` 2.0.9(Boot 4.0.x 대응)로 관리한다.
