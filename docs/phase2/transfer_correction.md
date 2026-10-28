# 이체 정정 체인 · 자동 재시도 금지 (PH-80)

> **정본입니다.** 이미 기표된 이체를 바로잡는 방법과, 결과를 모르는 이체를 다루는 규칙은 이 문서를 따릅니다.
> 태스크 범위·기한은 [tasks.md](tasks.md) §PH-80, 용어는 [glossary.md](glossary.md) 9번 처리 불명 · 10번 조회거래 · 11번 정정 체인입니다.
>
> **대상**: P4 PH-33-②(타행 조회거래) · PH-36(보상 트랜잭션 · 자금 정합성 §5), 이체·원장 경로를 고치는 모든 PR의 리뷰어

---

## 1. 규칙 한 줄

**기표된 이체는 지우지도 고치지도 않고, 정정 체인으로만 바로잡습니다. 결과를 모르는 이체는 다시 실행하지 않고 조회로 확인합니다.**

## 2. 정정 체인

30,000원을 보내려다 50,000원을 보낸 경우입니다.

```
① 원거래   A → B 50,000원   지우지 않는다. invalidated_at만 찍는다 (status는 SUCCESS 그대로)
② 취소정정 B → A 50,000원   correction_type = REVERSAL, ref_transfer_id = ①
③ 정상거래 A → B 30,000원   correction_type = REPOST,   ref_transfer_id = ①
```

| | 취소정정 (`REVERSAL`) | 정상거래 (`REPOST`) |
|---|---|---|
| 하는 일 | 원거래를 없던 일로 되돌린다 | 원래 하려던 거래를 새로 한다 |
| 방향·금액 | 원거래와 반대 방향, 같은 금액 | 원거래와 같은 두 계좌, 고친 금액 |
| 원장 | `REVERSAL` 2행이 `reversal_id`로 같은 계좌의 원거래 행을 가리킨다. 원거래 2행은 `reversed = true` | 보통 이체와 같은 기표 |
| 조건 | 원거래가 SUCCESS이고 아직 무효화되지 않음. 취소정정 거래 자체는 다시 취소할 수 없다 | 원거래가 이미 취소정정됨 |
| 언제 쓰나 | 항상 (타행 미처리 확인처럼 이것만 필요한 경우도 있다) | 의도한 거래가 따로 있을 때만 |

- **DELETE는 0건입니다.** 원장에서 바뀌는 값은 원거래 행의 `reversed` 하나뿐입니다([schema_reference.md](../schema_reference.md) `ledger_entry` APPEND-ONLY 예외).
- **원거래 하나에서 체인 전체를 찾습니다.** `SELECT * FROM transfer WHERE ref_transfer_id = :원거래ID`
- **정상거래도 틀렸으면** 그 정상거래를 원거래로 삼아 같은 방법으로 다시 정정합니다.
- **같은 원거래에 취소정정·정상거래는 각각 한 번만** 생깁니다. `uk_transfer_correction (ref_transfer_id, correction_type)`이 막습니다. 동시 요청은 락 전에 읽은 스냅샷으로 앞선 검사를 통과할 수 있어서, 코드 검사가 아니라 이 제약이 최종 방어선입니다.

**호출법** — `transfer.application.port.in.TransferCorrectionUseCase`

```java
reverse(원거래 거래번호)            // 취소정정
repost(원거래 거래번호, 고친 금액)   // 정상거래 — reverse 뒤에만
```

- 한 호출이 한 트랜잭션입니다. 실패하면 전부 롤백되고 원거래는 유효한 채로 남습니다.
- 채널은 `BT`이고 이체 한도를 차감하지 않습니다. HTTP 경로는 없습니다.
- 거부 사유: `TRF0202` 원거래 없음 · `TRF0305` 정정할 수 없는 이체(조건 위반·이미 정정됨) · `TRF0306` 받은 계좌 잔액이 부족해 되돌릴 수 없음 · `TRF0303` 정상거래 출금 잔액 부족 · `TRF0304`/`TRF0301` 계좌 정지·해지

## 3. 자동 재시도 금지

**돈을 움직이는 호출은 결과를 모를 때 코드가 다시 부르지 않습니다.** 첫 호출이 실제로는 처리됐는데 응답만 잃은 경우, 다시 부르면 같은 돈이 두 번 나갑니다.

**허용되는 것**

| 경우 | 왜 괜찮은가 | 예 |
|---|---|---|
| 결과를 **조회**해서 확정 | 돈을 다시 움직이지 않는다 | 자동이체 재확정 배치 `reconcileStuckExecution` — `transfer` 행을 찾아 상태만 확정한다 |
| 돈이 움직이기 **전** 단계의 DB 경합 재시도 | 실패해도 잔액·원장에 흔적이 없다 | 채번 `SequenceGenerator`의 당일 첫 행 경합 재시도 |
| 멱등키로 **저장된 결과를 돌려주기** | 다시 실행하지 않고 이전 결과를 읽는다 | 예약·자동이체 `uk_transfer_source_execution_date` 사전조회 |

