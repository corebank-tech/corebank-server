# PH-70-② — RDS → 온프레미스 VM MySQL GTID 복제 검증 (2026-11-03)

> **결과: 복제 연결·GTID 동기화·주요 테이블 행 수 비교·거래 표본 10건·신규 GTID 반영 검증 완료.**
>
> RDS와 VM에서 주요 10개 테이블의 실제 `COUNT(*)`를 각각 조회하여 모두 일치함을 확인했다. 다만 비교 당시 각 테이블의 원문 수치와 공통 GTID 기준점은 별도로 보관하지 않아, 동일 스냅샷 기준의 전수 정합성 검증으로 해석하지 않는다.
>
> 전체 DB의 행별 체크섬 검증 및 VPN 장애 전환 시험은 수행하지 않았다. 11/1 VPN 장애 원인은 #851에서 분석했으며, 재발 방지는 별도 작업으로 관리한다.
>
> 관련 이슈: #875 (본 검증 기록), #851 (VPN 장애 복구), #791 (GTID 모드), #793 (복제용 RDS 3306 접근), #817 (VPN 터널 이중화), #861 (장애 알람)
>
> 관련 문서: [RDS 컷오버 검증](rds_cutover_20261028.md), [정보계 ETL/레플리카 운영 절차](ph76_etl_ops_runbook.md), [VPN 운영 절차](ph100_vpn_vm_runbook.md)

## 1. 검증 대상과 범위

- 원본(Source): 2차 AWS RDS MySQL 8.4 `corebank-rds`.
- 복제본(Replica): 온프레미스 VM의 `info-replica-mysql` (MySQL, 포트 3306).
- 업무 DB: `minicore`.
- 연결: Site-to-Site VPN 경유 MySQL 3306.
- 방식: GTID 자동 위치 지정(`Auto_Position=1`), binlog 기반 복제.
- 복제 필터: RDS 관리용 `mysql.rds%` 테이블 제외.
- Replica 보호: `read_only=1`, `super_read_only=1` 확인.
- 병렬 복제: `replica_parallel_workers=4`, `replica_preserve_commit_order=ON`.
- 검증 범위: 복제 지연 해소, GTID 동기화, 주요 업무 테이블 행 수, 거래 표본 데이터, 신규 트랜잭션 반영, TLS 설정, 병렬 복제 Worker 상태.
- 범위 밖: 전체 행·컬럼 체크섬 검증, 정보계 API 종단 간 조회, VPN 장애 자동 전환 및 알람 시험.

### 1-1. TLS 연결 검증

2026-11-03 VM에서 `SHOW REPLICA STATUS\G`를 실행하여 복제 연결의 TLS 설정을 확인했다.

| 항목 | VM 관측값 |
|---|---|
| `Source_SSL_Allowed` | `Yes` |
| `Source_SSL_CA_File` | `/var/lib/mysql/rds-ca.pem` |
| `Source_SSL_Cipher` | 빈 값 |
| `Source_TLS_Version` | 빈 값 |
| `Source_SSL_Verify_Server_Cert` | `Yes` |
| `Replica_IO_Running` | `Yes` |

`Source_SSL_Allowed=Yes`로 TLS 사용 설정을 확인했고, `Source_SSL_Verify_Server_Cert=Yes`와 CA 파일 경로를 통해 서버 인증서 검증 설정이 활성화되어 있음을 확인했다.

`Source_SSL_Cipher`와 `Source_TLS_Version`은 허용할 암호군·TLS 버전을 제한하는 설정값이다. 빈 값이 TLS 미사용을 의미하지는 않는다.

다만 이번 조회에서는 실제 연결에 협상된 TLS 버전과 암호군을 별도로 측정하지 않았다. 따라서 **TLS 및 서버 인증서 검증 설정 확인**과 **실제 협상된 암호군 검증**을 구분한다.

### 1-2. 시각 기준

