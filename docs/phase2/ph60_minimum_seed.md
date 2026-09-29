# PH-60 최소 시드 실행·검증 가이드

## 1. 목적과 실행 방식

PH-60은 현행 `customer`, `account`, `transfer`, `ledger_entry`, `auto_transfer` 구조로 기능·성능 검증용 데이터를 만든다. 계정계·채널계 모델 분리는 후속 작업에서 다룬다.

대량 데이터 생성은 HTTP API로 노출하지 않는다. 실수로 운영 중 재실행하거나 외부에서 호출할 수 없도록 `phase2-seed` 프로필의 시작 작업으로만 실행한다. 따라서 Swagger UI에 추가되는 엔드포인트는 없다.

```bash
./gradlew bootRun --args="--spring.profiles.active=local,phase2-seed"
```

애플리케이션 로그의 `PH-60 minimum seed ready`가 출력되면 적재와 자체 정합성 검증이 끝난 것이다.

## 2. 고정 데이터 규격

| 대상 | 건수·규칙 |
|---|---:|
| 고객 | 10,000명 |
| 계좌 | 30,000개(고객당 요구불·정기예금·적금 각 1개) |
| 성공 이체 | 235,000건 |
| 원장 | 500,000행(개시 30,000행 + 이체 출금·입금 470,000행) |
| 자동이체 | 5,000건 |
| 만기 임박 | 정기예금 100개, 기준일 이후 1~30일 분포 |
| `MATURED` | 0개(PH-60b 범위) |
| 기준 시각 | `2026-09-01 00:00:00` KST |

생성 공식과 기준 시각이 코드에 고정되어 있어 같은 버전에서는 고객·계좌·이체·원장·자동이체 값과 분포가 동일하다.

## 3. PH-60 전용 대역

| 식별자 | 시작 | 종료 |
|---|---:|---:|
| `customer_id` | 6,000,001 | 6,010,000 |
| `account_id` | 60,000,001 | 60,030,000 |
| `transfer_id` | 60,000,001 | 60,235,000 |
| `ledger_entry_id` | 60,000,001 | 60,500,000 |
| `auto_transfer_id` | 60,000,001 | 60,005,000 |
| 거래번호 시퀀스 | 6,000,000,001 | 6,000,265,000 |
| 계좌번호 은행코드 | `860` | `860` |

QA 시드의 고객·계좌·거래번호 대역과 겹치지 않는다. 각 행은 고정 PK와 거래번호를 사용하고 `INSERT IGNORE`로 생성하므로 다시 실행해도 행을 추가하거나 기존 잔액·거래를 초기화하지 않는다. 적재 후 정확한 건수와 정합성을 다시 검사하며, 일부 행만 존재하는 불완전한 상태는 오류로 종료한다.

## 4. 원장과 GL 규칙

- 모든 계좌에 개시 입금 원장 1행을 만든다.
- 성공 이체마다 동일한 `transfer_id`로 출금 1행과 입금 1행을 만든다.
- 계좌의 최종 `balance`는 입금 원장 합계에서 출금 원장 합계를 뺀 값과 같다.
- 개시 잔액은 `20260901-OPN-600001` 전표 1건으로 기록한다.
- GL 분개는 현금 `10100` 차변과 예수금 `20100` 대변 각 1행이며 금액이 같다.

## 5. P4 검수 SQL

아래 두 쿼리는 모두 `0`을 반환해야 한다.

```sql
SELECT COUNT(*)
FROM (
    SELECT t.transfer_id
    FROM transfer t
    JOIN ledger_entry le ON le.transfer_id = t.transfer_id
    WHERE t.transfer_id BETWEEN 60000001 AND 60235000
      AND t.status = 'SUCCESS'
    GROUP BY t.transfer_id
    HAVING COUNT(*) <> 2
       OR SUM(le.direction = 'WITHDRAWAL') <> 1
       OR SUM(le.direction = 'DEPOSIT') <> 1
) broken_pairs;
```

```sql
SELECT COUNT(*)
FROM (
    SELECT a.account_id
    FROM account a
    JOIN ledger_entry le ON le.account_id = a.account_id
    WHERE a.account_id BETWEEN 60000001 AND 60030000
    GROUP BY a.account_id, a.balance
    HAVING a.balance <>
        SUM(CASE WHEN le.direction = 'DEPOSIT' THEN le.amount ELSE -le.amount END)
) balance_mismatches;
```

개시 잔액 전표는 다음 쿼리가 `0`이어야 한다.

```sql
SELECT ABS(
    SUM(CASE WHEN dr_cr = 'DEBIT' THEN amount ELSE 0 END)
  - SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END)
) AS debit_credit_difference
FROM gl_journal_entry
WHERE voucher_no = '20260901-OPN-600001';
```

## 6. 측정 결과

2026-09-29 로컬 MySQL 8.4.10, JDBC 배치 1,000건 기준 최초 적재는 **92,997ms(약 1분 33초)**였다.

| 검증 | 결과 |
|---|---:|
| 고객 | 10,000 |
| 계좌 | 30,000 |
| 성공 이체 | 235,000 |
| 원장 | 500,000 |
| 자동이체 | 5,000 |
| 깨진 이체 원장 쌍 | 0 |
| 잔액 불일치 계좌 | 0 |

환경별 저장장치·DB 사양에 따라 적재 시간은 달라질 수 있다.
