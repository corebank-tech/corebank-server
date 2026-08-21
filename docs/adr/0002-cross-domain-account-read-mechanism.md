# ADR-0002. 다른 도메인이 소유한 테이블을 읽는 메커니즘 — 부분 매핑 vs 공개 UseCase

- 상태: 수락됨 (2026-08-21)
- 관련: 이슈 #263, 계기 PR #265(#177) 셀프 리뷰, 선행 이슈 #68·#69, 영향 문서 `team_db_architecture_guide.md` §3-①

## 맥락

#177에서 계좌번호 단건 조회를 account 공개 UseCase(`AccountNumberQueryUseCase`)로 이관하면서,
`subscription`이 `account` 데이터를 읽는 경로가 두 가지로 갈렸다.

- `AccountNumberQueryAdapter` → account 공개 UseCase 호출
- `AccountLookupAdapter` → `SubscriptionAccountJpaEntity`(`@Table(name = "account")`) 부분 매핑 직접 조회

같은 패키지 안에서 같은 `account` 행을 같은 키로 읽는데 메커니즘이 달랐다.

`team_db_architecture_guide.md` §3-①은 "다른 도메인이 소유한 테이블을 일부 컬럼만 떼어 매핑하는
경량 엔티티"를 예외로 허용하면서 `transfer`의 `AccountLockJpaEntity`를 사례로 들고 있었다.
그래서 "부분 매핑이 팀 표준"으로 읽혔고, `subscription`만 UseCase로 바꾸면 4개 도메인 중
혼자 다른 방식이 되는 것처럼 보였다.

### 코드 확인 결과 (2026-08-21, `refactor/177-subscription-account-number-query-port` 기준)

`account` 테이블을 부분 매핑하는 4개 도메인은 성격이 같지 않다.

| 도메인 | 클래스 | 하는 일 | 공개 UseCase 전환 |
| --- | --- | --- | --- |
| `transfer` | `AccountLockJpaEntity` | `SELECT ... FOR UPDATE` 락 + `balance` / `last_transaction_at` **쓰기**, `@Version` | 계약 재설계 필요 |
| `autotransfer` | `AccountLookupJpaEntity` | 읽기 전용 (`status`, `customer_id`, `alias`, `withdrawal_registered`) | 가능 |
| `scheduledtransfer` | `AccountLookupJpaEntity` | 읽기 전용 + 일괄 조회 | 가능 |
| `subscription` | `SubscriptionAccountJpaEntity` | 읽기 전용 (`findById` 단건) | 가능 |

`transfer`가 다른 이유는 `AccountLockPort`의 계약에 있다. `lockForTransfer`가 계좌 ID
오름차순으로 비관적 락을 잡고, `applyTransfer`가 **같은 트랜잭션 안에서** 두 계좌의 잔액을
원자적으로 변경한다. 이 락 순서 계약을 account 공개 API 뒤로 옮기려면 account 인 포트가
락 획득 순서와 락 획득~변경 사이의 재검증 지점까지 드러내야 해서 별도 재설계가 필요하다.