RDS와 VM의 MySQL 세션 시각 기준이 다르므로, 검증 기록에는 KST를 기준으로 사용하고 필요한 경우 UTC를 병기한다.

- RDS MySQL: `Asia/Seoul` (KST, UTC+9).
- VM MySQL 컨테이너: UTC.
- VM 추가 측정의 `@@SESSION.time_zone`: `SYSTEM`.
- VM 컨테이너의 시스템 시간대는 기존 정보계 운영 런북 기준 UTC다.

실제 관측 시각:

| 대상 | MySQL 출력 시각 | KST 환산 |
|---|---|---|
| VM | 2026-11-03 02:22:45 | 2026-11-03 11:22:45 |
| VM | 2026-11-03 02:28:36 | 2026-11-03 11:28:36 |
| VM | 2026-11-03 03:30:36 | 2026-11-03 12:30:36 |
| RDS | 2026-11-03 12:30:38 | 2026-11-03 12:30:38 |
| VM | 2026-11-03 06:04:39 | 2026-11-03 15:04:39 |

RDS 12:30:38과 VM 03:30:36(UTC)은 KST로 환산하면 약 2초 차이다.

## 2. VPN 장애 복구 및 누적 복제 지연 해소

### 2-1. VPN 장애 원인과 복구

11/1 VPN 터널 장애의 원인 및 복구 과정은 #851에 기록되어 있다.

확인된 원인:

1. AWS VPN 장비 교체 과정에서 AWS가 IKE DELETE 메시지를 전송했다.
2. VM strongSwan은 해당 세션을 삭제했으나, 정상적인 DELETE 수신 이후 재연결하는 `closeaction` 설정이 없어 연결을 다시 시작하지 않았다.
3. 장애 기간에 교육장의 공인 IP가 변경되면서 AWS Customer Gateway에 등록된 IP와 실제 공인 IP가 불일치했다.
4. 이로 인해 `ipsec restart`만으로 VPN을 복구할 수 없었다.

수행한 복구:

- 변경된 공인 IP를 사용하는 Customer Gateway 생성.
- AWS VPN 연결을 신규 Customer Gateway로 변경.
- 2026-11-03 10:50:55 KST 터널 1 `UP` 확인.
- VM strongSwan의 `ESTABLISHED`, `INSTALLED` 상태 확인.
- VPN 경유 RDS TCP 3306 연결 확인.

VPN 장애 원인 분석 및 연결 복구는 #851에서 완료 처리됐다.

VPN 터널 2 구성과 자동 재연결 보완은 별도 후속 작업이다.

### 2-2. 복제 지연 해소 결과

VPN 복구 이후 VM의 복제 상태를 확인했다.

| 관측 시각(KST) | IO/SQL | 수신 GTID | 적용 GTID | 지연(초) | 결과 |
|---|---|---|---|---:|---|
| 11/3 11:22:45 | Yes/Yes | `618-9939` | `1-8788` | 40,321 | 누적 적용 지연 |
| 11/3 11:28:36 | 정상 상태 유지 | `618-9940` | `1-9940` | 0 | 수신분 적용 완료 |
| 11/3 11:28 이후, 정확한 시각 미보관 | Yes/Yes | — | RDS/VM `1-9941` | 0 | GTID 일치 확인 |
| 11/3 12:30:36~38 | Yes/Yes | `9960`까지 수신 확인 | RDS/VM `1-9960` | 0 | 신규 GTID 적용 확인 |

GTID의 Source UUID:

`555c9129-d281-11f1-a0b0-0affe82c66a7`

11:22:45에는 IO/SQL 스레드 모두 실행 중이었지만, 수신된 GTID에 비해 적용 GTID가 뒤처져 있었다.

11:28:36에는 수신 GTID와 적용 GTID가 일치했고, `Seconds_Behind_Source=0` 및 미적용 GTID 없음이 확인됐다.

