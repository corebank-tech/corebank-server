# ADR-0003. 다른 도메인의 인프라를 재사용할 때 공개 계약 위치 — batch.api 신설

- 상태: 수락됨 (2026-09-08)
- 관련: 이슈 #366, PR #400 리뷰(danhandev, R2), 선례 ADR-0002

## 맥락

#366(멱등키 정리 배치)에 대한 1차 리뷰(PR #400, danhandev, R2)가 "다중 인스턴스에서
중복 실행될 수 있다"고 지적했다. `batch` 도메인에는 이미 `DailyTransferBatchService`가
쓰는 중복 실행 방지 락(`BatchExecutionLockPort` / `BatchExecutionLockPersistenceAdapter`)이
있어서, 이를 그대로 재사용해 `common.idempotency.IdempotencyKeyCleanupScheduler`에
`BatchExecutionLockPort`를 직접 주입했다.

이 수정이 2차 리뷰(PR #400, danhandev, R2)에서 새 문제로 지적됐다.

- `BatchExecutionLockPort`는 `batch.application.port.out`에 있는 **내부 port**로, 다른
  도메인이 재사용하도록 공개된 계약면이 아니다.
- 현재 승인된 cross-domain 예외는 ADR-0002가 다룬 `transfer`의 `AccountLockJpaEntity`
  부분 매핑 하나뿐이며, 그 예외도 ADR과 소유 도메인 합의를 거쳐 명시적으로 남긴 것이었다.
- `LayerArchitectureTest`는 도메인 내부 계층 방향(adapter→application→domain)만 검증하고,
  `TermsArchitectureTest`처럼 "다른 도메인이 내 내부를 침범하지 못하게" 막는 캡슐화 규칙은
  `terms` 도메인에만 걸려 있다. `batch`에는 같은 규칙이 없어 이번 위반이 CI로 잡히지 않았다.

## 결정

**중복 실행 방지 락처럼 여러 도메인이 재사용할 인프라는 소유 도메인에 `<domain>.api`
공개 패키지를 두고, 그 계약을 통해서만 노출한다.** `terms.api` + `TermsQueryPort` 선례를
그대로 따른다.

- `BatchExecutionLockPort` 인터페이스를 `batch.application.port.out`에서 `batch.api`로
  옮긴다. 인터페이스 자체는 내부 의존이 없는 순수 계약이라 패키지 이동만으로 충분하다.
- `BatchExecutionLockPersistenceAdapter`(구현체)와 `DailyTransferBatchService`(batch
  자신의 소비자)도 `batch.api`를 보도록 import를 바꾼다 — `terms` 자신도 `TermsQueryPort`를
  `api` 패키지에서 그대로 참조하는 것과 동일한 형태다.
- `common.idempotency.IdempotencyKeyCleanupScheduler`는 이제 `batch.api.BatchExecutionLockPort`를
  참조한다.

락 기능 자체를 `common`으로 옮기는 대안은 채택하지 않았다(아래 대안 (B) 참조).

## 검토한 대안

### (A) 현행 유지 — `common`이 `batch.application.port.out`을 계속 직접 참조

변경량은 없지만, ADR-0002가 정립한 "cross-domain 접근은 공개 계약을 통해서만" 원칙과
정면으로 어긋난다. `terms` 도메인에만 이 원칙을 강제하는 것도 일관성이 없다. 기각.

### (B) 락 기능(`BatchExecutionLockPort`+구현체)을 `batch`에서 `common`으로 이전

"여러 도메인이 쓰는 인프라니 공용 패키지가 맞다"는 논리로는 자연스러워 보이지만, 이
저장소는 이미 `common/`을 엔티티·리포지토리 수준까지만 두기로 팀 합의(2026-08-04, #366
착수 시점)를 마친 상태다. `BatchExecutionLockPort`는 `tryAcquire`/`release`라는
application-layer 계약을 가진 port이지 엔티티·리포지토리가 아니라, 이관 시 그 합의 범위를
벗어난다. 또한 batch 소유였던 코드(엔티티·어댑터·기존 소비자인 `DailyTransferBatchService`
사용처)를 전부 옮겨야 해서 변경 범위가 (A)/(C)보다 크고, batch 담당자와의 소유권 이전 합의도
별도로 필요하다. 기각.

### (C) `batch.api` 신설 (채택)

`terms.api`로 이미 검증된 패턴을 그대로 복제한다. `BatchExecutionLockPort`가 순수
인터페이스라 패키지 이동 + import 3곳 수정으로 끝나는 최소 변경이고, `batch` 도메인의
소유권도 그대로 유지된다. `common/` 범위 합의와도 충돌하지 않는다.

## 결과

- `common`이 `batch`를 참조하는 유일한 경로가 `batch.api.BatchExecutionLockPort`로
  고정된다.
- `batch` 도메인은 락 기능의 소유권을 유지한 채, 공개할 부분만 `api`로 노출하는 `terms`와
  동일한 모양을 갖춘다.
- 후속 과제: `LayerArchitectureTest`/`TermsArchitectureTest`처럼 `batch`에도 "다른 도메인이
  `batch.api` 밖의 클래스에 의존하면 안 된다"는 ArchUnit 규칙을 추가할지는 이번 PR 범위에
  포함하지 않았다 — 별도 결정 필요.
