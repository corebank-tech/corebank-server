# PH-36b 최종 측정 결과

> S4 [PH-36b](tasks.md#ph-36b-최종-측정--리포트) 측정 항목의 원자료와 판정을 모은다. 일정과 환경 배분은 [D-35](README.md#d-35--1030--p4) S4 일정표를 따랐다.
> 이 문서는 측정이 끝날 때마다 절을 더한다. 11/5에 기존 EC2 측정 2건(무응답 중 당행 TPS, 지연이체 정상 지급률)을 기록했다. 11/6에 무응답 중 당행 TPS 2회차, `execute()` 순환복잡도, 3-Tier 한 줄을 더했다.

## 1. 결론

| 항목 | 결과 | 판정 |
|---|---|---|
| 무응답 중 당행 TPS | 2회 모두 상대 은행 무응답 중에도 당행 성공률 100%, 처리량 5.0건/s 유지. NONE → NO_RESPONSE p95는 1회차(11/5) 132.5 → 138.8ms, 2회차(11/6) 197.3 → 165.7ms로 NO_RESPONSE가 느리지 않았다 | 당행 영향 없음 |
| 타행 장애 뒤 자금 정합성 | 1회차 타행 232건, 2회차 414건 모두 SUCCESS로 확정, [§5 검사](transfer_correction.md#5-타행-장애-뒤-자금-정합성-ph-36) 4종 두 회차 모두 0행 | 통과 |
| 지연이체 정상 지급률 | 50건 중 50건 지급, 전부 해제 시각부터 2분 안에 기표(최대 55.3초) | 100% |
| `execute()` 순환복잡도 | 이체 실행 본체 CCN 23 → 13, 218줄 → 86줄. CCN 15 초과 메서드 1 → 0 | 감소 |
| 3-Tier TPS · p99 한 줄 | 운영 3-Tier(WAS 2대)에서 10/s 1,800건 모두 성공, 10.00건/s, p95 448.2ms, p99 779.3ms. 같은 릴리스 3 · `hmac`의 단일 EC2(E′2)도 10/s를 견뎌 처리량 차이는 없고, p95는 135ms보다 길다 | 10/s 견딤 |

측정 중 결함 2건과 문서 SQL 문제 1건을 찾았다(7절). 위 판정에는 영향이 없다.

## 2. 공통 측정 조건

| 항목 | 값 |
|---|---|
| 대상 | 기존 EC2(vCPU 2) · 기존 RDS, 기존 ALB `corebank-alb-1199580552...`로 접속 |
| 이미지 | `corebank-server:f72982e0` (릴리스 3, PR #877 머지 커밋) |
| 서버 설정 | `DB_POOL_SIZE` 미설정(기본 10), `APP_OTP_CODE_HASH_ALGORITHM=hmac`, 지연이체 · FDS 스위치 꺼짐(지급률 준비 중 5분만 켬, 4절) |
| 11/6 서버 | 11/5 20:17에 서버 컨테이너만 다시 만들었다. #920 임시 조치로 `APP_LEDGERRECONCILIATION_FULLSCANCHUNKTIMEOUTSECONDS=1800`(COB 대사 청크 제한)만 더했고 이체 경로 설정은 같다. 그 뒤 11/6 측정까지 측정 부하는 없었다 |
| 부하 생성 | 사용자 맥북에서 k6. 대상과 다른 기기다([harness](harness.md) §7) |
| 시작 전 상태 | 11/5 16:10 load average 0.17(1분 평균, vCPU 2 기준 약 8%). 같은 EC2의 다른 측정(PH-30 E′1 · E′2, 11/4 · 11/5 오전)은 끝난 뒤다. 11/6 11:59 load average 0.66 · 0.29 · 0.14(1 · 5 · 15분), 그 전까지 부하 없이 쉬던 상태 |
| 데이터 | PH-60 · PH-60b 시드 고객. 이 측정이 쓴 고객 대역은 3절 · 4절에 적는다 |

## 3. 무응답 중 당행 TPS

### 3-1. 방법

`perf/k6/interbankFault.js`를 모의 대외기관 스위치 `NONE` · `NO_RESPONSE`로 한 번씩 돌렸다. 당행 이체 5/s와 타행 이체 1/s를 같은 3분 동안 고정 도착률로 흘린다. 스위치는 EC2 안에서 컨테이너로 바꿨다(스크립트 머리말 절차).

```bash
BASE="https://<기존 ALB DNS>/api/v1"
STATS="avg,min,med,p(90),p(95),p(99),max"
k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" -e RATE=5 -e FAULT_MODE=NONE \
  --summary-trend-stats="$STATS" --summary-export=fault_none.json perf/k6/interbankFault.js
# 스위치를 NO_RESPONSE로 바꾸고 확인한 뒤
k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" -e RATE=5 -e FAULT_MODE=NO_RESPONSE \
  --summary-trend-stats="$STATS" --summary-export=fault_no_response.json perf/k6/interbankFault.js
# 끝나면 스위치를 NONE으로 되돌린다
```

11/6 2회차는 같은 명령을 NONE → NO_RESPONSE → NONE 순서로 세 번 돌렸다. 첫 NONE이 1회차보다 크게 느려(3-2) 서버가 데워진 뒤 NONE을 한 번 더 쟀다. NO_RESPONSE와 바로 이어 잰 두 번째 NONE을 2회차 비교 기준으로 쓴다. 측정 중 EC2에서 `vmstat 5`를 같이 기록했다.

### 3-2. 결과

| 항목 | NONE | NO_RESPONSE |
|---|---|---|
| 시각(11/5) | 16:11:47 ~ 16:15 | 16:20:48 ~ 16:24:10 |
| 당행 `transfer_duration` avg | 86.7ms | 85.2ms |
| 당행 p95 | 132.5ms | 138.8ms |
| 당행 p99 | 272.9ms | 220.4ms |
| 당행 max | 702.5ms | 661.4ms |
| 당행 성공 | 901 / 901 | 900 / 900 |
| 당행 처리량(성공 ÷ 180초) | 5.01건/s | 5.00건/s |
| 타행 결과 | 성공 180 | TIMEOUT 52, 서킷 거절 129 |
| 타행 `interbank_duration` p95 | 147.0ms | 3.11s |
| `dropped_iterations` | 0 | 0 |
| HTTP 실패 | 0 / 4,464 | 0 / 4,464 |

11/6 2회차

| 항목 | NONE ① (예열 전) | NO_RESPONSE | NONE ② (예열 후) |
|---|---|---|---|
| 시각(11/6) | 12:02:40 ~ 12:06:04 | 12:08:21 ~ 12:11:42 | 12:12:39 ~ 12:15:57 |
| 당행 `transfer_duration` avg | 152.6ms | 89.8ms | 95.7ms |
| 당행 p95 | 605.9ms | 165.7ms | 197.3ms |
| 당행 p99 | 972.3ms | 312.5ms | 429.6ms |
| 당행 max | 1.50s | 854.5ms | 808.5ms |
| 당행 성공 | 900 / 900 | 901 / 901 | 901 / 901 |
| 당행 처리량(성공 ÷ 180초) | 5.00건/s | 5.01건/s | 5.01건/s |
| 타행 결과 | 성공 180 | TIMEOUT 53, 서킷 거절 128 | 성공 181 |
| 타행 `interbank_duration` p95 | 808.9ms | 3.08s | 225.9ms |
| HTTP 실패 | 0 / 4,460 | 0 / 4,468 | 0 / 4,468 |

- 두 회차 모두 NO_RESPONSE의 당행 p95가 바로 옆 NONE보다 크게 높지 않고(1회차 +6.3ms, 2회차 −31.6ms), 처리량과 성공률이 같다. 무응답 장애가 당행으로 번지지 않았다고 판정한다.
- 예열 전 NONE ①을 뺀 4회의 당행 p95는 132.5 ~ 197.3ms다. 1회차의 +6.3ms와 2회차의 −31.6ms는 모두 이 범위 안이다.
- NONE ①은 장애가 없는데도 같은 조건의 다른 세 회보다 p95가 3배 넘게 느렸다. 직전까지 부하가 없던 서버에 처음 건 부하라 덜 데워진 상태로 본다. 4분 뒤 같은 조건의 NONE ②는 197.3ms였다. 비교에서는 뺀다.
- 타행 TIMEOUT은 응답 대기 3초를 다 채우고 끝난다. 서킷 거절은 서킷이 열려 채번 전에 끊긴 `TRF0308`(거래번호 없음)이다. 스크립트가 이 두 가지를 나눠 센다(`perf/k6/lib/interbank.js`).

### 3-3. 서버 로그

| 구간 | 줄 수 | ERROR | HikariPool | WARN |
|---|---|---|---|---|
| NONE 16:11 ~ 16:16 | 1,262 | 0 | 0 | 0 |
| NO_RESPONSE 16:20:30 ~ 16:25 | 1,051 | 0 | 0 | 53 |
| (11/6) NONE ① 12:02:30 ~ 12:06:10 | 1,260 | 0 | 0 | 0 |
| (11/6) NO_RESPONSE 12:08:10 ~ 12:12:00 | 1,053 | 0 | 0 | 53 |
| (11/6) NONE ② 12:12:30 ~ 12:16:10 | 1,374 | 0 | 0 | 0 |

- WARN 53건은 모두 `InterbankSocketClient` `타행 응답 수신 실패 ... SocketTimeoutException: Read timed out`이다. 요청 스레드 52건은 k6 TIMEOUT 52건과 같고, 1건은 조회거래 스케줄러(`scheduling`)다.
- 11/6 WARN 53건도 모두 `Read timed out`이고 k6 TIMEOUT 53건과 같다.
- 타행 요청이 3초씩 잡혀 있어도 커넥션 풀 10이 바닥나지 않았다. 2회차도 같다.
- 11/6 세 구간 내내 `swpd`가 352,780KB로 고정이고 `so`는 0, `si`는 최대 71KB/s였다. 스왑 입출력은 NONE ①이 느린 원인이 아니다. 5/s에서 CPU 사용률(`us`)은 30 ~ 48%다.
- TIMEOUT 52건은 10건 안팎씩 몰려 났다(16:23:19 ~ 28, 16:24:02 ~ 10). 서킷이 반쯤 열린 상태에서 시험 요청을 보낸 구간으로 보이지만, 서킷 상태 전환 로그가 없어 확인하지 못했다.

서버 로그는 JSON(`"level":"WARN"`)이라 공백을 둔 `' WARN '` · `' ERROR '` 검색은 맞지 않는다. 11/6 세 구간은 11/9에 아래 명령으로 다시 셌고 위 표와 같았다. 11/5 구간은 11/5 20:17 컨테이너 재생성으로 `docker logs`와 파일 로그(`corebank.2026-11-05.0.log`가 20:17:06부터 시작) 모두 남아 있지 않아 다시 세지 못했다. 1회차 값은 측정 당일 집계 그대로다.

```bash
# 기존 EC2에서
cnt() { docker logs --since "$1" --until "$2" corebank-server 2>&1 | awk -v a="$1" '
  {n++} /"level":"ERROR"/{e++} /"level":"WARN"/{w++} /HikariPool/{h++} /Read timed out/{r++}
  END{printf "%s lines=%d ERROR=%d WARN=%d Hikari=%d ReadTimedOut=%d\n",a,n,e,w,h,r}'; }
cnt 2026-11-06T12:02:30+09:00 2026-11-06T12:06:10+09:00
cnt 2026-11-06T12:08:10+09:00 2026-11-06T12:12:00+09:00
cnt 2026-11-06T12:12:30+09:00 2026-11-06T12:16:10+09:00
```

### 3-4. 정합성

스위치를 16:24:31에 되돌리고 조회거래가 남은 TIMEOUT을 확정한 뒤 [transfer_correction.md §5](transfer_correction.md#5-타행-장애-뒤-자금-정합성-ph-36) SQL을 돌렸다. 구간은 `created_at` 16:11 ~ 16:26이다.

| 검사 | 결과 |
|---|---|
| 상태별 건수 | SUCCESS 232건, 6,966,713원. TIMEOUT · ERROR 0건 |
| (1) 잔액 = 마지막 원장 | 0행 |
| (2) 고객에게서 빠진 돈 | 0행 |
| (3) `10200` 순잔액 | 0행 |
| (4) 출금 · 되돌림 행 수 | 0행 |

- 232건 = NONE 성공 180 + NO_RESPONSE TIMEOUT 52. 모의 대외기관이 송금은 처리하고 응답만 보내지 않았으므로 조회거래가 모두 SUCCESS로 확정한 것이 맞다.
- 서킷 거절 129건은 채번 전에 끊겨 `transfer` 행이 없다.

11/6 2회차는 NO_RESPONSE 뒤 12:12에 스위치를 NONE으로 되돌리고 이어 NONE ②를 잰 다음, 20:50에 기존 RDS에서 아래 SQL을 돌렸다. 구간은 `created_at` 12:02 ~ 12:17이다.

| 검사 | 결과 |
|---|---|
| 상태별 건수 | SUCCESS 414건, 12,431,119원. TIMEOUT · ERROR 0건 |
| (1) 잔액 = 마지막 원장 | 0행 |
| (2) 고객에게서 빠진 돈 | 0행 |
| (3) `10200` 순잔액 | 0행 |
| (4) 출금 · 되돌림 행 수 | 0행 |

- 414건 = NONE 성공 180 + 181, NO_RESPONSE TIMEOUT 53. 1회차와 같이 조회거래가 TIMEOUT을 모두 SUCCESS로 확정했다.
- 서킷 거절 128건은 채번 전에 끊겨 `transfer` 행이 없다.

실행한 SQL. [transfer_correction.md §5](transfer_correction.md#5-타행-장애-뒤-자금-정합성-ph-36) 원문에 `transaction_number` 범위만 더했다(7절 3번). 1회차는 `created_at` 16:11 ~ 16:26과 거래번호 범위 `20261105WB` ~ `20261105WC`라는 같은 조건으로 돌렸다.

```sql
SET @from_at = '2026-11-06 12:02:00';
SET @to_at   = '2026-11-06 12:17:00';
SET @tx_from = '20261106WB';
SET @tx_to   = '20261106WC';

-- 상태별 건수(판정 전에 TIMEOUT이 0인지 본다)
SELECT t.status, COUNT(*) AS transfers, SUM(t.amount) AS amount
FROM transfer t
WHERE t.transaction_number >= @tx_from AND t.transaction_number < @tx_to
  AND t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.status;

-- (1) 계좌 잔액 = 마지막 원장 balance_after
SELECT a.account_id, a.balance,
       (SELECT le.balance_after FROM ledger_entry le
        WHERE le.account_id = a.account_id
        ORDER BY le.occurred_at DESC, le.ledger_entry_id DESC LIMIT 1) AS last_balance_after
FROM account a
WHERE a.account_id IN (
    SELECT withdrawal_account_id FROM transfer
    WHERE transaction_number >= @tx_from AND transaction_number < @tx_to
      AND payee_bank_code IS NOT NULL AND created_at >= @from_at AND created_at < @to_at)
HAVING NOT (a.balance <=> last_balance_after);

-- (2) 고객에게서 빠진 돈 = 성공·처리 불명이면 송금액, 실패면 0
SELECT t.transaction_number, t.status, t.amount,
       COALESCE(SUM(CASE WHEN le.direction = 'WITHDRAWAL' THEN le.amount ELSE -le.amount END), 0) AS net_out
FROM transfer t
LEFT JOIN ledger_entry le ON le.transaction_number = t.transaction_number
WHERE t.transaction_number >= @tx_from AND t.transaction_number < @tx_to
  AND t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
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
WHERE t.transaction_number >= @tx_from AND t.transaction_number < @tx_to
  AND t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.transaction_number, t.status, t.amount
HAVING unsettled_net <> CASE WHEN t.status = 'TIMEOUT' THEN -t.amount ELSE 0 END;

-- (4) 출금은 최대 1행, 되돌림은 송신 뒤 실패에만 정확히 1행
SELECT t.transaction_number, t.status,
       COALESCE(SUM(le.direction = 'WITHDRAWAL'), 0) AS withdrawals,
       COALESCE(SUM(le.direction = 'DEPOSIT'), 0)    AS refunds
FROM transfer t
LEFT JOIN ledger_entry le ON le.transaction_number = t.transaction_number
WHERE t.transaction_number >= @tx_from AND t.transaction_number < @tx_to
  AND t.payee_bank_code IS NOT NULL AND t.created_at >= @from_at AND t.created_at < @to_at
GROUP BY t.transaction_number, t.status
HAVING withdrawals > 1
    OR refunds <> CASE WHEN t.status = 'ERROR' AND withdrawals = 1 THEN 1 ELSE 0 END;
```

## 4. 지연이체 정상 지급률

### 4-1. 방법

| 시각(11/5) | 한 일 |
|---|---|
| 16:40 | 기존 EC2 compose에 `APP_DELAYED_TRANSFER_ENABLED=true`를 넣고 서버만 재생성(16:41 healthy). 모의 대외기관 컨테이너는 그대로 둠 |
| 16:42 | 고객 50명에게 지연이체를 켜고(기준금액 1만 원, 지연 3시간) 1만 원 이상을 당행 이체해 보류. 37건 보류, 13건은 7절 결함으로 실패 |
| 16:44 | 실패분을 채우려고 다른 고객 13명을 동시 1개로 다시 걸어 13건 보류. 보류 합계 50건(`transfer_hold_id` 1 ~ 50) |
| 16:45 | 스위치를 빼고 서버만 재생성(16:46 healthy). 이미 보류된 건은 스위치가 꺼져 있어도 실행된다 |
| 19:42:19 ~ 19:44:51 | 해제 시각. `TransferHoldReleaseScheduler`(1분 주기)가 실행 |
| 20:02 | 아래 SQL로 판정 |

- 고객 대역은 출금 `ph60_user_08001` ~ `08063`, 수취 `09001` ~ `09063`이다. 금액은 19,876원 + 순번이다. 기준금액 · 지연시간 · 금액은 시나리오 값이다.
- 보류는 `perf/k6/delayedHold.js`로 만들었다. 로그인 → `PUT /transfers/delayed-transfer-setting` → 계좌비밀번호 · OTP 토큰 → `POST /transfers` 순서이고, 응답의 `transferHold`로 보류를 확인한다. 측정 때는 `_workspace`에 두고 돌린 같은 스크립트를 import 경로만 고쳐 넣었다.

```bash
BASE="https://<기존 ALB DNS>/api/v1"
# 16:42 동시 5개 50명
k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" perf/k6/delayedHold.js
# 16:44 실패분을 다른 고객 13명으로 동시 1개
k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" -e FROM_SEQ=8051 -e COUNT=13 -e VUS=1 perf/k6/delayedHold.js
```

- 다시 돌리면 `transfer_hold_id`가 1 ~ 50이 아니다. 4-2 SQL의 `transfer_hold_id` 범위와 해제 시각을 HELD 로그의 `holdId` · `releaseAt`으로 바꾼다.

### 4-2. 결과

지급률 정의는 [tasks.md PH-06](tasks.md#ph-06-지연이체-서비스)이다. 해제 시각부터 2분이 지난 보류 중 고객 취소를 뺀 것이 분모, `EXECUTED`가 분자다.

| 항목 | 값 |
|---|---|
| 대상 | 50 |
| 지급(`EXECUTED`) | 50 |
| 제때 지급(해제 + 2분 안 기표) | 50 |
| 실패(`FAILED`) | 0 |
| 미지급(`HELD` · `EXECUTING`) | 0 |
| **지급률** | **100.00%** |
| 해제 → 기표 | 최소 11.4초, 평균 24.1초, 최대 55.3초 |
| 실행된 이체 | 50건 모두 `SUCCESS`, `source_type=HELD`, 원장 100행(건당 출금 · 입금 1행씩). 아래 (5) · (6) SQL로 11/6에 다시 확인했다 |

- 16:46 재기동은 해제 시각보다 3시간 앞이라 실행에 영향이 없었다.
- DB 시간대는 세션 · 전역 모두 `Asia/Seoul`이다.

판정 SQL(4-2 표의 지급률 · 실패 · 해제 → 기표 · 이체 행)

```sql
SELECT COUNT(*) AS due,
       SUM(h.status = 'EXECUTED') AS paid,
       SUM(h.status = 'EXECUTED' AND t.transferred_at <= h.release_at + INTERVAL 2 MINUTE) AS paid_on_time,
       SUM(h.status = 'FAILED') AS failed,
       SUM(h.status IN ('HELD', 'EXECUTING')) AS not_yet_paid,
       ROUND(100 * SUM(h.status = 'EXECUTED') / COUNT(*), 2) AS paid_rate_pct
  FROM transfer_hold h
  LEFT JOIN transfer t ON t.transfer_id = h.transfer_id
 WHERE h.release_mode = 'AT_TIME' AND h.status <> 'CANCELLED'
   AND h.transfer_hold_id BETWEEN 1 AND 50
   AND h.release_at <= '2026-11-05 19:44:51';

SELECT MIN(TIMESTAMPDIFF(MICROSECOND, h.release_at, t.transferred_at)) / 1e6 AS min_s,
       AVG(TIMESTAMPDIFF(MICROSECOND, h.release_at, t.transferred_at)) / 1e6 AS avg_s,
       MAX(TIMESTAMPDIFF(MICROSECOND, h.release_at, t.transferred_at)) / 1e6 AS max_s
  FROM transfer_hold h JOIN transfer t ON t.transfer_id = h.transfer_id
 WHERE h.transfer_hold_id BETWEEN 1 AND 50 AND h.status = 'EXECUTED';

-- (5) 실행된 이체의 상태 · source_type. SUCCESS · HELD · 50 한 줄이 나왔다
SELECT t.status, t.source_type, COUNT(*) AS transfers
  FROM transfer_hold h JOIN transfer t ON t.transfer_id = h.transfer_id
 WHERE h.transfer_hold_id BETWEEN 1 AND 50
 GROUP BY t.status, t.source_type;

-- (6) 건당 원장 출금 1 · 입금 1(합계 100행)이 아닌 이체. 0행이 나왔다
SELECT t.transaction_number,
       COUNT(le.ledger_entry_id)                     AS rows_,
       COALESCE(SUM(le.direction = 'WITHDRAWAL'), 0) AS w,
       COALESCE(SUM(le.direction = 'DEPOSIT'), 0)    AS d
  FROM transfer_hold h
  JOIN transfer t ON t.transfer_id = h.transfer_id
  LEFT JOIN ledger_entry le ON le.transaction_number = t.transaction_number
 WHERE h.transfer_hold_id BETWEEN 1 AND 50
 GROUP BY t.transaction_number
HAVING rows_ <> 2 OR w <> 1 OR d <> 1;
```

## 5. `execute()` 순환복잡도

### 5-1. 방법

[README 측정 지표](README.md)의 "`execute()` 순환복잡도 · #381 이후 · 재측정 · 정적 분석" 항목이다. 개선 전은 #381 리팩터링 PR #426 머지 직전 커밋 `50185247^1`, 개선 후는 릴리스 3 커밋 `f72982e0`(2절 이미지와 같은 커밋)의 `TransferExecutionService.java`다. `origin/main`이 아니라 커밋으로 고정해 다음 릴리스 뒤에도 같은 파일을 잰다. 두 파일을 같은 도구로 한 번에 쟀다.

```bash
python3 -m venv /tmp/lz && /tmp/lz/bin/pip install -q lizard   # lizard 1.25.0
D=$(mktemp -d)
F=$(git ls-tree -r --name-only f72982e0 | grep '/TransferExecutionService.java$')
git show 50185247^1:"$F" > $D/before.java
git show f72982e0:"$F"   > $D/after.java
/tmp/lz/bin/lizard $D/before.java $D/after.java
```

### 5-2. 결과

| 항목 | 개선 전(`50185247^1`) | 개선 후(릴리스 3) |
|---|---|---|
| 이체 실행 본체 | `execute()` CCN 23, NLOC 147, 218줄 | `doExecute()` CCN 13, NLOC 60, 86줄 |
| `execute()` | 위와 같음 | CCN 2, 16줄(`doExecute()` 호출과 지표 기록) |
| 파일 내 최대 CCN | 23 | 13 |
| 파일 평균 CCN(메서드 수) | 6.5(6개) | 2.8(16개) |
| CCN 15 초과 경고 | 1건 | 0건 |

- 비교는 메서드 이름이 아니라 이체 실행 본체끼리 한다. 지금 `execute()`는 지표 기록을 감싸는 껍데기라 이름만 맞춰 23 → 2로 적으면 과장이다.
- 릴리스 3에서 지연이체 보류 분기(`isHeldExecution` · `findHeldOrigin`)가 더해진 뒤에도 15를 넘는 메서드가 없다.
- 개선 후 출력에서 메서드 앞에 `Posting::`이 붙은 것은 lizard가 파일 안 중첩 타입 이름을 잘못 읽은 것이다. 모두 `TransferExecutionService`의 메서드다.

## 6. 3-Tier TPS · p99 한 줄

### 6-1. 방법

PH-30과 같은 `perf/k6/load.js`를 운영 3-Tier에 5/s · 10/s 고정 도착률로 각 3분, 1회씩 돌렸다. 운영 서버라 COB(23:20) 전에 끝냈다.

| 항목 | 값 |
|---|---|
| 대상 | 운영 3-Tier, `https://api.corebank.cloud/api/v1` → ALB → WAS `t3.small` 2대 → RDS `db.t3.small` Multi-AZ. 사양은 [ph57_capacity_plan.md](ph57_capacity_plan.md) 기준이고 측정 때 직접 확인하지 않았다 |
| 이미지 | 릴리스 3 (11/3 P5 수동 반영, [D-35](README.md#d-35--1030--p4) 일정 기준). 측정 때 WAS의 이미지 태그는 확인하지 않았다 |
| 부하 생성 | 사용자 맥북에서 k6. `MAX_VUS = SESSION_POOL = 200` |
| 시각(11/6) | smoke 22:59:20, 5/s 22:59:49 ~ 23:03:27, 10/s 23:06:47 ~ 23:10:17 |
| 데이터 | 새 환경 PH-60 시드 고객(smoke 로그인 `customerId=6000001`) |

```bash
BASE="https://api.corebank.cloud/api/v1"
STATS="avg,min,med,p(90),p(95),p(99),max"
k6 run -e BASE_URL="$BASE" perf/k6/smoke.js
k6 run -e BASE_URL="$BASE" -e RATE=5 -e DURATION=3m -e MAX_VUS=200 -e SESSION_POOL=200 \
  --summary-trend-stats="$STATS" --summary-export=tier3_5.json perf/k6/load.js
k6 run -e BASE_URL="$BASE" -e RATE=10 -e DURATION=3m -e MAX_VUS=200 -e SESSION_POOL=200 \
  --summary-trend-stats="$STATS" --summary-export=tier3_10.json perf/k6/load.js
```

### 6-2. 결과

| 항목 | 5/s | 10/s |
|---|---|---|
| 이체 `transfer_duration` avg | 223.5ms | 269.8ms |
| 이체 p95 | 338.4ms | 448.2ms |
| 이체 p99 | 549.7ms | 779.3ms |
| 이체 max | 855.8ms | 1.85s |
| 이체 성공 | 901 / 901 | 1,800 / 1,800 |
| 성공 처리량(성공 ÷ 180초) | 5.01건/s | 10.00건/s |
| HTTP 실패 | 0 / 3,804 | 0 / 7,400 |

한 줄: **운영 3-Tier(WAS 2대)는 10/s에서 이체 1,800건을 모두 처리했다(10.00건/s, p95 448.2ms, p99 779.3ms).** 같은 릴리스 3 · `hmac`의 단일 EC2(E′2)도 10/s를 견뎠으므로 10/s에서는 환경 전환으로 처리량이 늘었는지 알 수 없다. 단일 EC2 C 기준선(릴리스 2 · `bcrypt`, 3.27건/s)과의 차이는 대부분 OTP 해시 교체 효과다.

- 같은 10/s의 단일 EC2 E′2(같은 릴리스 3 · `hmac`, p95 135ms)보다 p95가 약 3배 길다. 3-Tier는 RDS Multi-AZ 동기 복제와 ALB → 사설 서브넷 WAS 구간이 더해지고, 부하 생성 기기도 다르다(E′2는 리눅스 4코어). 어느 쪽이 얼마인지는 나누지 않았다.
- 10/s까지만 쟀으므로 3-Tier의 포화 도착률은 모른다. 단일 EC2 E′2는 15/s 근처가 한계였다(P4 리포트 2-2). WAS 2대의 확장 효율은 P5 PH-56 범위다.
- 1회씩만 쟀다. 이 측정 거래 2,702건(smoke 1, 5/s 901, 10/s 1,800)은 11/6 운영 거래일에 들어 있다.

## 7. 측정 중 발견

| # | 내용 | 영향 | 처리 |
|---|---|---|---|
| 1 | 지연이체 설정을 처음 저장하는 서로 다른 고객이 동시에 몰리면 `delayed_transfer_setting` INSERT 데드락으로 500. 동시 5개 50명 중 11건(22%), 동시 1개 13명은 0건 | 돈은 움직이지 않는다. 다시 보내면 성공 | #921 (P4) |
| 2 | OTP 발급의 `verification_request` INSERT 데드락으로 500, 같은 실행에서 2건 | 이체 전 단계라 돈은 움직이지 않는다 | 이슈 예정 (P1) |
| 3 | §5 SQL 원문이 `created_at` 범위로만 거르는데 그 컬럼으로 시작하는 인덱스가 없어, 운영 규모 `transfer` 전체를 훑어 오래 걸린다 | 판정에는 영향 없음 | 이번에는 `transaction_number >= '20261105WB' AND transaction_number < '20261105WC'`를 더해 `uk_transfer_txno` range로 돌렸다(실행계획 추정 9,150행). §5 수정은 따로 한다 |

1번과 2번은 보류를 만들기 전 단계에서 났고 실패분은 다른 고객으로 다시 걸어 채웠으므로 지급률 판정에 영향이 없다. S4 결함 수정 규칙([D-35](README.md#d-35--1030--p4))에 따라 dev에만 머지하고, 이 문서의 수치는 수정 전 릴리스 3 기준이다.

## 8. 한계

- 부하는 당행 5/s · 타행 1/s 각 3분, 지급률은 50건으로 시나리오 규모다. 같은 분에 수백 건이 해제되는 경우(스케줄러 한 번에 100건)는 재지 않았다.
- 무응답 측정의 서킷 상태 전환은 로그가 없어 시각을 확인하지 못했다.
- 무응답 측정은 날짜별로 1쌍씩 2회다. 11/6 예열 전 회차처럼 직전 부하 이력에 따라 절대값이 크게 흔들린다. 그래서 판정은 바로 이어 잰 NONE과 NO_RESPONSE 쌍 안에서만 한다.
- 11/6 측정 중 `vmstat`의 `st`(다른 VM에 CPU를 뺏긴 비율)가 몇 차례 15 ~ 30%로 튀었다. p99 흔들림의 원인일 수 있으나 확인하지 않았다.
- PH-30 E′1 · E′2(11/4)는 리눅스 4코어에서, 이 문서의 측정은 맥북에서 부하를 냈다. 무응답 판정은 이 문서 안(NONE 대 NO_RESPONSE)에서만 한다.
- 11/5 거래일에 이 측정 거래가 약 2,100건(당행 약 1,800, 타행 232, 보류 실행 50) 들어 있다. 같은 날 밤 COB 측정을 볼 때 참고한다.
- 11/6 거래일에도 약 3,100건(당행 2,702, 타행 414)이 들어 있다.
