# PH-36b 최종 측정 결과

> S4 [PH-36b](tasks.md#ph-36b-최종-측정--리포트) 측정 항목의 원자료와 판정을 모은다. 일정과 환경 배분은 [D-35](README.md#d-35--1030--p4) S4 일정표를 따랐다.
> 이 문서는 측정이 끝날 때마다 절을 더한다. 11/5에 기존 EC2 측정 2건(무응답 중 당행 TPS, 지연이체 정상 지급률)을 기록했다. 3-Tier 한 줄은 11/6에 더한다.

## 1. 결론

| 항목 | 결과 | 판정 |
|---|---|---|
| 무응답 중 당행 TPS | 상대 은행 무응답에도 당행 p95 132.5 → 138.8ms, 처리량 5.01 → 5.00건/s, 성공률 100% 유지 | 당행 영향 없음 |
| 타행 장애 뒤 자금 정합성 | 타행 232건 전부 SUCCESS로 확정, [§5 검사](transfer_correction.md#5-타행-장애-뒤-자금-정합성-ph-36) 4종 모두 0행 | 통과 |
| 지연이체 정상 지급률 | 50건 중 50건 지급, 전부 해제 시각부터 2분 안에 기표(최대 55.3초) | 100% |
| 3-Tier TPS · p99 한 줄 | 11/6 측정 예정 | - |

측정 중 결함 2건을 찾았다(5절). 두 측정의 판정에는 영향이 없다.

## 2. 공통 측정 조건

| 항목 | 값 |
|---|---|
| 대상 | 기존 EC2(vCPU 2) · 기존 RDS, 기존 ALB `corebank-alb-1199580552...`로 접속 |
| 이미지 | `corebank-server:f72982e0` (릴리스 3, PR #877 머지 커밋) |
| 서버 설정 | `DB_POOL_SIZE` 미설정(기본 10), `APP_OTP_CODE_HASH_ALGORITHM=hmac`, 지연이체 · FDS 스위치 꺼짐(지급률 준비 중 5분만 켬, 4절) |
| 부하 생성 | 사용자 맥북에서 k6. 대상과 다른 기기다([harness](harness.md) §7) |
| 시작 전 상태 | 11/5 16:10 load average 0.17(1분 평균, vCPU 2 기준 약 8%). 같은 EC2의 다른 측정(PH-30 E′1 · E′2, #531)은 끝난 뒤다 |
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

- 당행 p95 차이 +6.3ms는 회차 간 흔들림 범위이고 p99는 오히려 낮다. 처리량과 성공률이 같아 무응답 장애가 당행으로 번지지 않았다고 판정한다.
- 타행 TIMEOUT은 응답 대기 3초를 다 채우고 끝난다. 서킷 거절은 서킷이 열려 채번 전에 끊긴 `TRF0308`(거래번호 없음)이다. 스크립트가 이 두 가지를 나눠 센다(`perf/k6/lib/interbank.js`).

### 3-3. 서버 로그

| 구간 | 줄 수 | ERROR | HikariPool | WARN |
|---|---|---|---|---|
| NONE 16:11 ~ 16:16 | 1,262 | 0 | 0 | 0 |
| NO_RESPONSE 16:20:30 ~ 16:25 | 1,051 | 0 | 0 | 53 |

- WARN 53건은 모두 `InterbankSocketClient` `타행 응답 수신 실패 ... SocketTimeoutException: Read timed out`이다. 요청 스레드 52건은 k6 TIMEOUT 52건과 같고, 1건은 조회거래 스케줄러(`scheduling`)다.
- 타행 요청이 3초씩 잡혀 있어도 커넥션 풀 10이 바닥나지 않았다.
- TIMEOUT 52건은 10건 안팎씩 몰려 났다(16:23:19 ~ 28, 16:24:02 ~ 10). 서킷이 반쯤 열린 상태에서 시험 요청을 보낸 구간으로 보이지만, 서킷 상태 전환 로그가 없어 확인하지 못했다.

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

## 4. 지연이체 정상 지급률

### 4-1. 방법

| 시각(11/5) | 한 일 |
|---|---|
| 16:40 | 기존 EC2 compose에 `APP_DELAYED_TRANSFER_ENABLED=true`를 넣고 서버만 재생성(16:41 healthy). 모의 대외기관 컨테이너는 그대로 둠 |
| 16:42 | 고객 50명에게 지연이체를 켜고(기준금액 1만 원, 지연 3시간) 1만 원 이상을 당행 이체해 보류. 37건 보류, 13건은 5절 결함으로 실패 |
| 16:44 | 실패분을 채우려고 다른 고객 13명을 동시 1개로 다시 걸어 13건 보류. 보류 합계 50건(`transfer_hold_id` 1 ~ 50) |
| 16:45 | 스위치를 빼고 서버만 재생성(16:46 healthy). 이미 보류된 건은 스위치가 꺼져 있어도 실행된다 |
| 19:42:19 ~ 19:44:51 | 해제 시각. `TransferHoldReleaseScheduler`(1분 주기)가 실행 |
| 20:02 | 아래 SQL로 판정 |

- 고객 대역은 출금 `ph60_user_08001` ~ `08063`, 수취 `09001` ~ `09063`이다. 금액은 19,876원 + 순번이다. 기준금액 · 지연시간 · 금액은 시나리오 값이다.
- 보류를 만든 k6 스크립트는 저장소에 넣지 않았다. 로그인 → `PUT /transfers/delayed-transfer-setting` → 계좌비밀번호 · OTP 토큰 → `POST /transfers` 순서이고, 응답의 `transferHold`로 보류를 확인했다.

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
| 실행된 이체 | 50건 모두 `SUCCESS`, `source_type=HELD`, 원장 100행(건당 출금 · 입금 1행씩) |

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
```

## 5. 측정 중 발견

| # | 내용 | 영향 | 처리 |
|---|---|---|---|
| 1 | 지연이체 설정을 처음 저장하는 서로 다른 고객이 동시에 몰리면 `delayed_transfer_setting` INSERT 데드락으로 500. 동시 5개 50명 중 11건(22%), 동시 1개 13명은 0건 | 돈은 움직이지 않는다. 다시 보내면 성공 | #921 (P4) |
| 2 | OTP 발급의 `verification_request` INSERT 데드락으로 500, 같은 실행에서 2건 | 이체 전 단계라 돈은 움직이지 않는다 | 이슈 예정 (P1) |
| 3 | §5 SQL 원문이 `created_at` 범위로만 거르는데 그 컬럼으로 시작하는 인덱스가 없어, 운영 규모 `transfer` 전체를 훑어 오래 걸린다 | 판정에는 영향 없음 | 이번에는 `transaction_number >= '20261105WB' AND transaction_number < '20261105WC'`를 더해 `uk_transfer_txno` range로 돌렸다(실행계획 추정 9,150행). §5 수정은 따로 한다 |

1번과 2번은 보류를 만들기 전 단계에서 났고 실패분은 다른 고객으로 다시 걸어 채웠으므로 지급률 판정에 영향이 없다. S4 결함 수정 규칙([D-35](README.md#d-35--1030--p4))에 따라 dev에만 머지하고, 이 문서의 수치는 수정 전 릴리스 3 기준이다.

## 6. 한계

- 부하는 당행 5/s · 타행 1/s 각 3분, 지급률은 50건으로 시나리오 규모다. 같은 분에 수백 건이 해제되는 경우(스케줄러 한 번에 100건)는 재지 않았다.
- 무응답 측정의 서킷 상태 전환은 로그가 없어 시각을 확인하지 못했다.
- 같은 EC2 · RDS에서 같은 날 측정한 PH-30 수치와 부하 생성 기기가 다를 수 있다. 비교는 이 문서 안(NONE 대 NO_RESPONSE)에서만 한다.
- 11/5 거래일에 이 측정 거래가 약 2,100건(당행 약 1,800, 타행 232, 보류 실행 50) 들어 있다. 같은 날 밤 COB 측정을 볼 때 참고한다.
