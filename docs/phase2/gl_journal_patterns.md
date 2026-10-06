# 분개 패턴표 · 전표번호 채번 (PH-21)

> **정본입니다.** 원장 이벤트를 `gl_voucher`·`gl_journal_entry` 로 옮기는 규칙은 이 표를 따릅니다.
> 새 패턴이 필요하면 소유 트랙이 이 문서를 고치고, 코드가 아니라 이 표를 근거로 씁니다.
>
> **1차 대상**: P6 PH-60b 시드 생성기(분개 600만) — 필요한 것은 §1·§2·§3-1~3-3·§4·§5 입니다.
> 용어(전표·분개·영업일·거래일)는 [glossary.md](glossary.md) 를 따릅니다.
> 테이블 구조는 [schema_reference.md](../schema_reference.md) §9 입니다.

---

## 1. 전표번호 채번

```
yyyyMMdd-TTT-NNNNNN        예) 20260922-TRF-000123
```

| 조각 | 내용 |
|---|---|
| `yyyyMMdd` | **거래일**(`trade_date`). 발생 시각이 아니라 귀속 영업일입니다 |
| `TTT` | 전표유형 3자 — `OPN` / `TRF` / `SUB` / `INT` / `REV` |
| `NNNNNN` | **(거래일, 유형)당 1부터** 증가하는 6자리 0채움 일련번호 |

전체 19자입니다(`voucher_no VARCHAR(20)`).

**일련번호가 6자리인 이유.** PH-60b 는 3개월 동안 **거래 300만 건(= 전표 300만 건 · 분개 600만 줄)**을
넣습니다. 거래 1건이 `ledger_entry` 에는 출금·입금 2행으로 남으므로(`LedgerPair`), 여기서 말하는 300만은
`ledger_entry` 행 수가 아니라 거래 수입니다. 전표는 거래 1건당 1건이라 달력 기준 하루 약 3.3만 · 영업일
기준 약 4.6만 건입니다. 4자리(9,999)로는 첫날부터 넘치고, 5자리(99,999)는
핫스팟 구간(P5 PH-56 이 의도적으로 만드는 집중 구간)에서 여유가 2배뿐입니다.

**유형별로 따로 셉니다.** 같은 날 `TRF` 와 `SUB` 는 각자 1부터 시작합니다. 런타임 기표(PH-24)에서
유형별로 채번하면 단일 카운터 경합이 줄고, 시드 생성기도 유형별로 독립 할당할 수 있습니다.

**서버는 `gl_voucher_sequence` 로 채번합니다**(PH-21). 거래일은 호출자가 넘기는 값이고, 카운터는 기표
트랜잭션과 별도로 커밋하므로 기표가 롤백되면 결번이 생깁니다. 그날 카운터가 없으면 `gl_voucher` 에 이미
있는 가장 큰 번호 다음부터 이어 가므로, **시드는 카운터를 채우지 않아도 됩니다.** 시드는 생성기가 만드는
거래일을 그대로 쓰면 됩니다.

---

## 2. 모든 패턴에 적용되는 규칙

1. **전표 하나 안에서 차변 합계 = 대변 합계.** 이게 깨지면 시산표가 처음부터 맞지 않습니다.
2. **분개 한 줄은 한쪽입니다.** `dr_cr` 이 방향을 말하고 `amount` 는 **항상 양수**입니다
   (`CHECK (amount > 0)` — 0원 분개도 거부됩니다).
3. **`line_no` 는 전표 안에서 1부터.** `UNIQUE (voucher_no, line_no)` · `CHECK (line_no > 0)`.
4. **`trade_date` 를 분개에도 같은 값으로 넣습니다.** 전표와 분개가 복합 FK
   `(voucher_no, trade_date)` 로 묶여 있어 다른 값을 넣으면 INSERT 가 거부됩니다.
5. **전표를 먼저 INSERT 한 뒤 분개를 넣습니다**(FK). 계정과목은 `R__seed_gl_account.sql` 이
   먼저 적재하므로 신경 쓰지 않아도 됩니다.
6. 금액은 **원 단위 정수**입니다(`BIGINT`).
7. **`reference_key` 에 원 거래번호를 넣습니다**(PH-24). 원장 2행이 공유하는 `transaction_number` 이고,
   개시 전표는 `OPENING-{yyyyMMdd}` 입니다. `UNIQUE (tx_type, reference_key)` 라 같은 거래를 두 번 기표하면
   INSERT 가 거부됩니다. 이 키로 원장과 전표를 1:1 로 잇습니다(§5 (7)).

**1번(차대변 일치)은 DB 제약으로 걸지 않습니다**(PH-21 결정). 서버는 전표 도메인(`gl.domain.Voucher`)이
저장 전에 거부하고(`GLA9001`), 저장된 뒤의 불일치는 §5 검증 SQL 로 찾습니다. 이유는 셋입니다.

