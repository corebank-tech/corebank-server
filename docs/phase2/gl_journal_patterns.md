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
| `TTT` | 전표유형 3자 — `OPN` / `TRF` / `SUB` / `INT` |
| `NNNNNN` | **(거래일, 유형)당 1부터** 증가하는 6자리 0채움 일련번호 |

전체 19자입니다(`voucher_no VARCHAR(20)`).

**일련번호가 6자리인 이유.** PH-60b 가 원장 300만 건을 3개월에 넣고 원장 1건당 전표 1건이므로, 달력
기준 하루 약 3.3만 · 영업일 기준 약 4.6만 건입니다. 4자리(9,999)로는 첫날부터 넘치고, 5자리(99,999)는
핫스팟 구간(P5 PH-56 이 의도적으로 만드는 집중 구간)에서 여유가 2배뿐입니다.

**유형별로 따로 셉니다.** 같은 날 `TRF` 와 `SUB` 는 각자 1부터 시작합니다. 런타임 기표(PH-24)에서
유형별로 채번하면 단일 카운터 경합이 줄고, 시드 생성기도 유형별로 독립 할당할 수 있습니다.

> 영업일 provider 는 P5 PH-40(10/2)입니다. 그 전까지 서버 구현은 `LocalDate` 로 임시 채번합니다.
> 시드는 생성기가 만드는 거래일을 그대로 쓰면 됩니다.

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

### 3-2. 당행 이체 — `TRANSFER` / `TRF`

원장 2행(출금·입금)에 전표 1건이 대응합니다.

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
(`ProductSubscriptionDepositService` 가 `withdrawalAccountId`·`depositAccountId` 로 원장 2행을
남깁니다 — `LedgerPair.forProductSubscription`, "2행 복식기표 원장 쌍"). 외부에서 현금이 들어오는
거래가 아니므로 차변은 현금성이 아니라 예수금입니다.

> 요구불예수금과 저축성예수금을 계정으로 나누면 두 패턴이 갈리지만, 2차 계정과목 15개에는
> 예수금이 하나뿐이라 나누지 않았습니다. 상품별 계정 매핑(`product_gl_mapping`)은 PH-24 범위입니다.

### 3-4. 아직 확정되지 않은 패턴

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
WHERE voucher_no NOT REGEXP '^[0-9]{8}-(OPN|TRF|SUB|INT)-[0-9]{6}$';

-- (5) 거래일이 전표번호의 날짜와 다른 전표 — 0행이어야 한다
SELECT voucher_no, trade_date FROM gl_voucher
WHERE DATE_FORMAT(trade_date, '%Y%m%d') <> SUBSTRING(voucher_no, 1, 8);
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
