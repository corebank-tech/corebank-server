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
| `TTT` | 전표유형 3자 — `OPN` / `TRF` / `SUB` / `INT` / `REV` / `IBT` / `IBS` / `MAT` / `TRM` |
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
   INSERT 가 거부됩니다. 이 키로 원장과 전표를 1:1 로 잇습니다(§5 (7)). **예외는 타행이체**입니다 — 원 거래번호
   하나에 송신 요청 `IBT` 와 확정 해소 `IBS` 두 전표가 붙고, 유형이 달라 유일 제약에 걸리지 않습니다(§3-5).

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

### 3-5. 타행이체 미결제 2패턴 — `INTERBANK_TRANSFER` / `IBT` · `INTERBANK_SETTLEMENT` / `IBS`

타행이체(PH-33-②, #654)는 상대 은행 응답을 받기 전에 고객 계좌에서 먼저 출금합니다. 결과가 정해질 때까지 그 돈을
`10200` 미결제타점권에 묶어 두고, 확정되면 비웁니다. `ledger_entry` 에는 고객 계좌 출금 1행만 남고(상대 계좌가
당행에 없다), 실패로 확정되면 같은 거래번호로 되돌림 입금 1행이 붙습니다.

**송신 요청 (`IBT`)** — 출금과 같은 트랜잭션

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `20100` 예수금 | DEBIT | 이체금액 (고객 출금) |
| 2 | `10200` 미결제타점권 | CREDIT | 이체금액 |

**확정 해소 (`IBS`)** — 성공이면 결제로, 실패면 고객 예수금으로 되돌린다

| 결과 | line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|---|
| 성공 | 1 | `10200` 미결제타점권 | DEBIT | 이체금액 |
| 성공 | 2 | `10100` 현금및현금성자산 | CREDIT | 이체금액 (상대 은행에 결제) |
| 실패 | 1 | `10200` 미결제타점권 | DEBIT | 이체금액 |
| 실패 | 2 | `20100` 예수금 | CREDIT | 이체금액 (고객에게 되돌림) |

- 두 전표 모두 **원 거래번호를 참조 키로** 씁니다. 유형이 달라 `(tx_type, reference_key)` 유일 제약에 걸리지 않습니다.
- 처리 불명(TIMEOUT)인 동안은 `IBT` 만 있어 `10200` 이 대변 잔액입니다. 확정되면 그 거래의 `10200` 순잔액이 0이
  됩니다. **`10200` 의 남은 대변 잔액 = 아직 확정되지 않은 타행 송신 금액 합계**입니다.
- 해소 전표의 거래일은 원거래일이 아니라 **확정한 날의 영업일**입니다(정정 거래와 같은 규칙). 조회거래가 다음 날
  확정하면 두 전표의 거래일이 다릅니다.
- `10200` 은 정상잔액이 차변인 자산 계정인데 여기서는 대변으로 먼저 쌓였다가 0이 되는 정산 계정으로 씁니다.
  2차 계정과목 15개에 내국환 채무 계정이 없어 tasks.md 의 지정대로 `10200` 을 썼습니다.
- 송신 요청은 이체 확장점의 훅 B 가 아니라 타행 서비스가 `gl.api` 를 직접 부릅니다(이자 PH-14 와 같은 방식).

### 3-6. 예·적금 이자 지급 — `INTEREST` / `INT` (PH-14, #687)

PH-15 만기해지와 PH-16 중도해지에서 공통으로 사용하는 이자 지급 분개 패턴이다.

이자·원천세 금액은 PH-12의 `InterestResult(gross, tax, net, appliedRate)`를 그대로 사용한다.

- `gross`: 세전이자
- `tax`: 원천징수 세금 합계(소득세 + 지방소득세)
- `net`: 실제 지급할 세후이자
- 정합식: `gross = tax + net`

**원장 기표**

`net > 0`이면 `LedgerDepositUseCase.credit(accountId, net, INTEREST, channel)`로 상품 계좌에 세후이자를 입금한다.

- 원장 `ledger_entry`: 입금(`DEPOSIT`) 1행
- 원장 `transaction_type`: `INTEREST`
- 원장 입금액: `net`
- GL 전표 유형: `GlTxType.INTEREST` (`INT`)
- GL `reference_key`: `LedgerDepositResult.transactionNumber`
- GL `trade_date`: `LedgerDepositResult.tradeDate`

GL 거래일을 별도로 조회하거나 달력 날짜로 계산하지 않는다.

**GL 분개 — 일반과세 이자 지급**

원천세가 있는 경우 전표 1건에 분개 4행을 기록한다.

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `50100` 이자비용 | DEBIT | `gross` |
| 2 | `20100` 예수금 | CREDIT | `gross` |
| 3 | `20100` 예수금 | DEBIT | `tax` |
| 4 | `20200` 원천세예수금 | CREDIT | `tax` |

첫 두 행은 은행의 세전 이자비용과 고객 예금채무 증가를 나타낸다.
나머지 두 행은 고객 예금채무 일부를 원천세 납부채무로 전환한다.

차변 합계와 대변 합계는 모두 `gross + tax`로 일치한다.

**예시 — 세전이자 10,000원**

| 항목 | 금액 |
|---|---:|
| 세전이자 (`gross`) | 10,000원 |
| 소득세 (14%, 10원 미만 절사) | 1,400원 |
| 지방소득세 (소득세의 10%, 10원 미만 절사) | 140원 |
| 원천세 합계 (`tax`) | 1,540원 |
| 상품 계좌 입금액 (`net`) | 8,460원 |

이 경우 예수금(`20100`) 순증가는 8,460원이고, 원천세예수금(`20200`) 증가는 1,540원이다.

**0원 및 소액 처리**

- `gross = 0`이면 원장과 GL을 기표하지 않지만 `interest_payment` 지급 이력은 1건 저장한다.
- `gross > 0`, `tax = 0`이면 위 표의 1~2행만 기표한다. 0원 세금 분개를 생성하지 않는다.
- `gross > 0`, `net = 0`은 현재 지급 서비스에서 오류로 거부한다.
- 지급액이 0원이어서 원장을 생성하지 않은 경우 이력의 `transaction_number`, `trade_date`는 NULL이다.

**트랜잭션 및 중복 방지**

- `InterestPaymentUseCase.pay()`는 호출자 트랜잭션 안에서만 실행한다(`MANDATORY`).
- 원장 입금, GL 전표, `interest_payment` 이력을 같은 트랜잭션에서 처리한다.
- 원장·GL·이력 저장 또는 후속 해지 처리가 실패하면 모두 롤백한다.
- `interest_payment.account_id`의 UNIQUE 제약으로 계좌당 이자 지급을 최대 1회로 제한한다.
- 중복 지급은 `PRD0302`로 거부한다. 요청별 멱등키만으로 중복 지급을 판단하지 않는다.
- PH-15·PH-16은 이 지급 계약을 재사용하고, 이후 원리금 이체 및 `CLOSED` 전이를 동일 트랜잭션에서 수행한다.

**PH-60b 시드 생성기는 이자 지급 전표를 생성하지 않는다.** 이 패턴은 실제 이자 지급 경로에서 사용하며, PH-60b의 기존 시드 패턴 범위는 변경하지 않는다.

### 3-7. 예·적금 원리금 이체 — `MATURITY` / `MAT` · `TERMINATION` / `TRM` (PH-15·PH-16, #713)

만기해지(PH-15)와 중도해지(PH-16)는 `transfer.api.LedgerDepositUseCase.transferBetween()` 으로 상품 계좌의
원리금을 고객 계좌로 옮깁니다. `ledger_entry` 에 `MATURITY` 또는 `TERMINATION` 2행이 남고 전표 1건이 대응합니다.

| line_no | 계정 | dr_cr | 금액 |
|---|---|---|---|
| 1 | `20100` 예수금 | DEBIT | 이체금액 (상품 계좌 측) |
| 2 | `20100` 예수금 | CREDIT | 이체금액 (고객 계좌 측) |

- **계정 구성은 3-2 와 같고 `tx_type` 만 다릅니다.** 3-3 상품가입과 같은 이유입니다. 원장 `transaction_type` 과
  전표 유형이 1:1 로 맞아야 전표번호만으로 고객 이체와 해지를 구분할 수 있습니다.
- 금액은 `transferBetween()` 에 넘긴 원리금(원금과 세후이자의 합)입니다. 세전이자와 원천세는 3-6 `INT` 전표가
  이미 나눴으므로 이 전표에서 다시 나누지 않습니다.
- `reference_key` 는 `transferBetween()` 이 돌려준 `LedgerDepositResult.transactionNumber`, `trade_date` 는 같은
  결과의 `tradeDate` 입니다. 이자 전표는 `credit()` 의 거래번호를 쓰므로, 해지 1건에 `INT` 와 `MAT`(또는 `TRM`)
  두 전표가 서로 다른 키로 붙습니다.
- 해지 서비스가 `gl.api.JournalPostingUseCase.post()` 를 직접 부릅니다. 입금 계약은 훅 B 를 부르지 않습니다
  ([transfer_seam.md](transfer_seam.md) §8 정책 2). `post()` 는 `MANDATORY` 라 이자 지급, 원리금 이체, `CLOSED`
  전이와 같은 트랜잭션에서 부릅니다.
- 이 전표가 빠지면 §5 (7) 이 그 원장 거래를 전표 없는 거래로 잡습니다.
- **범위 밖:** 중도해지 수수료 분개. 수수료를 떼면 `40200` 수수료수익 줄이 필요하므로 PH-16 이 수수료 처리를
  확정할 때 이 절에 더합니다. PH-60b 시드 생성기는 이 전표를 만들지 않습니다.

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
WHERE voucher_no NOT REGEXP '^[0-9]{8}-(OPN|TRF|SUB|INT|REV|IBT|IBS|MAT|TRM)-[0-9]{6}$';

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
    WHEN 'INTERBANK_TRANSFER'   THEN 'IBT'
    WHEN 'INTERBANK_SETTLEMENT' THEN 'IBS'
    WHEN 'MATURITY'             THEN 'MAT'
    WHEN 'TERMINATION'          THEN 'TRM'
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
| 2026-10-20 | PH-33-② (#654) — 타행이체 미결제 2패턴(§3-5, `IBT`·`IBS`), 검증 SQL (4)·(6) 유형 추가, 참조 키 1:1 규칙(§2-7)에 타행 예외 |
| 2026-10-22 | #713 — 예적금 원리금 이체 패턴(§3-7, `MAT`·`TRM`), 검증 SQL (4)·(6) 유형 추가 |
