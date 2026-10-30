# 이체 파이프라인 확장점(seam) (PH-99)

> **정본입니다.** `TransferExecutionService.execute()`에 다른 트랙이 기능을 붙이는 규칙은 이 문서를 따릅니다.
> 태스크 범위·기한은 [tasks.md](tasks.md) §PH-99, 이벤트·아웃박스 규칙은 [README.md](README.md) §3-3입니다.
>
> **대상**: P2 PH-90(훅 A) · P3 PH-24(훅 B) · P5 PH-41(tradeDate) · P1 PH-32·PH-96(이벤트·아웃박스) · P2 PH-14·PH-15·PH-16(입금 계약, §8) · P4 PH-06·P6 FDS(훅 C 이체 보류, §9)

---

## 1. 규칙 한 줄

**`execute()`는 P4만 고칩니다.** 다른 트랙은 `transfer.api`의 인터페이스를 구현한 빈만 등록합니다.
빈을 등록하면 스프링이 `List<...>`로 모아 `TransferExecutionService`에 주입하므로 `execute()`를 열 필요가 없습니다.

## 2. 흐름과 자리

코드 순서 그대로입니다. `[훅]`은 지금 동작하는 자리, `[자리]`는 주석으로만 표시된 자리입니다.

```
execute(command)
 ├─ 멱등성 사전조회 (SCHEDULED·AUTO)
 ├─ verifyBeforeLock      소유 · 동일계좌 · 상품유형 · 계좌비밀번호 토큰
 ├─ 채번 → draft 보관      ← 여기부터 실패하면 ERROR 행을 남긴다. Transfer는 거래일을 읽은 트랜잭션이 만든다
 ├─ preCheckWithoutLock   잔액·상태 스냅샷 (OTP 있는 즉시이체만)
 ├─ [훅 A] TransferPreCheck 체인 (order 오름차순)
 ├─ consumeOtpAuthToken
 └─ postLedger — REQUIRES_NEW 트랜잭션 {
       tradeDate = BusinessDateGate.acquireSharedAndReadBusinessDate() → Transfer 생성   ← 가장 먼저, 한 건에 한 번 (PH-41, #516, #662)
       한도 적립                ← 고객 단위 행 락. 커밋까지 쥔다
       [훅 C] TransferHoldPolicy 체인 (즉시이체만, #771 PR-b에서 연결)   ← HOLD면 이 트랜잭션을 롤백하고 보류 저장 (§9)
       계좌 락 → 재검증 → transfer INSERT → 잔액 UPDATE
         [자리] PH-90 재검증을 출금가능액(잔액 − 보류액) 기준으로 바꾼다
       executedAt 캡처
       원장 2행 저장
       [훅 B] LedgerPostingHook.afterLedger(ctx)
       transfer.complete
       transfer 저장
       publishEvent(TransferSettled)   ← 즉시·예약·자동 모두. P1 #505, 이후 P4 소유
    }
 실패 시 failTransfer() — REQUIRES_NEW {
       tradeDate = gate로 읽기 → Transfer 생성
       ERROR 행 저장
       publishEvent(TransferSettled)   ← status=ERROR
    }
```

## 3. 훅 표