이후 RDS와 VM의 적용 GTID가 `1-9941`, `1-9960`에서 각각 일치했다.

`Seconds_Behind_Source`는 보조 지표이므로, 복제 완료 여부는 실제 GTID 적용 상태와 함께 판단했다.

## 3. 주요 테이블 실제 행 수 비교

### 3-1. RDS·VM 행 수 일치 검증

2026-11-03 RDS와 VM에서 주요 업무 테이블 10개에 대해 실제 `COUNT(*)`를 각각 실행했다.

최초 `information_schema.TABLES.TABLE_ROWS` 조회에서는 일부 행 수가 다르게 나타났으나, 이는 InnoDB 통계 기반 추정치였다.

이후 실제 `COUNT(*)` 조회 결과에서는 **10개 테이블 모두 RDS와 VM의 행 수가 일치했다.**

| 유형 | 테이블 | 기존 RDS·VM 비교 결과 |
|---|---|---|
| 고객 | `customer` | 일치 확인 |
| 계좌 | `account` | 일치 확인 |
| 상품 가입 | `product_subscription` | 일치 확인 |
| 마이그레이션 | `flyway_schema_history` | 일치 확인 |
| 공통 코드 | `common_code` | 일치 확인 |
| 금리 구간 | `product_rate_tier` | 일치 확인 |
| 이체 | `transfer` | 일치 확인 |
| 거래 원장 | `ledger_entry` | 일치 확인 |
| GL 전표 | `gl_voucher` | 일치 확인 |
| GL 분개 | `gl_journal_entry` | 일치 확인 |

**검증 범위와 한계**

- RDS와 VM에서 각각 실제 `COUNT(*)`를 실행했다.
- 10개 테이블 모두 행 수가 일치함을 확인했다.
- 비교 당시 각 테이블의 정확한 숫자와 조회 시각은 현재 검증 기록에 원문으로 남아 있지 않다.
- RDS와 VM에서 동일한 데이터 스냅샷을 확보한 뒤 비교한 것은 아니다.
- 따라서 이 검증은 **실제 행 수 일치 관측**이며, 공통 GTID 기준의 전수 정합성 증명은 아니다.

### 3-2. VM 추가 측정 결과

기존 RDS·VM 일치 검증 이후, VM에서 동일한 10개 테이블을 재측정하여 실제 행 수를 기록했다.

측정 환경: VM `info-replica-mysql`

| 테이블 | VM COUNT(*) |
|---|---:|
| `customer` | 60,004 |
| `account` | 180,011 |
| `product_subscription` | 100,000 |
| `flyway_schema_history` | 97 |
| `common_code` | 51 |
| `product_rate_tier` | 43 |
| `transfer` | 3,200,576 |
| `ledger_entry` | 6,627,317 |
| `gl_voucher` | 3,275,373 |
| `gl_journal_entry` | 6,550,746 |

추가 측정 후 조회한 정보:

| 항목 | 값 |
|---|---|
| 확인 시각(UTC) | 2026-11-03 06:04:39.037832 |
| 확인 시각(KST) | 2026-11-03 15:04:39.037832 |
| `@@SESSION.time_zone` | `SYSTEM` |
| `@@GLOBAL.gtid_executed` | `555c9129-d281-11f1-a0b0-0affe82c66a7:1-10041` |

위 시각과 GTID는 `COUNT(*)` 실행 후 별도 SQL로 조회한 값이다.

**주의:** 이 값은 VM 단독 추가 측정 결과다. 앞선 RDS·VM 행 수 일치 검증과는 측정 시점이 다르므로, 이 표의 숫자를 과거 RDS 측정값으로 간주하지 않는다.

또한 여러 테이블의 `COUNT(*)`를 실행하는 동안 GTID가 증가할 수 있으므로, 조회 후 확인한 `1-10041`을 모든 테이블이 동일한 스냅샷에서 측정됐다는 근거로 사용하지 않는다.