- MySQL 의 CHECK 는 한 행만 보고 지연 제약이 없어, 여러 줄의 합을 저장 시점에 막을 수 없습니다. 트리거로
  흉내 내려면 전표에 확정 상태를 두고 확정 UPDATE 때 합을 검사해야 해서 기표마다 비용이 늘고 시드도 그
  순서를 따라야 합니다.
- PH-28b 는 편측기표·금액변조를 직접 주입해 탐지율을 잽니다. DB 가 막으면 주입 자체가 안 됩니다.
- Apache Fineract 도 같은 방식입니다 — 차대변 검증은 애플리케이션(`checkDebitAndCreditAmounts`)에만 있고
  DB 제약·트리거가 없습니다.

`V202609221558__create_gl_tables.sql` 머리말의 "차대변 일치를 DB 제약으로 건다"는 이 결정 전에 쓴 문장입니다.

---

## 3. 패턴표

계정코드는 `R__seed_gl_account.sql` 의 15개 중에서만 씁니다.

### 3-1. 개시 잔액 — `OPENING` / `OPN`

장부 시작 시점에 **한 번만** 세웁니다. 상대 계정 없이 잔액만 넣으면 시산표가 처음부터 안 맞습니다.

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `10100` 현금및현금성자산 | DEBIT | 개시 시점 총액 |
| 2 | `20100` 예수금 | CREDIT | 개시 시점 **고객 계좌 잔액 합계** |
| 3 | `30100` 개시잔액 | CREDIT | 1번 − 2번 (은행 자기자본 몫) |

2번이 0이면 그 줄을 만들지 않습니다(0원 분개 금지). 3번도 같습니다.
**1번 ≥ 2번이 전제입니다.** 1번이 2번보다 작으면 3번이 음수가 되어 `CHECK (amount > 0)` 에 걸리므로,
생성기는 개시 전표를 만들기 전에 이 조건을 확인하고 어기면 적재를 중단합니다(시드 입력값 오류).

### 3-2. 당행 이체 — `TRANSFER` / `TRF`

`ledger_entry` 2행(출금·입금)에 전표 1건이 대응합니다.

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `20100` 예수금 | DEBIT | 이체금액 (출금계좌 측) |
| 2 | `20100` 예수금 | CREDIT | 이체금액 (입금계좌 측) |

당행 내부 이동이라 은행 전체의 예수금 총액은 변하지 않습니다. 같은 계정코드가 양변에 서는 것이
정상이며, 시산표에서 `20100` 의 차변·대변 합계가 함께 커집니다.

### 3-3. 상품가입 초입금 — `PRODUCT_SUBSCRIPTION` / `SUB`

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `20100` 예수금 | DEBIT | 초입금액 (기존 출금계좌 측) |
| 2 | `20100` 예수금 | CREDIT | 초입금액 (신규 예적금계좌 측) |

**계정 구성이 3-2 와 같습니다.** 다른 것은 `tx_type` 뿐입니다.

상품가입 초입금은 **고객의 기존 입출금계좌에서 신규 예적금계좌로 옮기는 내부 이동**입니다
(`ProductSubscriptionDepositService` 가 `withdrawalAccountId`·`depositAccountId` 로 `ledger_entry` 2행을
남깁니다 — `LedgerPair.forProductSubscription`, "2행 복식기표 원장 쌍"). 외부에서 현금이 들어오는
거래가 아니므로 차변은 현금성이 아니라 예수금입니다.

> 요구불예수금과 저축성예수금을 계정으로 나누면 두 패턴이 갈리지만, 2차 계정과목 15개에는
> 예수금이 하나뿐이라 나누지 않았습니다. 상품별 계정 매핑(`product_gl_mapping`)은 예수금을 나눌 때
> 도입합니다(2차 범위 밖). FE 시산표 목과 정보계 지표(REQ-ADM-030 "총 예수금 잔액")도 예수금 하나를 전제합니다.

### 3-4. 취소정정 — `REVERSAL` / `REV`

정정 체인([transfer_correction.md](transfer_correction.md))의 취소정정 거래입니다. 원거래 입금계좌에서
원거래 출금계좌로 돈이 돌아가며 `ledger_entry` 에 `REVERSAL` 2행이 남습니다.

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `20100` 예수금 | DEBIT | 원거래 금액 (원거래 입금계좌 측) |
| 2 | `20100` 예수금 | CREDIT | 원거래 금액 (원거래 출금계좌 측) |

계정 구성은 3-2 와 같습니다. **원 전표를 지우거나 고치지 않고** 새 전표를 세웁니다. 참조 키는 원거래가
아니라 취소정정 거래 자신의 거래번호입니다. 정상거래(`REPOST`)는 보통 이체와 같아 3-2 `TRF` 로 기표합니다.

### 3-5. 아직 확정되지 않은 패턴

| 패턴 | 소유 | 시점 | 상태 |
|---|---|---|---|
| 이자 지급 2줄 | **P2** (PH-14) | S2 | 미확정. 차 `50100` 이자비용 / 대 `20100` 예수금, 그리고 차 `20100` 예수금 / 대 `20200` 원천세예수금 두 줄 구조로 예정 |
| 타행 미결제 2패턴 | **P4** (PH-33) | S2 말~S3 | 미확정. `10200` 미결제타점권의 차대 방향을 P4 가 정합니다 |