| 자리 | 계약 | 등록자 | 트랜잭션 | 실패하면 |
|---|---|---|---|---|
| 훅 A | `TransferPreCheck { int order(); void check(TransferPreCheckContext) }` | P2 PH-90 | 없음. 락 이전, 읽기 전용 | `BusinessException` → 이체 ERROR 확정. OTP·한도 소모 없음. 계좌비밀번호 토큰은 `verifyBeforeLock`에서 이미 소비돼 재시도 시 재인증 필요 |
| 훅 B | `LedgerPostingHook { void afterLedger(LedgerPostingContext) }` | P3 PH-24 | 원장을 기표한 트랜잭션 안. 이체는 REQUIRES_NEW, 상품가입·정정은 호출자 트랜잭션 | 원장·잔액·한도 적립까지 롤백 → 이체 ERROR 확정. 상품가입·정정은 예외가 호출자에게 전파된다 |
| 훅 C | `TransferHoldPolicy { int order(); TransferHoldDecision decide(TransferHoldContext) }` | P4 PH-06 지연이체(#772) · P6 FDS | 기표 REQUIRES_NEW 안, 한도 적립 뒤. 고객 락을 쥔 채 읽기만 | 예외 → 이체 ERROR 확정. HOLD → 기표 트랜잭션 롤백 후 보류 저장(§9) |
| tradeDate | `business.api.BusinessDateGate` | 제공 P5 PH-41·#556, 대입 P4 | 기표·ERROR 기록 트랜잭션 안 | — |
| 확정 이벤트 | `TransferSettled` — 성공·실패를 `status`로, 이체 종류를 `txType`으로 구분 | 타입·페이로드 P1 PH-32. 발행 코드는 P1이 #505에서 넣었고(EVT-2 대신) | 성공은 기표 REQUIRES_NEW 안, 실패는 `failTransfer()` REQUIRES_NEW 안 | 리스너 예외 → 이체 롤백 |

transfer 행이 생기기 전의 사전검증 실패는 엔진이 발행하지 않습니다. 즉시이체는 고객이 API 응답으로 오류를 받고, 예약·자동이체는 배치가 거래번호 없는 결과일 때와 재확정이 transfer 행을 찾지 못했을 때만 `ScheduledTransferSettled`·`AutoTransferExecutionSettled`를 발행합니다.

컨텍스트 레코드는 원시 타입만 담습니다. `api` 패키지는 ArchUnit에서 다른 계층을 참조할 수 없어 `TransferCommand`를 넘기지 않습니다.

| 레코드 | 필드 |
|---|---|
| `TransferPreCheckContext` | `customerId` · `withdrawalAccountId` · `amount` |
| `TransferHoldContext` | `customerId` · `withdrawalAccountId` · `depositAccountNumber` · `amount` · `requestedAt` · `flowId`(없으면 null) |
| `LedgerPostingContext` | `transactionNumber` · `txType`(원장 `transaction_type` 값 — `IMMEDIATE_TRANSFER`·`SCHEDULED_TRANSFER`·`AUTO_TRANSFER`·`PRODUCT_SUBSCRIPTION`·`REVERSAL`) · `amount` · `fromAccountId` · `toAccountId` · `tradeDate` |

## 4. 구현체 예시

```java
@Component
class AvailableBalancePreCheck implements TransferPreCheck {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void check(TransferPreCheckContext context) {
        if (/* 출금가능액 < context.amount() */) {
            throw new BusinessException(/* 도메인 ErrorCode */);
        }
    }
}
```

구현체는 소유 도메인 쪽에 둡니다. `transfer`의 `application`·`domain`·`adapter`는 참조하지 않습니다(AGENTS.md 규칙 2).

## 5. 정책

1. **훅 A는 모든 이체 경로에서 돕니다.** 즉시이체뿐 아니라 예약·자동이체 배치도 지납니다. OTP 유무와 무관합니다. 배치 이체를 거부하면 그 ERROR 행이 멱등키(`sourceId`+`executionDate`)로 남아, 같은 회차를 다시 실행해도 새로 처리하지 않고 그 ERROR 결과를 돌려받습니다.
2. **훅 A의 거부는 `BusinessException`으로만 합니다.** 그 밖의 예외는 내부 오류로 ERROR 확정 후 호출자에게 다시 던집니다.
3. **훅 A는 락을 잡지 않습니다.** 최종 판정은 락 이후 재검증입니다. 훅 A는 "통과 못 할 요청을 싸게 먼저 거르는" 자리입니다.
   **출금가능액의 최종 판정은 P4가 락 이후 재검증에 넣습니다.** 훅 A만으로는 검사와 출금 사이에 보류액이 바뀌는 경우를 막지 못합니다. `account.hold_amount`가 PH-90에서 생기므로, PH-90 머지 때 P4가 락으로 읽은 행의 `잔액 − 보류액`으로 검사를 바꿉니다. 보류액을 바꾸는 쪽(P2)도 계좌 행 락을 잡아야 이 검사가 유효합니다.
4. **훅 B는 예외를 삼키지 않습니다.** GL 기표 실패는 이체 실패입니다. 이벤트가 아니라 동기 호출인 이유는, 다른 커밋에 들어가면 "원장은 있는데 전표가 없는" 상태가 정상이 되기 때문입니다.
   **훅 B는 같은 트랜잭션의 DB 쓰기만 합니다.** 롤백이 되돌리는 건 DB뿐이라 외부 호출은 아웃박스(정책 8)로 넘깁니다. 훅 B 실패 시 즉시이체는 OTP가 이미 소비돼 재인증이 필요하고, 배치 이체는 정책 1처럼 ERROR 결과가 멱등키로 남습니다.
5. **훅 B는 `order()`가 없습니다.** 등록자가 P3 하나뿐입니다. 둘 이상이 필요해지면 P4와 순서 규칙을 먼저 정합니다.
   **훅 B는 원장이 생기는 모든 자리에서 부릅니다**(PH-24). `TransferExecutionService`(즉시·예약·자동) 외에 `ProductSubscriptionDepositService`(상품가입 초입금)와 `TransferCorrectionService`(취소정정 `REVERSAL`·정상거래는 원거래 유형)가 원장 저장 직후 부릅니다. 원장을 새로 쓰는 경로를 만들면 훅 B 호출도 함께 넣어야 합니다 — GL 은 모르는 `txType` 을 예외로 거부합니다(`GLA9009`).
   **예외: 타행이체(PH-33-②, #654)는 `execute()`를 지나지 않고 훅 B 도 부르지 않습니다.** 상대 계좌가 당행에 없어 원장이 1행이고 분개가 예수금↔미결제타점권이라, 이자처럼 `InterbankTransferService` 가 같은 트랜잭션에서 `gl.api` 를 직접 부릅니다([분개 패턴표](gl_journal_patterns.md) §3-5). 훅 A(`TransferPreCheck`)는 타행도 지납니다.
6. **tradeDate는 기표 트랜잭션 맨 앞에서 gate로 한 번만 읽어 transfer에 싣습니다(#516, #662).** 트랜잭션 밖에서 `today()`로 미리 읽으면 락을 기다리는 사이 영업일이 넘어가 전환 뒤에 이전 영업일로 커밋됩니다(#648). 원장(`ledger_entry.trade_date`, #472)·훅 B 컨텍스트는 그 transfer에서 꺼내 씁니다. 락 순서는 gate → 한도 → 계좌입니다. 한 이체 안에서 두 번 구하면 마감 경계에서 원장과 전표의 거래일이 갈릴 수 있습니다(같은 트랜잭션의 재호출은 첫 값을 돌려줍니다).
   **ERROR 행의 거래일도 `failTransfer()` 트랜잭션이 gate로 읽습니다.** 롤백된 기표 트랜잭션의 값을 쓰지 않으므로, 기표 실패와 기록 사이에 영업일이 넘어가면 ERROR 행은 다음 영업일을 받을 수 있습니다(원장이 없어 cutoff 보장과 무관). gate 대기가 락 대기 시간을 넘겨 기록까지 실패하면 ERROR 행 없이 예외가 전파됩니다. 상품가입 입금·입금 계약·정정도 메서드마다 한 번만 구하고, 정정 거래는 원거래일이 아니라 정정한 날의 영업일입니다.
7. **이벤트는 활성 트랜잭션 안에서 발행합니다.** `@TransactionalEventListener`는 트랜잭션 밖 발행을 받지 않습니다. `AFTER_COMMIT` 리스너는 두지 않습니다(README §3-3).
8. **아웃박스 리스너는 `BEFORE_COMMIT` + `JdbcTemplate` INSERT입니다.** INSERT가 실패하면 이체도 롤백됩니다. 의도된 동작입니다.

## 6. 머지 순서 (강제)

```
PH-99 (10/2) → PH-80 (10/8) → PH-90 → PH-24 → PH-41 대입 (10/12~16) → PH-33-② (10/23)
```

PH-90·PH-24 PR은 **10/12까지 리뷰 가능한 상태**로 올립니다. 이 순서를 벗어나 `execute()`를 고치는 PR은 P4가 리뷰에서 막습니다.

훅 C는 #771 PR-b(계약·호출 자리, 10/29) → #772 PR-a(보류 저장·실행) → #772 PR-b(지연이체) 순서로 머지하고, 모두 릴리스 3(10/30)에 싣습니다. FDS 구현체는 #772 PR-a 뒤에 머지합니다.

## 7. 테스트

| 테스트 | 고정하는 것 |
|---|---|
| `TransferExecutionServiceHookTest` | 훅 A 거부 시 ERROR·OTP·한도 미소모, order 순서 / 훅 B 예외 시 전체 롤백·예외 전파, 컨텍스트 값·활성 트랜잭션 |
| `TransferSeamOutboxFlushTest` | 훅 B 빈의 이벤트를 BEFORE_COMMIT 리스너가 받아 INSERT하면 이체와 같은 커밋에 실림 / 리스너 예외 시 이체·INSERT 함께 롤백 |

`TransferSeamOutboxFlushTest`는 아웃박스 테이블이 생기기 전의 골격입니다. PH-96은 프로브 테이블(`seam_outbox_probe`)과 프로브 리스너를 실제 아웃박스로 바꿔 끼웁니다.

## 8. 입금 계약 — 예적금 이자·만기·해지 (#595)

`execute()`의 훅이 아니라 **다른 도메인이 계좌에 돈을 넣는 별도 계약**입니다. P2는 `transfer.api`만 import합니다.
`LedgerPair`·`AccountLockPort`를 직접 쓰지 않습니다.

```java
public interface LedgerDepositUseCase {
    // 상품 계좌 → 고객 계좌, 원장 2행. 만기해지·중도해지
    LedgerDepositResult transferBetween(long fromAccountId, long toAccountId, long amount, DepositType type, DepositChannel channel);
    // 은행 → 계좌, 원장 1행(상대편은 GL 전표). 이자
    LedgerDepositResult credit(long accountId, long amount, DepositType type, DepositChannel channel);
}
record LedgerDepositResult(String transactionNumber, long depositBalanceAfter, LocalDate tradeDate) {}
```

| 메서드 | `type` | 원장 | 돈이 빠지는 계좌 | 돈이 들어가는 계좌 |
|---|---|---|---|---|
| `transferBetween` | `MATURITY`(만기해지) | 2행 | `MATURED`, 잔액 ≥ 금액 | `ACTIVE` |
| `transferBetween` | `TERMINATION`(중도해지) | 2행 | `ACTIVE`, 잔액 ≥ 금액 | `ACTIVE` |
| `credit` | `INTEREST`(이자) | 1행 | — | `ACTIVE`·`MATURED` |

`channel`은 거래 종류가 아니라 누가 시작했는지로 정합니다. COB가 시작하면 `BT`, 고객이 요청하면(중도해지·만기해지) `WB`입니다. 고객이 요청한 해지에서 함께 나가는 이자도 `WB`입니다. 나중에 COB가 만기해지를 자동으로 하면 그 건은 `BT`입니다. 통장 표시는 `만기해지`·`중도해지`·`이자`로 고정합니다.

**거부**

| 상황 | 오류코드 |
|---|---|
| 금액이 0 이하 | `TRF0003` |
| 메서드와 맞지 않는 `type` (예: `credit`에 `MATURITY`) | `CMN0001` |
| 빠지는 계좌가 `ACTIVE`·`MATURED`가 아님 | `TRF0304` |
| 빠지는 계좌 상태가 `type`과 맞지 않음 (예: `ACTIVE`에 `MATURITY`) | `TRF0307` |
| 들어가는 계좌 상태가 위 표와 다름 | `TRF0301` |
| 빠지는 계좌 잔액 부족 | `TRF0303` |
| 계좌가 없음 | `TRF9001` |

**정책**

1. **호출자 트랜잭션에 참여합니다(REQUIRED).** 만기·해지의 상태 전이(`MATURED` → `CLOSED`)와 입금을 한 트랜잭션에 넣으면, 상태 전이가 실패할 때 잔액·원장도 함께 롤백됩니다. 채번만 독립 커밋이라 롤백되면 거래번호가 빕니다.
   **거부되면 호출자 트랜잭션도 롤백 전용이 됩니다.** 예외를 잡고 같은 트랜잭션에서 다음 계좌로 넘어가면 커밋 때 `UnexpectedRollbackException`으로 앞선 건까지 사라집니다. 여러 계좌를 도는 이자 배치는 건별로 트랜잭션을 나눕니다.
2. **원장(`ledger_entry`)까지만 기표합니다.** 전표는 호출자가 같은 트랜잭션에서 `gl.api`로 올립니다. 훅 B(`LedgerPostingHook`)는 부르지 않습니다.
3. **중복은 호출자가 막습니다.** 같은 건을 두 번 부르면 두 번 기표됩니다. 만기·해지는 `CLOSED` 전이가, 이자는 지급 이력이 한 번만을 보장합니다. 돌려받은 `transactionNumber`를 지급 이력에 저장해 원장과 잇습니다.
4. **`credit`(이자)은 계좌의 `last_transaction_at`을 바꾸지 않습니다.** 은행이 넣는 돈은 고객 거래가 아니라, 휴면 판정의 최종 거래일을 늦추면 안 됩니다. `transferBetween`은 고객 계좌로 원리금이 들어가는 거래라 두 계좌 모두 갱신합니다. Apache Fineract도 휴면 판정 최종 거래일을 입금·출금(`transaction_type_enum in (1,2)`)으로만 계산하고 이자 지급(3)은 뺍니다(`SavingsAccountReadPlatformServiceImpl.retrieveSavingsIdsPendingInactive`). PH-16 휴면 전환은 이 전제를 씁니다.
5. **만기 시점 이자는 `credit` → `transferBetween` 순서로 부릅니다.** 상품 계좌에 마지막 이자를 넣은 뒤 원리금을 한 번에 옮깁니다. 이때 상품 계좌 락을 쥔 채 고객 계좌 락을 더 잡으므로 계좌 ID 오름차순 규칙을 벗어날 수 있습니다. 상품 계좌는 고객 계좌보다 늦게 개설돼 ID가 크고, 어긋나도 MySQL이 데드락을 감지해 한쪽을 롤백합니다.
6. **받는 계좌가 누구의 어떤 계좌인지는 호출자가 검증합니다.** 이 계약은 계좌 ID만 받고 고객을 모르므로, `toAccountId`의 상태(`ACTIVE`)만 봅니다. 그 계좌가 같은 고객의 등록 출금계좌인지(PH-15 "원리금은 출금계좌로")는 P2가 가입 정보에서 꺼내 넘기기 전에 확인합니다.
7. **입금 계약을 부른 뒤에 계좌를 처음 읽어 상태를 바꿉니다.** `account` 행을 account의 `AccountJpaEntity`와 transfer의 `AccountLockJpaEntity`가 같은 `version` 컬럼으로 각자 고칩니다. 입금 **전에** 읽은 `Account`로 저장하면 커밋 때 두 UPDATE가 같은 옛 version을 조건으로 나가 낙관적 락 충돌(`ObjectOptimisticLockingFailureException`)이 나고 입금까지 전체 롤백됩니다. 잔액이 조용히 덮이지는 않지만 만기해지가 매번 실패합니다. 순서는 이렇게 둡니다.
   - 금액 계산에 필요한 잔액은 엔티티를 올리지 않는 조회(`AccountPersistencePort.findBalancesByAccountIds` 등)나 가입 정보로 구합니다.
   - 상품 계좌 상태는 이 계약이 락을 잡은 뒤 다시 봅니다(`TRF0304`·`TRF0307`). 호출자가 미리 `Account`를 올려 검사할 필요가 없습니다.
   - 입금 뒤 `Account`를 조회해 `CLOSED`로 바꾸고 저장합니다. 조회 쿼리 전에 transfer 변경이 flush돼 새 version을 읽습니다.

테스트는 `LedgerDepositServiceTest`가 고정합니다 — 호출자 롤백 시 잔액·원장 롤백, `credit` → `transferBetween` 뒤 잔액 = 원장 합계, 거부 8종, 호출자 account 저장 순서 2종(입금 전 조회 → 전체 롤백 / 입금 뒤 조회 → 함께 커밋). `last_transaction_at` 유지는 `AccountLockPersistenceAdapterTest`가 고정합니다.

## 9. 훅 C — 이체 보류 (#771)

즉시이체를 기표하지 않고 원본 요청 그대로 잡아 두는 자리입니다([glossary](glossary.md) 12번 이체 보류). 지연이체(PH-06, #772)와 P6 FDS가 같이 씁니다. O-09 결정은 [README.md](README.md) D-34입니다.
**#771 PR-b는 계약과 호출 자리만 머지합니다.** 구현체가 0개면 모든 요청이 지금처럼 실행됩니다. 보류 저장·실행 부품은 #772 PR-a가 만듭니다.

### 9-1. 자리와 직렬화

```
postLedger — REQUIRES_NEW {
    tradeDate = gate
    한도 적립 checkAndReserve        ← 고객 단위 행 락. 커밋까지 쥔다
    [훅 C] TransferHoldPolicy 체인   ← 즉시이체만. order 오름차순, 처음 HOLD를 낸 구현체가 정한다
        CONTINUE → 계좌 락 → 재검증 → 기표 (기존 그대로)
        HOLD     → 이 트랜잭션 롤백 (한도 적립·transfer 행 없음)
}
HOLD면 REQUIRES_NEW { transfer_hold 저장 → publishEvent(TransferHeld) }
```

- **고객이 요청한 즉시이체에서만 돕니다.** 예약·자동이체와 보류 건 실행(§9-4)은 지나지 않습니다. 타행이체는 `execute()`를 지나지 않아 대상이 아닙니다.
- **같은 고객의 즉시이체는 판정부터 기표 커밋까지 줄을 섭니다.** 한도 적립이 고객 단위 행 락을 커밋까지 쥐기 때문입니다. 그래서 훅 C 안에서 조회한 성공 이력에는 앞선 요청의 결과가 들어 있고, 2번째와 3번째 요청이 동시에 들어와 둘 다 통과하는 일이 없습니다.
- **판정 전 검증은 그대로 먼저 돕니다.** 소유·수취인·잔액 스냅샷·훅 A·한도 검사(`LMT0002`·`LMT0003`)를 통과한 요청만 보류됩니다. 통과 못 할 요청은 보류하지 않고 지금처럼 거부합니다.

### 9-2. 계약 (`transfer.api`)

```java
// 훅 C. 구현체가 0개면 항상 바로 실행한다.
public interface TransferHoldPolicy {
    int order();
    TransferHoldDecision decide(TransferHoldContext context);
}

public record TransferHoldContext(
        long customerId,
        long withdrawalAccountId,
        String depositAccountNumber,
        long amount,
        LocalDateTime requestedAt, // 채번 시각. 시간 창의 기준으로 쓴다
        String flowId) {}          // FE 행동 흐름 식별자. 없으면 null

public record TransferHoldDecision(
        boolean hold, String policyCode, TransferHoldReleaseMode releaseMode, LocalDateTime releaseAt) {
    public static TransferHoldDecision proceed() { ... }                                          // 바로 실행
    public static TransferHoldDecision holdUntil(String policyCode, LocalDateTime releaseAt) { ... } // 지연이체
    public static TransferHoldDecision holdForApproval(String policyCode) { ... }                 // FDS
}

public enum TransferHoldReleaseMode { AT_TIME, ON_APPROVAL }

// 보류 저장과 같은 커밋에 실린다(ADR-0010). 보류 뒤 할 일은 이 이벤트를 받아서 한다.
public record TransferHeld(
        long transferHoldId, long customerId, String policyCode, TransferHoldReleaseMode releaseMode,
        LocalDateTime releaseAt, String flowId, LocalDateTime heldAt) {}

public interface TransferHoldUseCase {
    TransferHoldExecutionResult release(long transferHoldId, long actorId); // 원본 요청 그대로 1회 실행
    void reject(long transferHoldId, long actorId, String reasonCode);     // 관리자 거절
    void cancel(long transferHoldId, long customerId);                      // 고객 취소
}

// 커밋된 SUCCESS만 센다. 현재 요청은 넣지 않는다. 고객의 모든 출금계좌가 기준이다.
public interface RecentTransferQuery {
    RecentTransferAggregate aggregateSucceeded(long customerId, String depositAccountNumber, LocalDateTime since);
    boolean hasSucceededBefore(long customerId, String depositAccountNumber, LocalDateTime before);
}

public record RecentTransferAggregate(int count, long totalAmount, LocalDateTime firstTransferredAt) {}
```

- `flowId`는 `POST /transfers` 요청 본문의 선택 필드로 받고 멱등 지문(fingerprint)에 넣습니다. 이체는 값을 전달만 하고, 그 흐름이 이 고객 것인지는 FDS가 검사합니다.
- `RecentTransferQuery`는 기존 인덱스 `ix_transfer_wacc`(출금계좌, 처리시각)로 고객의 출금계좌마다 읽습니다. `transfer`에 인덱스를 새로 달지 않습니다(§9-5 규칙 6).
- `RecentTransferQuery`는 당행 즉시·예약·자동이체의 SUCCESS 행을 셉니다. 정정 체인으로 무효화된 행(`invalidated_at`)은 뺍니다. `firstTransferredAt`은 "반복 묶음 첫 이체 이전에 보낸 적 있는가"를 `hasSucceededBefore`로 물을 때 씁니다.
- **정책 A가 보는 과거 성공 이체와 `flowId`의 연결은 P6이 소유합니다.** `transfer`에 컬럼을 더하지 않습니다(§9-5 규칙 6). P4는 `TransferSettled`에 nullable `String flowId`를 더하고, P6은 성공 커밋 뒤 이 이벤트를 받는 시점에 성공 이체 ID(`refId`)와 `flowId`의 연결을 자기 테이블에 저장해 반복 묶음 단위로 조회합니다.
    - P4 구현은 PH-103(#747, 같은 이벤트를 고친다) 머지 뒤, S4 측정이 끝난 11/6 이후 첫 PR입니다. FDS는 11/6까지 꺼 두므로 P6은 그 전에 이 시그니처로 리스너를 만들어 둘 수 있습니다.
    - **연결 순서:** 지금 `TransferSettled`에는 `flowId`가 없습니다. P6은 연결 저장 부품을 먼저 준비하고, 이벤트를 읽는 리스너는 P4가 필드를 추가한 PR이 머지된 뒤 연결합니다. P4는 그 PR이 머지되면 P6에 알립니다.
    - **멱등 저장:** 같은 이벤트가 다시 전달될 수 있으므로, P6은 성공 이체 ID(`refId`)를 키로 한 번만 저장합니다. 같은 `refId`가 다시 오면 새 행을 만들지 않습니다.
    - 이벤트는 커밋 뒤 따로 처리되므로, 연달아 들어온 이체에서는 앞 건의 연결이 잠깐 안 보일 수 있습니다. 이때도 정책 B는 `RecentTransferQuery`만으로 판정합니다.
- 이름과 필드는 PR-b 리뷰에서 바뀔 수 있습니다. 바뀌면 이 절을 같은 PR에서 고칩니다.

### 9-3. HOLD일 때 P4가 보장하는 것

| 항목 | 동작 |
|---|---|
| 원장·잔액·한도 | 바뀌지 않습니다. 기표 트랜잭션을 롤백하므로 한도 적립도 취소됩니다 |
| 저장 | 새 트랜잭션에서 원본 요청(출금계좌·수취계좌번호·금액·메모·채널)·요청 지문·인증 완료 시각·정책 코드·해제 방식과 시각·`flowId`를 `transfer_hold`에 저장하고, `TransferHeld`를 같은 커밋에 발행합니다 |
| 토큰 | 계좌비밀번호 토큰과 OTP는 지금처럼 판정 전에 소비합니다. 토큰 원문은 저장하지 않습니다. **#771·#772 초안의 "토큰 소비와 보류 저장을 한 트랜잭션으로"에서 바꾼 계약입니다.** 일반 즉시이체의 인증 순서를 바꾸지 않기 위해서입니다. 보류 저장이 실패하면 기표 중 내부 오류와 같은 경로로 `transfer`에 ERROR 행을 남기고 실패 응답을 돌려줍니다. 토큰은 이미 소비돼서, 같은 멱등 키로 다시 보내도 인증 토큰 오류로 끝나고 보류·기표는 생기지 않습니다. 자동 재시도는 없고, 고객은 다시 인증해 새 요청을 보냅니다 |
| 거래번호 | 판정 전에 받은 거래번호는 쓰이지 않고 빕니다(롤백 때와 같습니다). 실행할 때 새로 받습니다 |
| 응답 | HTTP 200 + `code="0000"`. `data`에 보류 상태와 `transferHoldId`, 지연이체면 실행 예정 시각을 담습니다. `ProcessResultStatus`에는 값을 더하지 않습니다 |
| 확정 이벤트 | `TransferSettled`는 보류 시점에 발행하지 않습니다. 이체 결과가 아직 없어서입니다. 실행될 때 지금처럼 발행됩니다 |

### 9-4. 실행·거절·취소

- 상태는 `HELD → EXECUTING → EXECUTED / FAILED`, `HELD → REJECTED`(관리자 거절), `HELD → CANCELLED`(고객 취소), `HELD → EXPIRED`(만료)입니다. 전이는 조건부 UPDATE라, 같은 보류 건을 두 곳에서 동시에 처리해도 한 곳만 성공합니다.
- **실행은 원본 요청 그대로 `execute()`를 한 번 부릅니다.** `transferType=IMMEDIATE`, `source_type=HELD`, `source_id=보류 ID`, `execution_date=보류한 날짜`입니다. `source_type`은 `VARCHAR(12)`라 12자 이하 값을 씁니다. 컬럼을 넓히면 300만 행 `transfer`에 ALTER가 걸립니다. 기존 `uk_transfer_source_execution_date`가 두 번 실행을 DB에서 막고, 다시 부르면 앞선 결과를 돌려받습니다. 원장 거래유형은 `IMMEDIATE_TRANSFER` 그대로라 GL(훅 B)은 바뀌지 않습니다.
- **실행할 때는 토큰을 받지 않습니다.** 인증은 보류 시점에 끝났습니다. 잔액·한도·계좌 상태와 훅 A는 실행 시점 기준으로 다시 검사하고, 거부되면 `FAILED`로 끝납니다. 자동 재시도는 하지 않습니다.
- **전용 실행 메서드를 따로 두지 않습니다.** `release()`는 토큰을 비우고 `sourceId`에 보류 ID를 넣어 `execute()`를 부릅니다. 계좌비밀번호·OTP 검증은 토큰 값이 있을 때만 돌고(`verifyBeforeLock`·`consumeOtpAuthToken`), 훅 C는 `sourceId`가 없는 고객 즉시이체에만 돌아서(`isCustomerImmediate`) 둘 다 건너뜁니다. 예약·자동이체와 같은 경로입니다. 지금 `resolveSourceType`은 `IMMEDIATE`를 `null`로 돌려주므로, #772가 `sourceId`가 있는 `IMMEDIATE`를 `HELD`로 매핑합니다.
- **`sourceId`가 있다는 것만으로 인증을 건너뛰지 않습니다.** 지금 `TransferCommand`는 즉시이체에 `sourceId`를 금지하고 토큰을 필수로 둡니다. #772는 이 규칙을 아래처럼만 풉니다. HTTP `TransferRequest`는 `sourceId`를 받지 않지만, 서비스를 부르는 다른 경로까지 막기 위해서입니다.
    - `sourceId`가 없는 즉시이체는 지금처럼 두 토큰이 필수입니다.
    - `sourceId`가 있는 즉시이체는 토큰이 없어야 합니다. 토큰과 함께 보내면 거부합니다.
    - `execute()`는 인증을 건너뛰기 전에 `sourceId`의 `transfer_hold` 행이 `EXECUTING`인지(`release()`가 조건부 UPDATE로 선점한 상태), 출금계좌·수취계좌번호·금액이 저장된 원본과 같은지 확인합니다. 아니면 기표 없이 거부합니다.
    - 이 확인은 `sourceId`가 있을 때만 돕니다. 일반 즉시이체 경로에는 쿼리가 늘지 않습니다(§9-5 규칙 6).
- **승인하면 바로 실행합니다.** 지연이체 설정이 함께 해당하는 건이어도 다시 지연하지 않습니다. 승인 실행은 훅 C를 지나지 않고, 승인 과정의 고객 확인 콜이 지연이체의 보호 목적을 채웁니다.
- **`ON_APPROVAL` 건은 보류한 영업일 안에 승인되지 않으면 `EXPIRED`입니다.** 실행할 때 `execution_date`가 보류한 날짜라, 며칠 뒤 승인하면 오래된 요청이 뒤늦게 기표되기 때문입니다. 만료 처리는 FDS 착수 때 구현하고, #772는 `AT_TIME`만 다룹니다. `AT_TIME` 건은 만료하지 않고 실행 시점 검사에서 거부되면 `FAILED`입니다.
- **승인 권한과 콜 기록 검증은 호출자 몫입니다.** `release`·`reject`는 `actorId`와 사유만 남깁니다. 관리자 권한과 콜 결과 입력은 FDS가 부르기 전에 확인합니다.
- Apache Fineract의 maker-checker도 같은 방식입니다. 업무 트랜잭션 안에서 승인 필요를 판정해 롤백하고(`CommandSourceService.validateMakerChecker`), 승인하면 저장해 둔 원본으로 같은 처리를 다시 실행합니다(`PortfolioCommandSourceWritePlatformServiceImpl.approveEntry`). 승인 단계는 하나이고, 승인된 건은 승인 대기 판정을 다시 거치지 않습니다. 비밀값을 지운 요청은 승인 대기로 보낼 수 없게 막아, 비밀값을 저장하지 않습니다.

### 9-5. 구현체 규칙

1. **외부 호출을 하지 않습니다.** 훅 C는 고객 락을 쥔 채 돕니다. Python 서비스 같은 외부 API를 기다리면 그 고객의 다른 이체가 모두 같이 멈춥니다. 같은 DB 읽기(`RecentTransferQuery`, 자기 도메인 테이블)만 합니다. 행동 점수가 필요하면 미리 계산해 저장해 둔 값을 읽습니다.
2. **쓰기를 하지 않습니다.** HOLD면 트랜잭션이 롤백돼 훅 C 안의 쓰기는 사라집니다. 보류 뒤 할 일은 `TransferHeld`를 받아서 합니다.
3. **예외는 이체 실패입니다.** 훅 C가 예외를 던지면 이체는 ERROR로 확정됩니다. 판정을 건너뛰고 통과시키지 않습니다. 켜고 끄는 설정은 구현체가 둡니다(FDS는 11/6 측정이 끝날 때까지 꺼 둡니다).
4. **order가 작은 구현체가 먼저 정합니다.** 처음 HOLD를 낸 구현체가 정하고 나머지는 부르지 않습니다. FDS(승인)가 지연이체(시각)보다 작은 order를 씁니다. 승인을 기다리는 건이 시각이 되었다고 실행되면 안 되기 때문입니다.
5. 구현체는 소유 도메인에 두고 `transfer.api`만 참조합니다(AGENTS.md 규칙 2).
6. **성능 측정을 바꾸지 않습니다.** 릴리스 3 이미지로 S4 재측정(PH-30·PH-36b)을 하므로, 훅 C 때문에 즉시이체 경로가 느려지면 그 비용이 LOCK-TUNE 개선 효과와 섞입니다.
    - 구현체마다 전역 스위치를 둡니다(예: `app.delayed-transfer.enabled`). 꺼져 있으면 DB를 읽지 않고 바로 `proceed()`를 돌려줍니다. 고객별 설정을 조회해야 꺼진 줄 아는 구조로 만들지 않습니다.
    - 구현체가 0개이거나 스위치가 모두 꺼져 있으면 즉시이체 경로에 쿼리·락이 하나도 늘지 않아야 합니다. P4는 구현체가 없으면 컨텍스트도 만들지 않습니다.
    - `transfer`에 인덱스·컬럼을 더하지 않습니다. 이체 INSERT 비용과 운영 ALTER 시간이 함께 바뀝니다.
    - S4 PH-30 재측정 동안 지연이체·FDS 스위치를 끕니다. 재측정이 끝나면 켜고 PH-36b의 지연이체 정상 지급률을 잽니다.

### 9-6. 테스트 (#771 PR-b · #772)

| 고정하는 것 |
|---|
| 구현체 0개면 기존 즉시이체 결과·원장이 그대로다 |
| 스위치가 꺼진 구현체는 DB를 읽지 않는다 |
| HOLD면 원장·잔액·한도 사용액이 그대로고, `transfer_hold` 1행과 `TransferHeld`가 같은 커밋에 남는다 |
| 보류 저장이 실패하면 `transfer` ERROR 1행이 남고 원장·잔액·한도 사용액이 그대로다. 토큰은 소비돼 같은 멱등 키 재요청은 인증 토큰 오류로 끝나고, 보류·기표가 생기지 않는다 |
| 같은 고객의 동시 요청에서 뒤 요청의 훅 C가 앞 요청의 커밋 결과를 본다 |
| 보류 건을 두 번 실행해도 기표는 1회다. 실행 시점 잔액 부족·계좌 정지면 `FAILED`다 |
| 예약·자동이체와 보류 건 실행은 훅 C를 지나지 않는다. 보류 건 실행은 토큰 없이 성공하고 `source_type=HELD`로 남는다 |
| `sourceId`를 채운 즉시이체는 보류 행이 `EXECUTING`이 아니거나(없음·`HELD`·종료) 출금계좌·수취계좌번호·금액이 원본과 다르면 인증 생략 없이 거부되고 기표되지 않는다. 토큰과 `sourceId`를 함께 보내도 거부된다 |

## 10. 범위 밖

- **타행이체 보류** — 타행이체는 `execute()`를 지나지 않습니다(정책 5).
- **예약·자동이체 보류** — 실행 시점에 고객이 화면을 조작하지 않아 판정할 행동이 없습니다.
- **보류 중 한도 예약** — 실행 시점에 다시 검사합니다. 한도를 되돌리는 계약이 아직 없습니다(#657).
- **FDS 판정 근거와 Agent 조사** — 경계만 정합니다. P4는 `transferHoldId`로 원본 요청과 상태를 읽는 조회를 `transfer.api`에 둡니다. A/B 판정 근거·행동 요약·Agent 읽기 Tool은 P6이 소유합니다. 조회 구현은 FDS 착수 때 합니다.
- **보류 종료 이벤트** — 고객 취소·만료를 FDS 사건 종료에 쓰려면 별도 이벤트가 필요할 수 있습니다. FDS 착수 때 P6과 정합니다.
