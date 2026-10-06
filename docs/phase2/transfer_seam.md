# 이체 파이프라인 확장점(seam) (PH-99)

> **정본입니다.** `TransferExecutionService.execute()`에 다른 트랙이 기능을 붙이는 규칙은 이 문서를 따릅니다.
> 태스크 범위·기한은 [tasks.md](tasks.md) §PH-99, 이벤트·아웃박스 규칙은 [README.md](README.md) §3-3입니다.
>
> **대상**: P2 PH-90(훅 A) · P3 PH-24(훅 B) · P5 PH-41(tradeDate) · P1 PH-32·PH-96(이벤트·아웃박스)

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
 ├─ 채번 → created 생성    ← 여기부터 실패하면 ERROR 행을 남긴다
 ├─ preCheckWithoutLock   잔액·상태 스냅샷 (OTP 있는 즉시이체만)
 ├─ [훅 A] TransferPreCheck 체인 (order 오름차순)
 ├─ consumeOtpAuthToken
 └─ postLedger — REQUIRES_NEW 트랜잭션 {
       한도 적립 → 계좌 락 → 재검증 → transfer INSERT → 잔액 UPDATE
         [자리] PH-90 재검증을 출금가능액(잔액 − 보류액) 기준으로 바꾼다
       executedAt 캡처
         [자리] PH-41 tradeDate = businessDateProvider.today()
       원장 2행 저장
       [훅 B] LedgerPostingHook.afterLedger(ctx)
       transfer.complete
       transfer 저장
       publishEvent(TransferSettled)   ← 즉시·예약·자동 모두. P1 #505, 이후 P4 소유
    }
 실패 시 failTransfer() — REQUIRES_NEW {
       ERROR 행 저장
       publishEvent(TransferSettled)   ← status=ERROR
    }
```

## 3. 훅 표

| 자리 | 계약 | 등록자 | 트랜잭션 | 실패하면 |
|---|---|---|---|---|
| 훅 A | `TransferPreCheck { int order(); void check(TransferPreCheckContext) }` | P2 PH-90 | 없음. 락 이전, 읽기 전용 | `BusinessException` → 이체 ERROR 확정. OTP·한도 소모 없음. 계좌비밀번호 토큰은 `verifyBeforeLock`에서 이미 소비돼 재시도 시 재인증 필요 |
| 훅 B | `LedgerPostingHook { void afterLedger(LedgerPostingContext) }` | P3 PH-24 | 이체 REQUIRES_NEW 안 | 원장·잔액·한도 적립까지 롤백 → 이체 ERROR 확정 |
| tradeDate | `business.api.BusinessDateProvider` | 제공 P5 PH-41, 대입 P4 | 같은 트랜잭션 | — |
| 확정 이벤트 | `TransferSettled` — 성공·실패를 `status`로, 이체 종류를 `txType`으로 구분 | 타입·페이로드 P1 PH-32. 발행 코드는 P1이 #505에서 넣었고(EVT-2 대신) | 성공은 기표 REQUIRES_NEW 안, 실패는 `failTransfer()` REQUIRES_NEW 안 | 리스너 예외 → 이체 롤백 |

transfer 행이 생기기 전의 사전검증 실패는 엔진이 발행하지 않습니다. 즉시이체는 고객이 API 응답으로 오류를 받고, 예약·자동이체는 배치가 거래번호 없는 결과일 때와 재확정이 transfer 행을 찾지 못했을 때만 `ScheduledTransferSettled`·`AutoTransferExecutionSettled`를 발행합니다.

컨텍스트 레코드는 원시 타입만 담습니다. `api` 패키지는 ArchUnit에서 다른 계층을 참조할 수 없어 `TransferCommand`를 넘기지 않습니다.

| 레코드 | 필드 |
|---|---|
| `TransferPreCheckContext` | `customerId` · `withdrawalAccountId` · `amount` |
| `LedgerPostingContext` | `transactionNumber` · `txType`(`IMMEDIATE_TRANSFER`·`SCHEDULED_TRANSFER`·`AUTO_TRANSFER`) · `amount` · `fromAccountId` · `toAccountId` · `tradeDate` |

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
6. **tradeDate는 `executedAt` 캡처 줄에서 한 번만 구합니다.** 원장(`ledger_entry.trade_date`, #472)·훅 B 컨텍스트·transfer가 같은 값을 씁니다. 훅 B와 원장이 먼저 필요로 하므로 원장 기표보다 앞에 둡니다. 한 이체 안에서 두 번 구하면 마감 경계에서 원장과 전표의 거래일이 갈릴 수 있습니다. PH-41 전까지 훅 B에는 `executedAt`의 달력일이 들어갑니다.
7. **이벤트는 활성 트랜잭션 안에서 발행합니다.** `@TransactionalEventListener`는 트랜잭션 밖 발행을 받지 않습니다. `AFTER_COMMIT` 리스너는 두지 않습니다(README §3-3).
8. **아웃박스 리스너는 `BEFORE_COMMIT` + `JdbcTemplate` INSERT입니다.** INSERT가 실패하면 이체도 롤백됩니다. 의도된 동작입니다.

## 6. 머지 순서 (강제)

```
PH-99 (10/2) → PH-80 (10/8) → PH-90 → PH-24 → PH-41 대입 (10/12~16) → PH-33-② (10/23)
```

PH-90·PH-24 PR은 **10/12까지 리뷰 가능한 상태**로 올립니다. 이 순서를 벗어나 `execute()`를 고치는 PR은 P4가 리뷰에서 막습니다.

## 7. 테스트

| 테스트 | 고정하는 것 |
|---|---|
| `TransferExecutionServiceHookTest` | 훅 A 거부 시 ERROR·OTP·한도 미소모, order 순서 / 훅 B 예외 시 전체 롤백·예외 전파, 컨텍스트 값·활성 트랜잭션 |
| `TransferSeamOutboxFlushTest` | 훅 B 빈의 이벤트를 BEFORE_COMMIT 리스너가 받아 INSERT하면 이체와 같은 커밋에 실림 / 리스너 예외 시 이체·INSERT 함께 롤백 |

`TransferSeamOutboxFlushTest`는 아웃박스 테이블이 생기기 전의 골격입니다. PH-96은 프로브 테이블(`seam_outbox_probe`)과 프로브 리스너를 실제 아웃박스로 바꿔 끼웁니다.

## 8. 범위 밖

- **입금 계약** — P2 이자·만기·해지 입금용 `transfer.api` 계약(예: `LedgerDepositUseCase`). 10/16까지 별도 PR입니다.
- **지연이체 보류 자리** — 훅 A는 읽기 전용이라 이체를 보류로 돌릴 수 없습니다. 방법은 [README.md](README.md) O-09에서 정합니다.
