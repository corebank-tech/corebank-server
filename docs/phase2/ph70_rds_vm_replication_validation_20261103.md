# PH-70-② — RDS → 온프레미스 VM MySQL GTID 복제 검증 (2026-11-03)

> **결과: 복제 연결·GTID 동기화·주요 테이블 행 수·거래 표본 10건·신규 GTID 반영 검증 통과.**
> 전체 DB의 행별 체크섬 검증이나 VPN 장애 원인 규명, 장애 전환 시험까지 완료했다는 의미는 아니다.
>
> 관련 이슈: #875 (본 검증 기록), #851 (11/1 VPN 장애와 레플리카 복구), #791 (GTID 모드), #793 (복제용 RDS 3306 접근), #817 (VPN 터널 이중화)
>
> 관련 문서: [RDS 컷오버 검증](rds_cutover_20261028.md), [정보계 ETL/레플리카 운영 절차](ph76_etl_ops_runbook.md), [VPN 운영 절차](ph100_vpn_vm_runbook.md)

## 1. 검증 대상과 범위

- 원본(Source): 2차 AWS RDS MySQL 8.4 `corebank-rds`.
- 복제본(Replica): 온프레미스 VM의 `info-replica-mysql` (MySQL, 포트 3306).
- 업무 DB: `minicore`.
- 연결: Site-to-Site VPN 경유 MySQL 3306, TLS 인증서 검증 활성화.
- 방식: GTID 자동 위치 지정(`Auto_Position=1`), binlog 기반 복제. RDS 관리용 `mysql.rds%` 테이블은 복제 필터로 제외.
- Replica 보호: `read_only=1`, `super_read_only=1` 확인.
- 범위: 복제 중단 뒤 재동기화 상태, 주요 업무 테이블 실제 행 수, `transfer` 거래 표본 필드, 신규 트랜잭션 적용을 **조회만으로** 확인.
- 비범위: 복제 재구축 절차 자체의 완전한 재현, 전체 행 값/체크섬, 정보계 API의 종단 간 조회, VPN 장애 자동 전환 및 알람 시험.

### 시각 해석

아래 시각은 **각 MySQL 세션의 `NOW()` 출력값**이다. RDS에서 `2026-11-03 12:30:38`, VM에서 `2026-11-03 03:30:36`이 관찰됐다. 두 세션의 출력 시각에는 9시간 차이가 있다. 세션 `@@session.time_zone`을 별도로 측정한 결과는 없으므로 실제 시각대 설정은 단정하지 않는다.

## 2. 누적 복제 지연과 해소

| 관측 시점 (VM 세션 출력) | IO / SQL | 수신 GTID | 적용 GTID | Seconds_Behind_Source | 해석 |
|---|---|---|---|---:|---|
| 2026-11-03 02:22:45 | Yes / Yes | `618-9939` | `1-8788` | 40,321 | 수신했으나 적용 대기 존재 |
| 2026-11-03 02:28:36 | 정상 상태 유지 | `618-9940` | `1-9940` | 0 | 수신분 적용 완료, `pending_gtid` 빈 값 |
| 이후 확인 | Yes / Yes | — | RDS/VM 모두 `1-9941` | 0 | 원본·복제본 GTID 일치 확인 |
| 최종 재확인 | Yes / Yes | `9960` 이상 | `9960` 이상 | 0 | IO/SQL errno 모두 0 |

GTID의 Source UUID: `555c9129-d281-11f1-a0b0-0affe82c66a7`.

- 최초 관측에서 `Replica_SQL_Running_State = Waiting for dependent transaction to commit` 상태가 보였지만, 오류 없이 적용 GTID가 증가하여 수신 지점까지 따라잡았다.
- `Replica_IO_State = Waiting for source to send event`는 소스의 후속 이벤트를 기다리는 상태로 관측됐다.
- `Seconds_Behind_Source`는 보조 지표다. **완료 판정에는 실제 GTID 적용 여부도 함께 사용**했다.
- 02:22:45와 02:28:36은 같은 VM 세션 시각으로 비교할 수 있지만, 그 사이의 원인 분석이나 지속적인 지연률 측정은 수행하지 않았다.

## 3. 실제 행 수(COUNT(*)) 비교

`information_schema.TABLES.TABLE_ROWS`를 처음 비교했을 때 일부 값이 달랐지만, 이는 InnoDB 통계 기반 **추정치**였다. 이에 따라 RDS와 VM에서 실제 `COUNT(*)`를 실행하여 아래 테이블의 **행 수 일치**를 확인했다.

| 유형 | 테이블 | RDS/VM 실제 COUNT(*) |
|---|---|---|
| 고객 | `customer` | 일치 |
| 계좌 | `account` | 일치 |
| 상품 가입 | `product_subscription` | 일치 |
| 마이그레이션 | `flyway_schema_history` | 일치 |
| 공통 코드 | `common_code` | 일치 |
| 금리 구간 | `product_rate_tier` | 일치 |
| 이체 | `transfer` | 일치 |
| 거래 원장 | `ledger_entry` | 일치 |
| GL 전표 | `gl_voucher` | 일치 |
| GL 분개 | `gl_journal_entry` | 일치 |

각 테이블의 **정확한 행 수 원문 수치**는 별도로 수집하지 않았다. 최초 `TABLE_ROWS` 추정치를 정확한 검증 값인 것처럼 재사용하지 않는다.

## 4. 거래 표본 10건 내용 비교

RDS와 VM에서 `transfer_id` 102944393~102944402를 조회했다. 두 환경에서 아래 필드가 **10/10건 모두 동일**했다.