**금지되는 것**

- `TransferExecutionService.execute()`, `AccountLockPort.applyTransfer()`, 대외 전문 송신을 `@Retryable`·루프·스케줄러로 다시 부르기
- ERROR나 결과 불명인 이체를 같은 의도로 **자동** 재실행하기 — 다시 보낼지는 고객이나 운영자가 새 거래로 정한다
- 정정을 위해 `transfer`·`ledger_entry` 행을 UPDATE(정해진 컬럼 밖)·DELETE하기

**결과를 모를 때 할 일**

```
응답 없음 → 처리 불명(TIMEOUT)으로 기록 → 조회거래(PH-33)로 상대 결과 확인
   ├─ 처리됨    → 완료로 확정
   └─ 처리 안 됨 → 취소정정(reverse)으로 돈을 돌려준다
```

## 4. 리뷰 체크 항목

이체·원장·전문 경로를 고치는 PR에서 리뷰어가 확인합니다.

- [ ] 돈을 움직이는 호출(`execute`·`applyTransfer`·전문 송신)을 감싸는 재시도(`@Retryable`, 루프, 재실행 스케줄)가 없다
- [ ] 결과를 모르는 경우를 실패(ERROR)로 단정하지 않고 조회로 확인하는 경로가 있다
- [ ] `transfer`·`ledger_entry`에 DELETE가 없고, UPDATE는 정해진 컬럼(`transfer`의 상태 확정·`invalidated_at`, `ledger_entry.reversed`)뿐이다
- [ ] 정정이 필요하면 `TransferCorrectionUseCase`를 쓰고, 원장 반대기표를 직접 만들지 않는다
- [ ] "이미 처리됐나"를 조회로만 막지 않고 유니크 제약이나 락으로 막는다 (TOCTOU)
- [ ] 원장·잔액 시각을 계좌 락을 쥔 뒤에 찍는다 (`occurred_at` 순서 계약, #545)

## 5. 타행 장애 뒤 자금 정합성 (PH-36)

상대 은행이 늦게 답하거나, 답하지 않거나, 같은 답을 두 번 보내도 돈이 새거나 두 번 움직이지 않았는지 아래 4가지로 봅니다. 타행 원장은 고객 출금 1행만 남고, 실패로 확정되면 되돌림 입금 1행이 붙습니다([gl_journal_patterns.md](gl_journal_patterns.md) §3-5). `10200` 미결제타점권은 송신 때 대변으로 쌓였다가 확정되면 0이 됩니다.

| | 검사 | 깨지면 |
|---|---|---|
| ① | 계좌 잔액 = 그 계좌 마지막 원장 `balance_after` | 원장 없이 잔액만 바뀌었다 |
| ② | 거래별로 고객에게서 빠진 돈(출금 − 되돌림) = SUCCESS·TIMEOUT이면 송금액, ERROR면 0 | 실패했는데 돈을 안 돌려줬거나, 성공했는데 돌려줬다 |
| ③ | 거래별 `10200` 순잔액 = TIMEOUT이면 −송금액, 확정됐으면 0 | 확정이 두 번 됐거나, 확정됐는데 미결제가 남았다 |
| ④ | 출금은 거래번호당 최대 1행, 되돌림은 송신 뒤 실패(ERROR이면서 출금 있음)에만 정확히 1행 | 보상이 두 번 일어났다 |

- **테스트**: `InterbankFaultScenarioTest`가 장애(응답 지연 · 무응답 · 중복응답 · 중복거래 `2001`)를 송신·조회 단계에 넣어 끝까지 돌리고, 중간과 끝에서 `FundIntegrityCheck`로 위 4가지를 봅니다. 테스트는 시작 잔액을 찍어 두므로 ②를 "시작 잔액 − 성공 = 현재 잔액 + 미결제"로 한 번에 봅니다.
- **운영·측정 데이터**: 아래 SQL을 씁니다. 시작 잔액을 알 수 없어서 ②를 거래 단위로 봅니다. ①이 맞으면 거래별 ②를 모두 더한 것이 테스트의 ②와 같습니다.
- `perf/sql/verify_integrity.sql`은 당행 이체 전용입니다. "이체 1건 = 원장 2행"으로 짝을 보므로 타행 성공 건을 모두 깨진 짝으로 셉니다.
- TIMEOUT이 남아 있어도 ③이 그만큼을 기대하므로 판정할 수 있습니다. 다만 같은 날 한도 적립은 TIMEOUT 금액만큼 성공 금액보다 큽니다.

```sql
-- 측정 구간. 타행 건(payee_bank_code IS NOT NULL)만 본다. (1)~(4)는 모두 0행이어야 한다.
SET @from_at = '2026-11-02 00:00:00';
SET @to_at   = '2026-11-03 00:00:00';

-- (1) 계좌 잔액 = 마지막 원장 balance_after
SELECT a.account_id, a.balance, le.balance_after
FROM account a
JOIN ledger_entry le
  ON le.ledger_entry_id = (SELECT MAX(l2.ledger_entry_id) FROM ledger_entry l2 WHERE l2.account_id = a.account_id)
WHERE a.account_id IN (
    SELECT withdrawal_account_id FROM transfer
    WHERE payee_bank_code IS NOT NULL AND created_at >= @from_at AND created_at < @to_at)
  AND a.balance <> le.balance_after;

-- (2) 고객에게서 빠진 돈 = 성공·처리 불명이면 송금액, 실패면 0
SELECT t.transaction_number, t.status, t.amount,
       COALESCE(SUM(CASE WHEN le.direction = 'WITHDRAWAL' THEN le.amount ELSE -le.amount END), 0) AS net_out
FROM transfer t
LEFT JOIN ledger_entry le ON le.transaction_number = t.transaction_number
WHERE t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.transaction_number, t.status, t.amount
HAVING net_out <> CASE WHEN t.status IN ('SUCCESS', 'TIMEOUT') THEN t.amount ELSE 0 END;

-- (3) 거래별 10200 순잔액 = 처리 불명이면 −송금액, 확정됐으면 0
SELECT t.transaction_number, t.status, t.amount,
       COALESCE(SUM(CASE WHEN e.dr_cr = 'DEBIT' THEN e.amount ELSE -e.amount END), 0) AS unsettled_net
FROM transfer t
LEFT JOIN gl_voucher v
       ON v.reference_key = t.transaction_number
      AND v.tx_type IN ('INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT')
LEFT JOIN gl_journal_entry e ON e.voucher_no = v.voucher_no AND e.account_code = '10200'
WHERE t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.transaction_number, t.status, t.amount
HAVING unsettled_net <> CASE WHEN t.status = 'TIMEOUT' THEN -t.amount ELSE 0 END;

-- (4) 출금은 최대 1행, 되돌림은 송신 뒤 실패에만 정확히 1행
SELECT t.transaction_number, t.status,
       COALESCE(SUM(le.direction = 'WITHDRAWAL'), 0) AS withdrawals,
       COALESCE(SUM(le.direction = 'DEPOSIT'), 0)    AS refunds
FROM transfer t
LEFT JOIN ledger_entry le ON le.transaction_number = t.transaction_number
WHERE t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.transaction_number, t.status
HAVING withdrawals > 1
    OR refunds <> CASE WHEN t.status = 'ERROR' AND withdrawals = 1 THEN 1 ELSE 0 END;

-- 참고. 리포트에 적을 건수 — 판정은 아니다
SELECT t.status, COUNT(*) AS transfers, SUM(t.amount) AS amount
FROM transfer t
WHERE t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.status;
```

## 6. 범위 밖 · 후속

| 항목 | 지금 | 후속 |
|---|---|---|
| 정정 알림 | 정정 거래는 `TransferSettled`를 발행하지 않는다. 발행하면 "이체 완료" 알림으로 나간다 | 정정 전용 이벤트·알림을 P1과 정한다 |
| 이체내역 화면 | 원장 거래내역은 취소된 행을 숨기지만, 이체내역(`transfer` 기준)은 원거래·정정 거래를 모두 보여 준다. 지금은 정정을 부르는 API가 없어 화면에 나오지 않는다 | 화면 표시 방식을 FE와 정한다 |
| 원장 거래유형 매핑 | `TransferType.ledgerTransactionType()` 한 곳에 둔다(#564에서 `TransferExecutionService` 사본을 지움) | — |
| TIMEOUT | `ProcessResultStatus.TIMEOUT`과 `Transfer.timeout()`(PROCESSING → TIMEOUT)만 있다. TIMEOUT에서 `complete()`·`fail()`은 막힌다(#564) | 조회거래로 SUCCESS·ERROR를 확정하는 전이와 호출자는 PH-33-② |
| TIMEOUT 집계 | 이력 요약·월별 통계(`successCount`·`failureCount`)는 TIMEOUT 건을 세지 않는다. 지금은 TIMEOUT을 만드는 경로가 없어 0건이다 | PH-33-②에서 "확인 중" 건수를 둘지 FE와 정한다 |