## 4. 거래 표본 10건 내용 비교

RDS와 VM에서 `transfer_id` 102944393~102944402를 조회했다.

다음 필드가 10건 모두 일치했다.

- `transfer_id`
- `transaction_number`
- `amount`
- `fee`
- `transfer_type`
- `status`
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

**검증 결과: 10/10건 일치, 비교 필드 불일치 0건.**

단, 모두 예약이체(`SCHEDULED`) 성공 거래이므로 다른 거래 유형과 실패 상태까지 포괄하는 검증은 아니다.

## 5. 신규 GTID 지속 반영 검증

### 5-1. RDS·VM 동기화 확인

- 이전 일치 확인 지점: RDS·VM `1-9941`.
- RDS 2026-11-03 12:30:38 KST: `1-9960`.
- VM 2026-11-03 03:30:36 UTC(12:30:36 KST): `1-9960`.
- VM `WAIT_FOR_EXECUTED_GTID_SET(...:1-9960, 30)` 결과: `0`.

실행한 SQL:

```sql
SELECT WAIT_FOR_EXECUTED_GTID_SET(
    '555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960',
    30
) AS wait_result;
```

실행 결과:

```text
+-------------+
| wait_result |
+-------------+
|           0 |
+-------------+
```

GTID 끝 번호가 9941에서 9960으로 증가했으며, **신규 GTID 19개가 원본과 Replica 양쪽에 반영된 것을 확인했다.**

최종 복제 상태 재확인에서도 다음을 확인했다.

| 항목 | 결과 |
|---|---|
| `Replica_IO_Running` | Yes |
| `Replica_SQL_Running` | Yes |
| `Seconds_Behind_Source` | 0 |
| `Last_IO_Errno` | 0 |
| `Last_SQL_Errno` | 0 |
| `Retrieved_Gtid_Set` | 9960 이상 |
| `Executed_Gtid_Set` | 9960 이상 |

### 5-2. VM 추가 GTID 관측

추가 행 수 측정 이후 VM에서 다음 GTID를 확인했다.

```text
555c9129-d281-11f1-a0b0-0affe82c66a7:1-10041
```

- 확인 시각: 2026-11-03 15:04:39 KST
- 측정 위치: VM
- 이전 확인 지점: `1-9960`
- 추가로 관측한 GTID: 81개

이는 VM에서 더 많은 GTID가 실행됐음을 보여준다.

다만 같은 시각의 RDS GTID는 별도로 확보하지 않았으므로, **15:04:39 기준 RDS와 VM이 완전히 동기화됐다고 단정하지 않는다.**

## 6. 병렬 복제 Worker 검증

VM의 MySQL은 Worker 4개를 사용하는 병렬 복제를 적용했다.

2026-11-03에 다음 SQL을 실행했다.

```sql
SELECT
    CHANNEL_NAME,
    WORKER_ID,
    SERVICE_STATE,
    LAST_ERROR_NUMBER,
    LAST_ERROR_MESSAGE,
    LAST_ERROR_TIMESTAMP,
    LAST_APPLIED_TRANSACTION
FROM performance_schema.replication_applier_status_by_worker
ORDER BY CHANNEL_NAME, WORKER_ID;
```

실제 관측 결과:

| Worker | SERVICE_STATE | LAST_ERROR_NUMBER | LAST_ERROR_MESSAGE | 마지막 적용 GTID 끝 번호 |
|---:|---|---:|---|---:|
| 1 | ON | 0 | 빈 값 | 10041 |
| 2 | ON | 0 | 빈 값 | 10030 |
| 3 | ON | 0 | 빈 값 | 9710 |
| 4 | ON | 0 | 빈 값 | 9711 |

모든 Worker의 `LAST_ERROR_TIMESTAMP`는 `0000-00-00 00:00:00.000000`이었다.

**검증 결과**

