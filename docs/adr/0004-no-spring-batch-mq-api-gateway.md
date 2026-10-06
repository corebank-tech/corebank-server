# ADR-0004. Spring Batch · MQ · API Gateway 미도입 — 대체 수단과 근거

- 상태: 수락됨 (2026-10-06)
- 관련: 이슈 #540(PH-85), `docs/phase2/mentor_proposal.md` 범위 결정, `docs/phase2/tasks.md`
  §PH-85·PH-96·PH-51, PR #543·#557(CobStep·러너), PR #505(아웃박스 발행측 골격)

## 맥락

2차 프로젝트 범위를 정할 때(`mentor_proposal.md`) 6인 31영업일 제약 안에서 끝내기 위해
Spring Batch, MQ, API Gateway 도입을 범위에서 제외했다. 세 가지를 새로 들여오는 대신,
이미 레포에 있는 패턴을 확장하는 쪽으로 정했는데, 이 결정의 배경과 "그래서 대신 무엇을
쓰는가"가 지금까지 `tasks.md` 한 줄 외에는 문서화돼 있지 않았다.

- **Spring Batch** — 배치 전용 노드가 없고(util 노드 docker 구성, `infra_arch.md` 참고)
  WAS 1대에서 `BatchExecutionLockPort`(ADR-0003)로 단일 실행만 보장하면 되는 규모라,
  Job/Step/Chunk 메타데이터 테이블 셋업과 운영 학습 비용을 들일 이유가 약하다.
- **MQ** — 발행하는 알림이 4종(즉시이체·예약이체·자동이체·상품가입)뿐이라 전용 브로커를
  운영할 트래픽 규모가 아니다. 더 중요하게는, "이체 성공과 이벤트 발행이 같은 트랜잭션
  안에서 원자적으로 묶여야 한다"는 요구가 있는데, 외부 브로커 클라이언트는 보통 DB
  트랜잭션에 참여하지 못해 별도의 2단계 커밋 문제가 생긴다.
- **API Gateway** — 서비스가 단일 애플리케이션이라 라우팅·집계가 필요 없고, 인증·인가는
  이미 Spring Security가 담당한다.

## 결정

**셋 다 새 인프라를 들이지 않고, 기존 코드 패턴을 확장해서 같은 요구를 만족시킨다.**

1. **Spring Batch → `batch.api.CobStep` 체인 + 러너**
   `CobStep { String name(); int order(); void run(LocalDate businessDate); }` 계약을
   구현한 빈들을 `CobRunnerService`가 `order()` 순으로 실행한다(PR #543). 각 스텝은
   자기 트랜잭션을 열고, 한 스텝이 실패해도 나머지 스텝은 계속 진행한다. 중복 실행 방지는
   `BatchExecutionLockPort`(ADR-0003)를 그대로 재사용한다(PR #557).

2. **MQ → Transactional Outbox**
   `@TransactionalEventListener(phase = BEFORE_COMMIT)` 리스너가 `JdbcTemplate`로
   `outbox` 테이블에 INSERT하고, 별도 릴레이가 그 테이블을 폴링해서 발송한다(PH-96,
   P1 담당). INSERT가 실패하면 원본 업무 트랜잭션도 함께 롤백된다 — 이벤트를 못 남기면
   업무도 커밋되지 않는다는 의도된 동작이다(`README.md` §3-3). 릴레이도
   `BatchExecutionLockPort`로 단일 인스턴스에서만 돈다.

3. **API Gateway → ALB + Spring Security**
   라우팅·헬스체크는 ALB(대상그룹 1개, PH-51)가 맡고, 인증·인가는 기존 Spring Security
   필터 체인이 그대로 담당한다. 별도 Gateway 계층을 추가하지 않는다.

## 검토한 대안

### 대안 A — Spring Batch 그대로 도입

Job/Step/Chunk 모델이 스텝 재시작·실패 격리를 기본 제공하는 건 맞지만, 메타데이터
테이블(`BATCH_JOB_INSTANCE` 등) 셋업과 운영 학습 비용이 크다. 우리가 필요한 것(스텝
순서 실행, 한 스텝 실패해도 나머지 진행, 단일 인스턴스 보장)은 `CobStep` 인터페이스와
기존 락 재사용만으로 충분히 달성돼서, 새 프레임워크를 들이는 이득이 비용을 넘지 않는다.
기각.

### 대안 B — MQ(Kafka·RabbitMQ 등) 도입

알림 4종이라는 작은 트래픽에 전용 브로커를 운영하는 비용이 과하다. 또한 이체 확정과
이벤트 발행이 원자적으로 묶여야 하는데, 외부 브로커 클라이언트는 보통 로컬 DB
트랜잭션에 참여하지 못한다. Outbox 패턴이 "같은 트랜잭션 안에서 적재 → 트랜잭션 커밋
후 비동기 발송"을 DB 하나로 해결해줘서 이 요구에 더 직접적으로 맞는다. 기각.

### 대안 C — API Gateway(Spring Cloud Gateway·Kong 등) 도입

서비스가 단일 애플리케이션이라 여러 서비스로 라우팅을 나눌 필요가 없고, 인증·인가는
이미 Spring Security가 담당한다. Gateway를 추가하면 운영 포인트(배포·모니터링 대상)가
하나 더 늘 뿐, 지금 규모에서 얻는 실익이 없다. 기각.

## 결과

대체 수단 중 일부는 아직 뼈대만 있고 실제 구현은 진행 중이다 — 착각하지 않도록
현재 상태를 그대로 남긴다.

| 항목 | 설계 결정 | 현재 상태 |
|---|---|---|
| Spring Batch 대체 | `CobStep` 체인 + 러너 | 인터페이스·러너 merge 완료(PR #543, #557). **실행 기록 테이블은 아직 없음** — #556에서 작업 예정 |
| MQ 대체 | Transactional Outbox | `BEFORE_COMMIT` 리스너·`DomainEventSink` 인터페이스는 있음(PR #505). **`outbox` 테이블·실제 INSERT·릴레이는 아직 없음** — 현재는 `DomainEventLoggingSink`가 로그만 남기는 임시 구현체. PH-96(P1)에서 교체 예정 |
| API Gateway 대체 | ALB + Spring Security | Spring Security는 이미 운영 중. **ALB는 Terraform plan만 끝났고 apply는 안 함** — PH-51(S3, 10/19~27 리허설·apply)에서 진행 |

이 ADR은 "왜 안 쓰고 무엇으로 대체하는가"라는 결정 자체를 문서화하는 것이 목적이고,
위 표의 미완료 항목들은 각자의 이슈(#556, PH-96, PH-51)에서 별도로 추적한다.