- `transfer_id`, `transaction_number`
- `amount`, `fee`
- `transfer_type`, `status`
- `transferred_at` (마이크로초 포함)

| transfer_id | transaction_number | amount | fee | transfer_type | status | transferred_at |
|---:|---|---:|---:|---|---|---|
| 102944402 | 20261103BT0000000446 | 48000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:12.462646 |
| 102944401 | 20261103BT0000000445 | 23000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:12.323910 |
| 102944400 | 20261103BT0000000444 | 28000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:12.181813 |
| 102944399 | 20261103BT0000000443 | 33000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:12.047143 |
| 102944398 | 20261103BT0000000442 | 38000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.915684 |
| 102944397 | 20261103BT0000000441 | 43000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.785603 |
| 102944396 | 20261103BT0000000440 | 48000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.654050 |
| 102944395 | 20261103BT0000000439 | 23000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.512715 |
| 102944394 | 20261103BT0000000438 | 28000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.378319 |
| 102944393 | 20261103BT0000000437 | 33000 | 0 | SCHEDULED | SUCCESS | 2026-11-03 00:11:11.243044 |

모두 예약이체(`SCHEDULED`) 성공 거래이므로, 서로 다른 거래 유형·상태의 포괄적인 검증은 아니다.

## 5. 신규 GTID 지속 반영 검증

- 이전 RDS/VM 일치 확인 지점: `...:1-9941`.
- RDS 2026-11-03 12:30:38 조회: `...:1-9960`.
- VM 2026-11-03 03:30:36 조회: `...:1-9960`.
- VM `WAIT_FOR_EXECUTED_GTID_SET('555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960', 30)` → **0(적용 완료)**.
- GTID 끝 번호 9941 → 9960: **신규 19개 트랜잭션이 원본 및 복제본에 반영**된 것이 확인됐다.
- 마지막 `SHOW REPLICA STATUS\G`: IO/SQL **Yes/Yes**, `Seconds_Behind_Source=0`, `Last_IO_Errno=0`, `Last_SQL_Errno=0`, 수신·적용 GTID **9960 이상** 확인.

위 결과는 **이번 관측 구간의 정상 동작**을 보여준다. 항상 지연이 0초임을 보장하지는 않는다.

## 6. 재검증용 읽기 쿼리

> RDS 운영 부하에 유의한다. 대용량 테이블의 전체 `COUNT(*)`는 승인된 읽기/측정 시간대에만 수행하고, 실행 중인 복제를 중지·초기화하지 않는다.

### 6-1. RDS: 원본 GTID 확보

```sql
SELECT NOW() AS checked_at,
       @@SESSION.time_zone AS session_time_zone,
       @@GLOBAL.gtid_executed AS source_gtid;
```

### 6-2. VM: 스레드, GTID, 읽기 전용 확인

```sql
SHOW REPLICA STATUS\G

SELECT NOW() AS checked_at,
       @@SESSION.time_zone AS session_time_zone,
       @@GLOBAL.gtid_executed AS replica_gtid,
       @@GLOBAL.read_only AS read_only,
       @@GLOBAL.super_read_only AS super_read_only;
```

### 6-3. VM: 원본 GTID 적용 대기

아래 GTID는 **2026-11-03 측정 예시**다. 실행할 때에는 6-1에서 얻은 원본 GTID 전체를 넣는다.

```sql
SELECT WAIT_FOR_EXECUTED_GTID_SET(
    '555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960',
    30
) AS wait_result;
```

- `0`: 지정 GTID까지 적용
- `1`: 대기 제한 시간 초과
- 오류나 `NULL` 반환 시 상세 메시지 확인

### 6-4. RDS 및 VM: 같은 거래 식별자로 내용 확인

```sql
USE minicore;

SELECT transfer_id, transaction_number, amount, fee,
       transfer_type, status, transferred_at
FROM transfer
WHERE transfer_id BETWEEN 102944393 AND 102944402
ORDER BY transfer_id DESC;
```

이후 RDS/VM에서 동일 필드의 값과 행 개수를 비교한다. 정확한 비교는 식별자 범위를 고정하고 같은 GTID 기준점을 확인한 뒤 수행한다.

## 7. 완료 판정, 한계, 후속

### 이번에 통과한 검증

- [x] IO/SQL 정상, 오류 코드 0, 지연 0초
- [x] GTID 원본/복제본 일치, 미적용 GTID 해소
- [x] 주요 업무 테이블 10개 실제 행 수 일치
- [x] 예약이체 거래 표본 10건 주요 필드 일치
- [x] 신규 GTID 19개 적용 확인
- [x] Replica `read_only` 및 `super_read_only` 활성화 확인

### 한계 및 별도 후속

- 전체 테이블의 모든 행/컬럼을 체크섬으로 비교하지 않았다. **전체 데이터 무결성을 증명하는 결과로 확대 해석하지 않는다.**
- 행 수 비교는 데이터가 같은 시점에 조회됐는지에 영향을 받는다. 운영 트랜잭션이 지속되는 상황에서는 GTID 기준점을 함께 기록해야 한다.
- 11/1 VPN 단절 원인과 재발 방지: #851, #817, #861.
- 정보계 ETL 및 API와의 종단 간 연동: [정보계 운영 런북](ph76_etl_ops_runbook.md) 및 담당 이슈에서 검증한다.
- VPN과 복제가 재차 중단될 경우 먼저 **네트워크·binlog 보존 상태·복제 오류**를 확인하고, 임의로 `RESET REPLICA` / `RESET MASTER` / read-only 해제를 실행하지 않는다.