- Worker 4개 모두 `ON`.
- Worker별 최근 오류 번호 모두 `0`.
- Worker별 최근 오류 메시지 모두 빈 값.
- Worker별 오류 시각은 모두 기본값.
- 조회 시점에 Worker별 최근 오류가 기록되지 않았음을 확인했다.

Worker마다 `LAST_APPLIED_TRANSACTION`이 다른 것은 각 Worker가 마지막으로 처리한 트랜잭션을 별도로 기록하기 때문이다.

따라서 Worker별 마지막 GTID의 차이만으로 복제 지연이나 데이터 누락을 판단하지 않는다.

전체 적용 상태는 `@@GLOBAL.gtid_executed`, `Executed_Gtid_Set`, `WAIT_FOR_EXECUTED_GTID_SET()`로 확인한다.

또한 `SHOW REPLICA STATUS`의 `Last_SQL_Errno`만으로 병렬 복제의 모든 Worker 상태를 판정하지 않고, `performance_schema.replication_applier_status_by_worker`를 함께 확인한다.

## 7. 재검증용 읽기 쿼리

> RDS 운영 부하에 유의한다. 대용량 테이블의 전체 `COUNT(*)`는 승인된 읽기·측정 시간대에만 수행한다.
>
> 실행 중인 복제를 중지하거나 초기화하지 않는다.
>
> 아래 SQL은 조회·대기용이며 운영 DB의 데이터를 직접 변경하지 않는다.

### 7-1. RDS: 원본 GTID 및 시각 확인

```sql
SELECT
    NOW(6) AS checked_at,
    @@SESSION.time_zone AS session_time_zone,
    @@GLOBAL.gtid_executed AS source_gtid;
```

### 7-2. VM: 복제 상태, GTID, 읽기 전용 설정 확인

```sql
SHOW REPLICA STATUS\G

SELECT
    NOW(6) AS checked_at,
    @@SESSION.time_zone AS session_time_zone,
    @@GLOBAL.gtid_executed AS replica_gtid,
    @@GLOBAL.read_only AS read_only,
    @@GLOBAL.super_read_only AS super_read_only;
```

`SHOW REPLICA STATUS`에서 다음을 확인한다.

- `Replica_IO_Running`
- `Replica_SQL_Running`
- `Seconds_Behind_Source`
- `Last_IO_Errno`
- `Last_SQL_Errno`
- `Retrieved_Gtid_Set`
- `Executed_Gtid_Set`
- `Source_SSL_Allowed`
- `Source_SSL_CA_File`
- `Source_SSL_Cipher`
- `Source_TLS_Version`
- `Source_SSL_Verify_Server_Cert`

### 7-3. VM: 병렬 복제 Worker 상태 확인

```sql
SELECT
    CHANNEL_NAME,
    WORKER_ID,
    SERVICE_STATE,
    LAST_ERROR_NUMBER,
    LAST_ERROR_MESSAGE,
    LAST_ERROR_TIMESTAMP,
    LAST_APPLIED_TRANSACTION
FROM performance_schema.replication_applier_status_by_worker
ORDER BY CHANNEL_NAME, WORKER_ID;
```

확인 기준:

- 활성 Worker가 설정된 개수만큼 존재하는지 확인.
- `SERVICE_STATE=ON` 확인.
- `LAST_ERROR_NUMBER=0` 확인.
- `LAST_ERROR_MESSAGE`에 오류가 없는지 확인.
- Worker별 최근 오류와 전체 GTID 적용 상태를 함께 확인.

### 7-4. VM: 원본 GTID 적용 대기

아래 GTID는 2026-11-03 측정 예시다.

실행할 때에는 RDS에서 새로 조회한 원본 GTID 전체를 입력한다.

```sql
SELECT WAIT_FOR_EXECUTED_GTID_SET(
    '555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960',
    30
) AS wait_result;
```

반환값:

- `0`: 지정한 GTID까지 적용 완료.
- `1`: 제한 시간 내 적용되지 않음.
- 오류 또는 `NULL`: 상세 오류 확인 필요.

원본 GTID와 VM의 적용 GTID를 비교할 때는 다음 SQL도 사용할 수 있다.

```sql
SELECT
    GTID_SUBSET(
        '555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960',
        @@GLOBAL.gtid_executed
    ) AS caught_up,
    GTID_SUBTRACT(
        '555c9129-d281-11f1-a0b0-0affe82c66a7:1-9960',
        @@GLOBAL.gtid_executed
    ) AS pending_gtid;
```

- `caught_up=1`: 지정한 원본 GTID 집합이 VM에 포함됨.
- `pending_gtid` 빈 값: 지정한 원본 GTID 중 미적용 항목 없음.

### 7-5. RDS 및 VM: 거래 표본 데이터 비교

```sql
USE minicore;

SELECT
    transfer_id,
    transaction_number,
    amount,
    fee,
    transfer_type,
    status,
    transferred_at
FROM transfer
WHERE transfer_id BETWEEN 102944393 AND 102944402
ORDER BY transfer_id DESC;
```

RDS와 VM에서 같은 거래 식별자에 대해 조회하고 모든 비교 필드의 값을 대조한다.

동일한 데이터 기준점을 확인하려면 원본 GTID를 확보하고 Replica가 해당 GTID까지 적용했는지 먼저 확인한다.

### 7-6. RDS 및 VM: 주요 테이블 실제 행 수 확인

RDS와 VM에서 동일한 SQL을 각각 실행한다.

조회 전후의 시각 및 GTID도 함께 기록한다.

```sql
USE minicore;

SELECT
    NOW(6) AS checked_at,
    @@SESSION.time_zone AS session_time_zone,
    @@GLOBAL.gtid_executed AS gtid_before;
```

```sql
SELECT 'customer' AS table_name, COUNT(*) AS row_count FROM customer
UNION ALL SELECT 'account', COUNT(*) FROM account
UNION ALL SELECT 'product_subscription', COUNT(*) FROM product_subscription
UNION ALL SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history
UNION ALL SELECT 'common_code', COUNT(*) FROM common_code
UNION ALL SELECT 'product_rate_tier', COUNT(*) FROM product_rate_tier
UNION ALL SELECT 'transfer', COUNT(*) FROM transfer
UNION ALL SELECT 'ledger_entry', COUNT(*) FROM ledger_entry
UNION ALL SELECT 'gl_voucher', COUNT(*) FROM gl_voucher
UNION ALL SELECT 'gl_journal_entry', COUNT(*) FROM gl_journal_entry;
```

```sql
SELECT
    NOW(6) AS checked_at,
    @@SESSION.time_zone AS session_time_zone,
    @@GLOBAL.gtid_executed AS gtid_after;
```

대용량 테이블의 정확한 `COUNT(*)`는 비용이 큰 조회이므로 승인된 측정 시간에만 실행한다.

조회 전후 GTID가 변경됐다면 측정 중 원본 데이터가 변경됐을 가능성을 고려해야 한다.

RDS·VM의 조회 시각과 GTID가 다르면 단순한 행 수 차이를 복제 오류로 단정하지 않는다.

공통 스냅샷을 확보하지 않은 결과는 관측값으로 기록한다.

## 8. 검증 결과 및 한계

### 8-1. 검증 완료 항목