**트랜잭션 때문에 불가능한 것은 아니다**(PR #271 리뷰 지적). 공개 UseCase도 기본 전파
(`REQUIRED`)로 호출자 트랜잭션에 참여하므로 락 획득·잔액 변경 자체는 공개 UseCase 뒤에서
얼마든지 가능하다. 실제로 `transfer`의 `ProductSubscriptionDepositService`(공개 인 포트
`ProductSubscriptionDepositUseCase`)가 상품가입 실행의 트랜잭션에 참여한 채로
락 획득 → 출금계좌 재검증 → 잔액 변경 → 원장 기표를 한 UseCase 안에서 수행한다.
즉 `transfer`의 부분 매핑은 "기술적 불가"가 아니라 **미뤄둔 재설계**다.

즉 §3-①이 사례로 든 `AccountLockJpaEntity`는 **쓰기·락 케이스**이고, 그 근거를
읽기 전용 조회까지 일반화한 것이 혼선의 원인이었다.

### `subscription`에서는 부분 매핑의 명분이 이미 깨져 있었다

부분 매핑 주석이 내세운 명분은 "account 패키지를 import하지 않는다"였는데, `subscription`에서는
그 전제가 성립하지 않는다.

- `SubscriptionAccountJpaEntity`가 `account.domain.AccountStatus`를 import하고 있었다.
- `ProductSubscriptionExecuteService`가 `ProductAccountOpeningUseCase`(account 공개 인 포트)를 직접 호출한다.
- `ProductSubscriptionValidationService`가 `AccountErrorCode`를 직접 쓴다.

`subscription`은 이미 account 공개면에 의존하는 도메인이다. 여기서만 테이블 직접 매핑으로
격리한다는 것은 실효가 없다.

### 전환 비용

`AccountPersistencePort.findByAccountIdAndCustomerId`가 이미 같은 행을 `Account` 도메인으로
반환하고, 출금계좌 판정에 필요한 `status` / `withdrawalRegistered` / `balance`를 모두 담고 있다.
신규 3파일(UseCase·결과 record·서비스), 삭제 2파일이며 두 삭제 파일을 참조하는 테스트는 없었다.

## 결정

**다른 도메인이 소유한 테이블을 읽기 전용으로 접근할 때는 소유 도메인의 공개 UseCase를
경유하는 것을 기본으로 한다. 부분 매핑은 트랜잭션 요구사항상 불가피한 쓰기·락에 한해
별도 ADR과 도메인 소유자 합의를 거친 명시적 예외로만 둔다.**

"쓰기·락이면 부분 매핑을 써도 된다"는 일반 허용 규칙이 아니다 — 위에서 봤듯 쓰기·락도
공개 UseCase 뒤에서 성립한다. 부분 매핑을 남기려면 그 도메인이 왜 소유 도메인의 공개 계약
뒤로 갈 수 없는지를 매번 근거로 제시하고 소유 도메인(P2)과 합의해야 한다.
판단 기준은 "도메인 개수"나 "기존 관례"가 아니다.

### 1. `subscription` — 공개 UseCase로 통일 (이 이슈에서 수행)

- account에 `WithdrawableAccountQueryUseCase.findWithdrawable(accountId, customerId)`를 추가한다.
  존재·소유·`ACTIVE`·출금계좌 등록을 모두 만족할 때만 값을 반환하고, 그 외는 사유를 구분하지 않고
  빈 `Optional`이다. 어떤 오류코드로 응답할지는 호출 도메인이 정한다(`subscription`은 `ACC0201`).
- `AccountLookupAdapter`가 이 UseCase를 호출하도록 바꾸고 `SubscriptionAccountJpaEntity` /
  `SubscriptionAccountJpaRepository`를 삭제한다.
- `subscription`의 `AccountLookupPort` / `WithdrawableAccount`(port/out)는 그대로 둔다 —
  account의 타입이 `subscription`의 application 계층으로 새지 않게 어댑터에서 매핑한다.
  `AccountNumberQueryAdapter`(#177)와 같은 형태다.

### 2. `transfer` — 부분 매핑 유지 (명시적 예외)

`AccountLockJpaEntity`는 그대로 둔다. 근거는 "선례"도 "트랜잭션상 불가능"도 아니라
**`lockForTransfer` → `applyTransfer` 계약을 account 공개 API 뒤로 옮기려면 별도 재설계가
필요하고 그 범위를 이번에 다루지 않는다**는 점이다. 현재 유일하게 승인된 예외이며,
재설계 시점에 다시 판단한다.

### 3. `autotransfer` / `scheduledtransfer` — 후속 이슈로 분리

읽기 전용이므로 이 결정의 대상이지만, 두 도메인은 `@Profile("prod")` 어댑터 + Mock 이중 구현
구조라 전환 시 프로파일 설계를 함께 봐야 한다. 이번 PR에 묶으면 타 담당자 코드를 대량으로
건드리는 리뷰 불가능한 크기가 된다. 별도 이슈로 옮긴다.

## 검토한 대안

### (A) 부분 매핑 유지 — 현행 규약을 팀 표준으로 확정

변경량은 가장 적다. 그러나 "활성인가 / 본인 소유인가 / 출금계좌로 등록됐는가"라는 동일한 판정이
도메인마다 복제된다 — 실제로 `autotransfer`와 `scheduledtransfer`의 `AccountStatusAdapter`에
`isActiveAccount` / `belongsToCustomer` / `isWithdrawalRegistered`가 그대로 중복돼 있다.
소유 도메인의 내부 스키마 변경이 호출 도메인에 그대로 전파된다 — `account`가 컬럼을 바꾸면
부분 매핑을 가진 도메인마다 Hibernate `validate` 기동 실패로 드러나고, 컴파일 시점에는 알 수 없다.
심사 중이던 PR #265를 상당 부분 되돌려야 한다는 점도 있다. 기각.

### (B) 4개 도메인 동시 전환

형태상의 일관성은 얻지만 `transfer`가 애초에 성립하지 않아 "동시 전환"이 불가능하다.
`autotransfer` / `scheduledtransfer`는 프로파일 이중 구현 때문에 별개의 설계 판단이 필요하다.
일관성을 이유로 한 PR에 묶으면 리뷰 품질이 떨어진다. 기각(범위만 분리, 방향은 채택).

## 결과

- `subscription`이 `account`를 읽는 경로가 공개 UseCase 하나로 통일된다.
- `account` 공개면에 `WithdrawableAccountQueryUseCase`가 추가되고, 출금계좌 판정 규칙이
  소유 도메인 한 곳에만 남는다.
- `team_db_architecture_guide.md` §3-①에 판단 기준(읽기 전용 → 공개 UseCase가 기본, 타 도메인
  테이블 쓰기·락 → 별도 ADR·소유자 합의를 거친 명시적 예외)이 기록되어 다음 도메인이 같은
  고민을 반복하지 않는다.
- `autotransfer` / `scheduledtransfer` 전환은 후속 이슈로 남는다. 남겨둔 부분 매핑은
  "아직 안 바꾼 것"이지 "표준"이 아니다.