**PH-60b 는 이 둘을 만들지 않습니다.** `tx_type` CHECK 에 `INTEREST` 는 있지만 패턴이 확정되지
않았고, 타행 미결제는 유형 자체가 아직 없습니다(추가하려면 새 V 파일로 CHECK 를 넓혀야 합니다).

---

## 4. 적재 순서

```
1) R__seed_gl_account.sql       계정과목 15개      (Flyway 가 자동)
2) 개시 잔액 전표 (OPN)          전표 1 → 분개 2~3
3) 거래 전표 (TRF / SUB)         전표 → 분개  ※ 전표가 먼저
```

개시 전표 없이 거래부터 넣으면 시산표가 처음부터 맞지 않습니다.

---

## 5. 자가 검증 SQL

**PH-60b 는 P4 검수를 통과해야 완료**입니다(원장 짝·잔액·분개 차대변). 넘기기 전에 아래를 돌려
0행이 나오는지 확인하면 왕복이 줍니다.

```sql
-- (1) 차대변이 맞지 않는 전표 — 0행이어야 한다
SELECT voucher_no,
       SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END) AS debit_total,
       SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
FROM gl_journal_entry
GROUP BY voucher_no
HAVING debit_total <> credit_total;

-- (2) 장부 전체 차대변 — 두 값이 같아야 한다
SELECT SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END) AS debit_total,
       SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
FROM gl_journal_entry;

-- (3) 줄이 하나뿐인 전표(편측기표) — 0행이어야 한다
SELECT voucher_no FROM gl_journal_entry
GROUP BY voucher_no HAVING COUNT(*) < 2;

-- (4) 전표번호 형식 위반 — 0행이어야 한다
SELECT voucher_no FROM gl_voucher
WHERE voucher_no NOT REGEXP '^[0-9]{8}-(OPN|TRF|SUB|INT|REV)-[0-9]{6}$';

-- (5) 거래일이 전표번호의 날짜와 다른 전표 — 0행이어야 한다
SELECT voucher_no, trade_date FROM gl_voucher
WHERE DATE_FORMAT(trade_date, '%Y%m%d') <> SUBSTRING(voucher_no, 1, 8);

-- (6) 전표번호의 유형(TTT)과 tx_type 이 다른 전표 — 0행이어야 한다
--     ELSE '' 는 매핑에 없는 tx_type 이 NULL 비교로 빠져나가지 않게 한다
SELECT voucher_no, tx_type FROM gl_voucher
WHERE SUBSTRING(voucher_no, 10, 3) <> CASE tx_type
    WHEN 'OPENING'              THEN 'OPN'
    WHEN 'TRANSFER'             THEN 'TRF'
    WHEN 'PRODUCT_SUBSCRIPTION' THEN 'SUB'
    WHEN 'INTEREST'             THEN 'INT'
    WHEN 'REVERSAL'             THEN 'REV'
    ELSE ''
END;

-- (7) 전표가 없는 원장 거래 — 0행이어야 한다(PH-24 이후 기표분)
SELECT DISTINCT le.transaction_number, le.transaction_type
FROM ledger_entry le
LEFT JOIN gl_voucher v ON v.reference_key = le.transaction_number
WHERE le.transaction_type <> 'OPENING'
  AND v.voucher_no IS NULL;
```

`trade_date` 복제본 불일치는 복합 FK 가 막으므로 별도 쿼리가 필요 없습니다.

---

## 알려진 갭

- **FE 시산표 목(`corebank-fe`)의 상품가입 분개가 이 표와 다릅니다.** `차 현금성 / 대 예수금` 으로
  들어가 있는데 위 3-3 이 맞습니다. 차대변은 맞아서 테스트로는 드러나지 않습니다.
  정정은 corebank-fe#158 에서 함께 합니다.
- `gl_voucher.voucher_no` 가 19자 문자열 PK 라 분개 600만 행의 복합 FK 인덱스가 작지 않습니다.
  PH-28 베이스라인(10/8)에서 실측한 뒤 필요하면 대리키 도입을 검토합니다.

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-09-23 | 최초 작성 (PH-21 / #452). 패턴 2종 + 개시 전표, 채번 규칙 확정 |
| 2026-09-27 | PR #491 리뷰 반영 — 거래·전표·분개 수 명시, 개시 전표 1번 ≥ 2번 전제, 검증 SQL (6) 추가 |
| 2026-10-01 | PH-21 구현 — 차대변 DB 제약을 걸지 않기로 결정(§2), 채번 카운터 `gl_voucher_sequence`(§1) |
| 2026-10-07 | PH-24 — 참조 키 규칙(§2-7), 취소정정 패턴(§3-4), 검증 SQL (7). `product_gl_mapping` 은 예수금 분리 시 도입 |