- [x] VPN 경유 RDS TCP 3306 연결 확인.
- [x] Replica IO/SQL 스레드 정상 동작 확인.
- [x] 복제 지연 40,321초에서 0초로 해소된 과정 확인.
- [x] RDS·VM GTID `1-9941`, `1-9960` 일치 확인.
- [x] 신규 GTID 19개가 VM에 적용된 사실 확인.
- [x] VM `WAIT_FOR_EXECUTED_GTID_SET()` 결과 `0` 확인.
- [x] RDS·VM에서 주요 테이블 10개 실제 `COUNT(*)` 비교 및 행 수 일치 관측.
- [x] VM 추가 측정으로 10개 테이블의 실제 행 수 기록.
- [x] 예약이체 거래 표본 10건의 주요 필드 모두 일치.
- [x] Replica `read_only` 및 `super_read_only` 활성화 확인.
- [x] TLS 사용 및 서버 인증서 검증 설정 확인.
- [x] 병렬 복제 Worker 4개 ON 및 최근 오류 번호 0 확인.
- [x] VM 추가 적용 GTID `1-10041` 관측.

### 8-2. 검증 한계

1. **전체 데이터 정합성**
   - 모든 테이블의 모든 행과 컬럼을 체크섬으로 비교하지 않았다.
   - 주요 10개 테이블의 행 수 비교와 거래 표본 10건의 내용 비교를 수행했다.

2. **행 수 비교 시점**
   - RDS·VM에서 실제 COUNT(*)를 실행하여 일치함을 확인했다.
   - 그러나 최초 비교 시점의 개별 행 수와 GTID 기준점은 보관하지 않았다.
   - 이후 확보한 VM 실제 행 수는 별도 시점의 관측값이다.
   - 따라서 공통 스냅샷 기반 전수 검증 결과로 확대 해석하지 않는다.

3. **최신 GTID 동기화**
   - RDS·VM의 `1-9960` 동기화는 확인했다.
   - 이후 VM에서 `1-10041`을 추가 관측했으나, 해당 시각의 RDS GTID는 별도로 확인하지 않았다.

4. **TLS**
   - TLS 및 서버 인증서 검증 설정은 확인했다.
   - 실제 협상된 TLS 버전·암호군은 별도로 측정하지 않았다.

5. **장애 복구**
   - 11/1 VPN 장애 원인 분석 및 연결 복구는 #851에서 완료했다.
   - VPN 이중화와 자동 전환 시험은 이번 검증 범위가 아니다.

### 8-3. 후속 작업

- #817: VPN 터널 2 연결 및 장애 전환 검증.
- #861: VPN·Replica 복제 장애 알람 구성 및 시험.
- #819: VPN 장애 기간의 정보계 ETL 자동 적재 확인.
- #823: AWS WAS → VM 정보계 API 연결 확인.
- #818: WAS 경유 정보계 조회 결과 및 p95·p99 측정.
- PH-54: RDS 페일오버 및 온프레미스 DR 전환 리허설.

복제 장애가 다시 발생하면 먼저 VPN 연결 상태, RDS binlog 보존 상태, Replica IO/SQL 오류 및 GTID 적용 상태를 확인한다.

원인과 복구 범위를 판단하기 전에 `RESET REPLICA`, GTID 초기화, Replica 승격 또는 읽기 전용 설정 해제를 임의로 실행하지 않는다.

## 9. 최종 결론

2026-11-03 검증을 통해 다음을 확인했다.

1. VPN 장애 복구 이후 RDS → VM GTID 기반 복제가 정상적으로 재개됐다.
2. 누적 복제 지연이 해소되고 원본·복제본 GTID가 일치했다.
3. 새로운 트랜잭션이 Replica에 지속적으로 적용됐다.
4. 주요 10개 테이블의 실제 행 수 일치가 관측됐다.
5. 거래 표본 10건의 주요 필드가 모두 일치했다.
6. TLS 인증서 검증 설정과 병렬 복제 Worker 상태가 정상임을 확인했다.

따라서 **이번 관측 범위에서 RDS → VM MySQL 복제의 정상 동작 및 표본 데이터 정합성을 확인했다.**

전체 DB의 스냅샷 단위 무결성, 장애 자동 전환, DR 승격 및 Failback은 별도 검증 대상으로 유지한다.
