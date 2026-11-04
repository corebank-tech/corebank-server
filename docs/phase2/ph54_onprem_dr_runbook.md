# PH-54 온프레미스 DR 전환 및 Failback 런북

> 관련 이슈: #886
>
> 담당: P2
>
> 작성 기준: 2026-11-03
>
> 상태: 절차 초안 및 사전 환경 점검 완료 / DR 리허설 미실시
>
> **주의: 이 문서는 DR 전환 절차를 정의한다. 실제 Replica 승격, 운영 DNS 변경, RDS 쓰기 차단 및 AWS 인프라 변경은 별도 승인 전까지 실행하지 않는다.**

## 1. 목적 및 범위

### 1-1. 목적

AWS 계정계 서비스에 장애가 발생했을 때 온프레미스 VM으로 서비스를 전환하고, 복구 소요 시간(RTO)과 데이터 유실 가능성(RPO)을 검증한다.

현재 AWS RDS의 데이터를 온프레미스 MySQL Replica로 비동기 복제하고 있다.

DR 발생 시 Replica를 새로운 Primary DB로 승격하고, VM에서 계정계 API를 실행한 뒤 DNS 및 접속 경로를 전환하여 서비스를 복구한다.

본 런북에서는 다음 사항을 정의한다.

1. 장애 유형별 DR 전환 판단 기준
2. 전환 전 원본 RDS 및 Replica 상태 확인
3. 원본 쓰기 차단 및 데이터 정합성 검증
4. MySQL Replica 승격
5. VM 계정계 API 기동
6. DNS 및 HTTPS 접속 경로 전환
7. 주요 금융 업무 정상 동작 검증
8. RTO/RPO 측정
9. AWS Failback 절차
10. 격리 리허설 및 결과 기록

### 1-2. 작업 범위

| 구분 | 담당 | 관련 작업 |
|---|---|---|
| 온프레미스 DR 시나리오·리허설 총괄 | P2 | #886 |
| RDS Multi-AZ 페일오버·스냅샷 복구 | P2 | #885 |
| RDS → VM MySQL 복제 정합성 검증 | P2 | #875 |
| VM 계정계 API·FE·HTTPS 실행 환경 준비 | P5 | #849 |
| VPN 및 VM 운영 환경 | P5, P2 협조 | PH-100 |
| 주요 거래·원장 정합성 검증 | P2, P3·P4 협조 | PH-54 |

**본 문서는 온프레미스 DR 전환에 집중한다.**

RDS 자체의 Multi-AZ 강제 페일오버와 스냅샷 복원 훈련은 #885에서 별도로 다룬다.

Discord 장애 알림, 담당자 승인 UI, 자동 승격 시스템 개발은 현재 #886의 범위에 포함하지 않는다.

---

## 2. 현재 시스템 구성

### 2-1. 정상 운영 구조

```text
                     고객 / FE
                         |
                         v
                 api.corebank.cloud
                         |
                         v
                    AWS ALB
                         |
                 +-------+-------+
                 |               |
                 v               v
               WAS 1           WAS 2
                 |               |
                 +-------+-------+
                         |
                         v
                  AWS RDS MySQL
                  (Primary DB)
                         |
                         | MySQL Binlog / GTID
                         | Site-to-Site VPN
                         v
                  온프레미스 VM
                info-replica-mysql
                  (Read Replica)
                         |
                         | 정보계 ETL
                         v
                  info-mart-mysql
                         |
                         v
                      info-api
```

정상 운영 시 금융 거래의 쓰기 요청은 AWS RDS에서 처리한다.

온프레미스 Replica는 RDS의 변경사항을 복제하며, 정보계 ETL의 데이터 소스로 사용한다.

Replica는 평상시 `read_only=ON`, `super_read_only=ON` 상태를 유지한다.

### 2-2. DR 전환 후 목표 구조

```text
                     고객 / FE
                         |
                         v
                 DR 접속 경로
                         |
                         v
                  온프레미스 VM
                         |
                         v
                   계정계 API
                         |
                         v
                  MySQL Primary
                (기존 Replica 승격)
```

이 구조에서는 기존 AWS RDS가 아닌 VM MySQL이 새로운 거래를 처리한다.

단, VM 내부 접속만 정상이라고 해서 DR이 완료된 것은 아니다. 실제 사용자에게 서비스를 제공할 HTTPS·DNS·FE 경로까지 정상이어야 한다.

### 2-3. 2026-11-03 기준 VM 상태

다음 값은 실제 VM에서 관측한 결과다.

| 항목 | 측정 결과 |
|---|---|
| 운영체제 | Ubuntu |
| 전체 메모리 | 약 14 GiB |
| 사용 가능 메모리 | 약 9.5 GiB |
| Swap | 0 |
| 루트 파일시스템 | 53 GB |
| 사용량 | 20 GB |
| 남은 공간 | 31 GB |
| Replica 데이터 디렉터리 | 9.7 GB |
| 정보계 마트 데이터 디렉터리 | 4.0 GB |
| Replica 컨테이너 | `info-replica-mysql` |
| 마트 컨테이너 | `info-mart-mysql` |
| 정보계 API 컨테이너 | `info-api` |

현재 컨테이너 구성:

| 컨테이너 | 호스트 바인딩 | 용도 |
|---|---|---|
| `info-replica-mysql` | `127.0.0.1:3306` | RDS 복제본 |
| `info-mart-mysql` | `127.0.0.1:3307` | 정보계 마트 |
| `info-api` | 별도 설정 확인 필요 | 정보계 조회 API |

**주의:** Replica의 호스트 포트가 `127.0.0.1`에 바인딩되어 있다고 해서 다른 Docker 컨테이너가 자동으로 접근할 수 있는 것은 아니다.

새 계정계 API 컨테이너의 DB 연결 방식은 P5의 #849에서 검증된 설정을 사용한다.

### 2-4. 현재 복제 상태

2026-11-03 사전 점검 결과:

```text
Replica_IO_Running: Yes
Replica_SQL_Running: Yes
Seconds_Behind_Source: 0
Last_IO_Errno: 0
Last_SQL_Errno: 0

Retrieved_Gtid_Set:
555c9129-d281-11f1-a0b0-0affe82c66a7:618-10071

Executed_Gtid_Set:
555c9129-d281-11f1-a0b0-0affe82c66a7:1-10071
```

추가 확인:

```text
read_only = 1
super_read_only = 1
```

판정:

- IO/SQL 복제 스레드 정상
- 조회 당시 복제 지연 0초
- 복제 오류 코드 0
- 수신한 최신 GTID까지 적용한 상태
- Replica의 쓰기 방지 설정 유지

다만 이 결과는 특정 시점의 관측값이다.

**RDS 원본과 완전히 동기화됐는지 최종 판단하려면 같은 전환 기준점의 원본 GTID를 추가로 비교해야 한다.**

### 2-5. 릴리스 3 DB 변경 반영

VM Replica의 `flyway_schema_history` 조회 결과:

| Version | Description | Success |
|---|---|---|
| `202611030100` | add payee status index to scheduled transfer | 1 |
| `202611030059` | add deposit status index to auto transfer | 1 |
| `202611021100` | create delayed transfer setting | 1 |

릴리스 3의 최신 DB 마이그레이션 이력이 Replica에 반영된 것을 확인했다.

현재 9.7GB의 Replica 데이터 디렉터리 용량은 위 마이그레이션 적용 이후에 측정한 값이다.

단, 이 결과만으로 모든 업무 데이터의 전수 정합성까지 검증됐다고 판단하지 않는다.

---

## 3. DR 전환 판단 기준

### 3-1. 장애 유형별 대응

| 장애 상황 | 우선 대응 | 온프레미스 DR |
|---|---|---|
| WAS 1대 장애 | ALB 및 정상 WAS로 서비스 유지 | 불필요 |
| WAS 전체 장애 | AWS WAS 복구 가능 여부 확인 | 복구 불가 시 검토 |
| RDS 인스턴스 장애 | RDS Multi-AZ 페일오버 | 복구 불가 시 검토 |
| 단일 AZ 장애 | AWS 내 가용 영역 전환 | 복구 불가 시 검토 |
| VPN만 단절 | VPN 복구 및 복제 상태 확인 | 승격 금지 |
| AWS 계정계 서비스 전체 장애 | 서비스 복구 가능성과 예상 복구 시간 평가 | 전환 검토 |
| AWS RDS 접근 불가 | 마지막 Replica 적용 상태 및 유실 위험 평가 | 별도 승인 후 검토 |

DR은 단순히 AWS API가 일시적으로 응답하지 않는다는 이유만으로 실행하지 않는다.

기존 AWS 환경에서 복구 가능한 장애인지 우선 판단한다.

### 3-2. DR 전환 승인 조건

실제 전환 전 다음 조건을 확인한다.

- [ ] AWS 계정계 서비스의 장애 범위가 확인됐다.
- [ ] AWS 내부 복구보다 온프레미스 전환이 필요한 이유를 기록했다.
- [ ] 기존 Primary에 신규 쓰기가 발생하지 않도록 차단할 수 있다.
- [ ] 진행 중인 트랜잭션 및 백그라운드 배치 처리를 확인했다.
- [ ] Replica의 마지막 적용 GTID를 기록했다.
- [ ] 예상 데이터 유실 가능성을 평가했다.
- [ ] VM 계정계 API 실행 환경이 준비되어 있다.
- [ ] HTTPS·FE·DNS 접속 경로가 준비되어 있다.
- [ ] RTO/RPO 측정 방법을 확정했다.
- [ ] P2 및 관련 담당자의 실행 승인을 받았다.

**가장 중요한 조건은 기존 Primary와 승격한 Replica가 동시에 쓰기를 처리하지 않도록 보장하는 것이다.**

### 3-3. 승격 금지 조건

다음 상황에서는 승격을 진행하지 않는다.

1. VPN만 단절됐고 AWS RDS가 정상 작동하는 경우
2. AWS의 쓰기 요청을 차단하지 못한 경우
3. 계획된 전환에서 원본 GTID의 미적용분이 남은 경우
4. Replica SQL 오류 또는 데이터 정합성 오류가 확인된 경우
5. VM 계정계 API가 정상적으로 실행될 준비가 되지 않은 경우
6. 운영 접속 경로·TLS 인증서·DNS 설정이 준비되지 않은 경우
7. 백업 및 실패 시 대응 방법이 없는 경우
8. 승인된 시험 시간대가 아닌 경우

실제 AWS 전체 장애로 원본 쓰기 차단이나 최신 GTID 확인이 불가능하다면, 안전한 전환을 보장할 수 없다는 점을 명시하고 별도의 장애 대응 결정을 받아야 한다.

**RDS에 연결되지 않는다는 사실만으로 RDS의 쓰기가 중단됐다고 판단하지 않는다.**

---

## 4. 승격 전 사전 점검

> 이 절의 명령은 조회 목적이다.
>
> 현재 복제를 중지하거나 컨테이너를 재시작하지 않는다.

### 4-1. VM 자원 확인

VM에서 실행한다.

```bash
TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S %Z'

free -h

df -h / /var/lib/info-replica /var/lib/info-mart

sudo du -sh /var/lib/info-replica /var/lib/info-mart

docker ps --format \
  'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

docker stats --no-stream
```

확인 항목:

- [ ] 남은 디스크 공간이 충분하다.
- [ ] 메모리 여유가 충분하다.
- [ ] Replica·마트·정보계 API가 정상 실행 중이다.
- [ ] MySQL 데이터 디렉터리에 접근할 수 있다.
- [ ] 불필요한 디스크 점유가 발생하지 않았다.

검증 전에는 가동 중인 MySQL 데이터 디렉터리를 단순 복사하여 일관된 백업으로 사용하지 않는다.

### 4-2. Replica 상태 확인

```bash
docker exec info-replica-mysql sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root_pw) mysql -uroot -e "SHOW REPLICA STATUS\\G"'
```

주요 확인 값:

```text
Replica_IO_Running
Replica_SQL_Running
Seconds_Behind_Source
Last_IO_Errno
Last_SQL_Errno
Last_IO_Error
Last_SQL_Error
Retrieved_Gtid_Set
Executed_Gtid_Set
```

정상 조건:

| 항목 | 기대값 |
|---|---|
| IO 스레드 | Yes |
| SQL 스레드 | Yes |
| IO 오류 번호 | 0 |
| SQL 오류 번호 | 0 |
| 복제 지연 | 0초 또는 허용 범위 이내 |
| GTID | 전환 기준점까지 적용 완료 |

`Seconds_Behind_Source=0`만으로 데이터 유실이 없다고 판정하지 않는다.

### 4-3. Replica GTID 및 쓰기 방지 상태

```bash
docker exec -i info-replica-mysql sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root_pw) exec mysql -uroot' <<'SQL'
SELECT
    NOW(6) AS checked_at,
    @@GLOBAL.gtid_executed AS executed_gtid,
    @@GLOBAL.gtid_purged AS purged_gtid,
    @@GLOBAL.gtid_mode AS gtid_mode,
    @@GLOBAL.server_id AS server_id,
    @@GLOBAL.server_uuid AS server_uuid,
    @@GLOBAL.read_only AS read_only,
    @@GLOBAL.super_read_only AS super_read_only;
SQL
```

승격 전 정상 상태:

```text
read_only = 1
super_read_only = 1
```

GTID 전체 값과 점검 시각을 기록한다.

### 4-4. 병렬 복제 Worker 확인

```bash
docker exec -i info-replica-mysql sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root_pw) exec mysql -uroot' <<'SQL'
SELECT
    CHANNEL_NAME,
    WORKER_ID,
    SERVICE_STATE,
    LAST_ERROR_NUMBER,
    LAST_ERROR_MESSAGE
FROM performance_schema.replication_applier_status_by_worker
ORDER BY CHANNEL_NAME, WORKER_ID;
SQL
```

확인 항목:

- [ ] Worker 오류가 없다.
- [ ] 복제 적용이 정상적으로 진행된다.
- [ ] 중단 또는 재시도 중인 작업이 없는지 확인했다.

Worker가 여러 개인 경우에도 최종 적용 GTID와 복제 상태를 함께 확인한다.

### 4-5. RDS 원본 GTID 확인

승인된 RDS 접속 환경에서 실행한다.

```sql
SELECT
    NOW(6) AS checked_at,
    @@GLOBAL.gtid_executed AS source_gtid,
    @@GLOBAL.server_uuid AS source_uuid,
    @@GLOBAL.read_only AS source_read_only;
```

출력된 `source_gtid`를 기록한다.

VM에서 해당 GTID가 모두 적용됐는지 확인한다.

```sql
SELECT GTID_SUBTRACT(
    '<RDS에서 확인한 전체 GTID 집합>',
    @@GLOBAL.gtid_executed
) AS missing_gtids;
```

`missing_gtids`가 빈 문자열이면 해당 원본 GTID 집합의 모든 트랜잭션이 Replica의 실행 이력에 포함되어 있다는 의미다.

단, 원본에서 쓰기가 계속 발생하는 동안에는 조회 직후에도 GTID가 증가할 수 있다.

따라서 계획된 전환에서는 **원본 쓰기를 중지한 후 최종 GTID를 확보**해야 한다.

또한 GTID 집합 비교는 데이터 전수 검증을 대체하지 않는다.

### 4-6. 백업 및 복구 가능성 확인

- [ ] RDS의 마지막 정상 백업 또는 스냅샷을 확인했다.
- [ ] Replica의 마지막 적용 GTID를 기록했다.
- [ ] MySQL 복제 설정과 데이터 보존 방법을 확인했다.
- [ ] 전환 실패 시 서비스를 어디로 복구할지 결정했다.
- [ ] 승격 후 VM에서 발생한 쓰기를 어떻게 보존할지 결정했다.

---

## 5. 격리 DR 리허설

### 5-1. 실행 원칙

2026-11-03~11-06 S4 기간에는 운영 Replica를 직접 승격하지 않는다.

실제 Replica는 RDS와의 복제를 유지해야 하므로, 격리 DB 또는 별도의 복구 환경에서 전환 절차를 먼저 검증한다.

격리 환경이 준비되지 않았다면 강제로 실행하지 않고 미실시 사유를 기록한다.

### 5-2. 격리 환경 요구사항

- 운영 Replica와 다른 MySQL 인스턴스를 사용한다.
- 별도의 데이터 디렉터리를 사용한다.
- 운영 Replica와 다른 포트를 사용한다.
- 서버 ID와 MySQL 인스턴스 식별자를 구분한다.
- 격리 DB가 실제 RDS를 복제하거나 변경하지 않도록 한다.
- 격리 API는 실제 운영 RDS에 접속하지 않는다.
- 운영 도메인과 별도의 시험 접속 경로를 사용한다.
- 테스트 데이터의 접근 권한과 보존 정책을 확인한다.

### 5-3. 현재 격리 환경 가능성

2026-11-03 기준:

```text
전체 파일시스템: 53GB
남은 공간: 31GB
Replica 데이터: 9.7GB
마트 데이터: 4.0GB
사용 가능 메모리: 약 9.5GiB
```

동일 VM에 별도 테스트 MySQL을 구성할 수 있을 가능성은 있으나, 데이터 복원과 임시 파일까지 고려한 충분한 용량은 아직 확정하지 않았다.

**격리본의 실제 생성·복원은 별도 승인 이후 실시한다.**

### 5-4. 격리 리허설 검증 항목

| 항목 | 검증 내용 |
|---|---|
| MySQL 승격 | 복제 중지 및 쓰기 허용 전환 |
| DB 연결 | VM 계정계 API가 격리 MySQL에 연결 |
| API 기동 | Spring Boot 정상 기동 |
| 로그인 | 시험 고객 로그인 성공 |
| 계좌 조회 | 정상 데이터 조회 |
| 당행이체 | 승인된 시험 계좌 간 이체 |
| 원장·GL | 이체 후 잔액과 분개 정합성 |
| 만기·중도해지 | 준비된 시험 데이터에서 동작 검증 |
| HTTPS | 시험 접속 경로에서 TLS 연결 |
| FE | API 연동 정상 확인 |
| 장애 복구 시간 | 리허설 시작부터 업무 정상화까지 측정 |

운영 Replica 또는 운영 RDS를 사용하는 쓰기 테스트는 수행하지 않는다.

---

## 6. 실제 DR 전환 절차

> **이 절은 승인 후 실행하는 절차다.**
>
> 현재 실제 운영 전환은 수행하지 않았다.
>
> 아래 명령을 실행하기 전에 P2·P5·리드 간 실행 시점 및 중단 조건을 합의한다.

### 6-1. Gate A — 전환 승인

담당자 확인:

| 역할 | 담당 |
|---|---|
| DR 전환 총괄 | P2 |
| VM·API 실행 환경 지원 | P5 |
| 거래 정합성 협조 | P3·P4 |
| 운영 전환 최종 승인 | 리드 및 합의된 승인자 |
| DNS 변경 실행 | 권한 확인 후 담당자 지정 |

실행 체크리스트:

- [ ] 전환 목적과 장애 상황을 기록했다.
- [ ] 전환 시작 시각을 합의했다.
- [ ] 원본 쓰기 차단 방법을 확인했다.
- [ ] 백업과 복구 경로를 확인했다.
- [ ] VM 계정계 API 기동 절차를 전달받았다.
- [ ] DNS 변경 권한과 기존 레코드를 확보했다.
- [ ] 주요 업무 검증 계정을 준비했다.
- [ ] 실행 중단 조건을 공유했다.

### 6-2. Gate B — 원본 쓰기 차단

계획된 DR 전환에서는 먼저 AWS 계정계의 모든 쓰기 경로를 차단한다.

차단 대상:

1. 외부 API의 신규 거래 요청
2. 이미 처리 중인 트랜잭션
3. COB 및 영업일 변경 배치
4. 자동·예약이체 관련 스케줄러
5. 감사 적재 및 기타 DB 쓰기 작업
6. 관리자·유지보수 작업
7. 다른 서비스에서 RDS로 직접 수행하는 쓰기

ALB에서 외부 요청을 차단하는 것만으로 모든 DB 쓰기가 차단되는 것은 아니다.

API 컨테이너 내부의 배치와 스케줄러는 계속 실행될 수 있기 때문이다.

완료 조건:

- [ ] 신규 거래가 차단됐다.
- [ ] 진행 중인 거래가 종료 또는 안전하게 중단됐다.
- [ ] 백그라운드 쓰기가 중지됐다.
- [ ] 원본 DB에 새로운 쓰기가 발생하지 않는 상태를 확인했다.
- [ ] 최종 원본 GTID를 기록했다.

이 시점을 `T_SOURCE_FROZEN`으로 기록한다.

### 6-3. Gate C — 최종 GTID 동기화

원본 쓰기가 중지된 상태에서 마지막 RDS GTID를 확인한다.

```sql
SELECT @@GLOBAL.gtid_executed AS source_gtid;
```

Replica에서 해당 GTID가 모두 적용됐는지 조회한다.

```sql
SELECT GTID_SUBTRACT(
    '<최종 원본 GTID 집합>',
    @@GLOBAL.gtid_executed
) AS missing_gtids;
```

승격 조건:

```text
missing_gtids = 빈 문자열
Replica SQL 오류 = 0
최종 GTID 기록 완료
```

조건을 만족하지 못하면 승격하지 않는다.

실제 원본 장애로 마지막 GTID를 확인할 수 없는 경우에는 마지막으로 관측한 원본 GTID와 Replica 적용 상태를 기록하고, 데이터 유실 가능성을 미확정 상태로 표시한다.

### 6-4. Gate D — Replica 승격

**다음 명령은 운영 Replica 상태를 변경한다. 실행 승인 전 사용 금지.**

최종 GTID 동기화와 원본 쓰기 차단이 확인된 후 복제를 중지한다.

```bash
# 승인된 실제 DR 전환에서만 실행

docker exec -i info-replica-mysql sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root_pw) exec mysql -uroot' <<'SQL'
STOP REPLICA;
SHOW REPLICA STATUS\G
SQL
```

복제 중지 결과를 확인한다.

확인 항목:

- [ ] IO 스레드 중지
- [ ] SQL 스레드 중지
- [ ] 최종 원본 GTID 적용 확인
- [ ] 복제 오류 확인
- [ ] 최종 적용 GTID 기록

이후 쓰기 방지를 해제한다.

```bash
# 승인된 실제 DR 전환에서만 실행

docker exec -i info-replica-mysql sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root_pw) exec mysql -uroot' <<'SQL'
SET GLOBAL super_read_only = OFF;
SET GLOBAL read_only = OFF;

SELECT
    @@GLOBAL.read_only AS read_only,
    @@GLOBAL.super_read_only AS super_read_only,
    @@GLOBAL.gtid_executed AS executed_gtid;
SQL
```

승격 후 기대 상태:

```text
read_only = 0
super_read_only = 0
```

이 시각을 `T_PROMOTED`로 기록한다.

**주의 사항**

- 현재 Replica의 `read_only` 설정은 영구 설정으로 저장된 이력이 있다.
- 위 명령은 실행 중인 인스턴스의 상태를 변경한다.
- MySQL 또는 컨테이너 재시작 후 읽기 전용 상태가 어떻게 되는지는 별도로 확인해야 한다.
- 재기동 정책은 P5의 VM 운영 구성과 함께 확정한다.
- 복제 메타데이터와 GTID 이력은 복구 검증을 위해 보존한다.

다음 명령은 기본 승격 절차에서 실행하지 않는다.

```sql
-- 실행 금지: 복제/GTID 이력 보존
-- RESET REPLICA;
-- RESET REPLICA ALL;
-- RESET BINARY LOGS AND GTIDS;
```

승격 후에도 API를 외부에 공개하기 전까지 원본 AWS의 쓰기 차단 상태를 유지한다.

### 6-5. Gate E — VM 계정계 API 기동

VM 계정계 API 실행 환경은 #849에서 P5가 준비한다.

P5로부터 다음 정보를 인계받는다.

- [ ] 계정계 API Docker 이미지 및 SHA
- [ ] Docker Compose 경로
- [ ] 실행 및 종료 명령어
- [ ] Spring Profile
- [ ] DB 접속 호스트와 포트
- [ ] MySQL TLS 연결 방법
- [ ] API 헬스체크 경로
- [ ] 운영 비밀 및 OTP 키 주입 방식
- [ ] FE 및 HTTPS 실행 경로
- [ ] 외부 연동 및 Mock 연결 방식

기존 `application-prod.yml`은 다음 형태의 DB 연결을 사용한다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/minicore?sslMode=REQUIRED&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true
```

VM API에서 `DB_HOST`가 승격한 MySQL에 연결되는지 확인해야 한다.

특히 Docker 컨테이너 내부의 `localhost`는 일반적으로 해당 컨테이너 자신이다.

현재 Replica가 호스트 `127.0.0.1:3306`에 바인딩되어 있으므로, API의 Docker 네트워크 구성에 맞는 실제 DB 접속 경로를 검증한다.

API 기동 시 다음 요소도 확인한다.

- Flyway 자동 실행 여부
- JDBC Session 저장소
- 자동 실행되는 스케줄러
- OTP 및 개인정보 암호화 키
- 감사 로그의 S3 의존성
- HTTPS Secure Cookie
- CSRF Cookie Domain
- CORS 허용 도메인

**P5가 검증한 실행 명령어와 설정을 이 절에 추가한 뒤 실제 리허설을 진행한다.**

API 기동 완료 시각을 `T_API_HEALTHY`로 기록한다.

### 6-6. Gate F — DNS 및 접속 경로 전환

API 기동과 내부 검증이 완료되면 외부 접속 경로 전환을 검토한다.

전환 전 확인:

- [ ] 실제 DNS 관리 주체와 변경 권한을 확인했다.
- [ ] 기존 `api.corebank.cloud` 레코드를 보관했다.
- [ ] 기존 TTL을 확인했다.
- [ ] VM의 외부 접근 가능한 HTTPS 진입점을 확보했다.
- [ ] TLS 인증서가 도메인과 일치한다.
- [ ] 방화벽 및 443 포트 접근이 가능하다.
- [ ] FE API 주소와 CORS 설정이 일치한다.
- [ ] 기존 AWS 경로의 쓰기가 차단되어 있다.

**VM의 사설 IP `192.168.0.211`을 외부 공개 DNS에 등록한다고 해서 외부 접속이 가능한 것은 아니다.**

공인 접근 경로와 포트 전달 또는 프록시 구성이 먼저 준비되어야 한다.

또한 CNAME 레코드의 대상에는 IP 주소가 아닌 DNS 이름을 사용해야 한다. 공인 IP를 직접 지정하는 구성이라면 적절한 A 레코드를 사용한다.

DNS 변경은 승인받은 담당자가 수행한다.

변경 후 확인:

```bash
# 조회용 명령: 운영 레코드 변경 없이 실행 가능
dig api.corebank.cloud

dig api.corebank.cloud @1.1.1.1

dig api.corebank.cloud @8.8.8.8
```

DNS 결과뿐 아니라 실제 HTTPS 연결과 업무 API 정상 응답도 확인한다.

DNS TTL 및 캐시로 인해 기존 경로와 신규 경로가 일정 시간 공존할 수 있으므로, 이 기간에도 기존 AWS에 쓰기가 발생하지 않도록 유지한다.

DNS 변경 시각을 `T_DNS_CHANGED`로 기록한다.

### 6-7. Gate G — 업무 정상 동작 확인

다음 순서로 확인한다.

| 순서 | 검증 항목 | 정상 조건 |
|---|---|---|
| 1 | API Health | 정상 응답 |
| 2 | 로그인 | 시험 고객 로그인 성공 |
| 3 | 계좌 조회 | 잔액·상태 정상 |
| 4 | 당행이체 | 성공 응답 및 거래 저장 |
| 5 | 이체 후 잔액 | 출금·입금 계좌 잔액 정합 |
| 6 | 원장 조회 | 이체 원장과 거래번호 일치 |
| 7 | GL 기표 | 분개 차변·대변 합계 일치 |
| 8 | 만기·중도해지 | 준비된 시험 시나리오 성공 |
| 9 | 정보계 조회 | 연동 경로 및 응답 확인 |
| 10 | 외부 연동 | 준비된 Mock 경로 정상 |

실제 리허설에서는 반드시 승인된 시험 계정을 사용한다.

다음 조건까지 확인한다.

- 중복 이체가 발생하지 않는다.
- 실패 거래가 성공으로 처리되지 않는다.
- 원장과 계좌 잔액이 일치한다.
- GL 기표 결과가 정상이다.
- API 로그에 DB 연결·TLS 오류가 없다.
- 신규 거래가 승격한 VM MySQL에 저장된다.

모든 필수 업무가 정상 동작하는 시각을 `T_BUSINESS_OK`로 기록한다.

---

## 7. RTO/RPO 측정

### 7-1. 측정 기준

| 지표 | 정의 |
|---|---|
| RTO | 장애 또는 전환 시작부터 업무 서비스 복구까지 걸린 시간 |
| RPO | 복구된 데이터가 장애 이전 마지막 확정 데이터와 얼마나 차이 나는지 |
| `T0` | 장애 인지 또는 계획된 전환 시작 |
| `T_SOURCE_FROZEN` | 원본 쓰기 차단 완료 |
| `T_PROMOTED` | Replica 승격 완료 |
| `T_API_HEALTHY` | VM 계정계 API 헬스체크 성공 |
| `T_DNS_CHANGED` | DNS 변경 완료 |
| `T_BUSINESS_OK` | 사용자 경로에서 핵심 업무 검증 완료 |

각 시각은 KST 기준으로 기록하고 측정에 사용한 시계와 명령도 명시한다.

### 7-2. RTO 계산

```text
RTO = T_BUSINESS_OK - T0
```

계획된 전환 리허설과 실제 장애 복구는 구분한다.

격리 리허설에서 운영 DNS를 변경하지 않았다면 DNS 전파에 필요한 시간은 실측 RTO에 포함되지 않았다고 명시한다.

### 7-3. RPO 평가

계획된 전환에서는 다음 조건을 확인한다.

1. 원본 쓰기 차단 완료
2. 원본 최종 확정 GTID 기록
3. Replica에서 원본 GTID 전부 적용
4. 원본에 추가 쓰기가 발생하지 않음
5. 주요 거래 및 원장 정합성 확인

이 경우 확인한 거래 범위에서 미복제 트랜잭션이 없었다고 기록할 수 있다.

단, 전체 데이터 유실 0건을 입증하려면 GTID 외에도 거래 정합성 근거가 필요하다.

실제 장애로 RDS에 접근하지 못하는 경우에는 마지막으로 확인한 원본 GTID와 시각을 기록한다.

최종 커밋 시점의 원본 데이터가 확인되지 않는다면 다음과 같이 판정한다.

```text
RPO: 정확한 측정 불가
사유: 장애 시점의 원본 최종 트랜잭션 확인 불가
확인 가능 정보: 마지막 관측 원본 GTID, Replica 적용 GTID
```

단순히 `Seconds_Behind_Source=0`이라는 이유로 RPO를 0으로 기록하지 않는다.

### 7-4. Tier별 목표

`tasks.md` PH-54 기준:

| Tier | 업무 | RTO 목표 | RPO 목표 |
|---|---|---|---|
| Tier 1 | 원장·결산 | 15분 이내 | 약 0 |
| Tier 2 | 온라인 업무 | 1시간 이내 | 5분 이내 |
| Tier 3 | 알림·관리자 | 4시간 이내 | 별도 정의 필요 |
| Tier 4 | 정보계·AI | 24시간 이내 | 별도 정의 필요 |

실제 측정 결과가 없는 항목은 통과로 표시하지 않는다.

---

## 8. Failback — AWS 복귀 절차

### 8-1. Failback의 의미

Failback은 DR 환경으로 전환한 서비스를 원래 AWS 환경으로 복귀시키는 절차다.

그러나 VM으로 승격한 MySQL에서 새로운 거래가 발생한 경우, 기존 AWS RDS와 VM DB는 서로 다른 데이터를 가질 수 있다.

따라서 **DNS만 AWS로 되돌리는 것은 안전한 Failback이 아니다.**

### 8-2. 상황 A — VM에서 신규 쓰기가 발생하지 않은 경우

전환 직후 오류가 발생했고 VM에서 업무 거래가 처리되지 않은 경우다.

진행 조건:

- [ ] VM에서 신규 업무 쓰기가 발생하지 않았음을 확인했다.
- [ ] 기존 AWS RDS가 정상이며 데이터 정합성이 확인됐다.
- [ ] 기존 AWS 접속 경로가 정상이다.
- [ ] 동시 쓰기 위험이 없다.
- [ ] 복귀 승인을 받았다.

이 경우 원본 RDS 기준으로 서비스를 복귀하는 방안을 검토한다.

단, 승격 과정에서 변경된 MySQL 복제 설정을 복구해야 하므로 기존 Replica 상태로 단순히 되돌아간다고 가정하지 않는다.

### 8-3. 상황 B — VM에서 신규 쓰기가 발생한 경우

VM이 새로운 Primary로 거래를 처리한 상태다.

이 경우 다음 절차가 필요하다.

1. VM의 신규 쓰기를 안전하게 중단한다.
2. VM 최종 GTID를 기록한다.
3. AWS RDS의 마지막 데이터 상태와 비교한다.
4. VM에서 발생한 거래·원장·GL 변경분을 확인한다.
5. 재동기화 전략을 결정한다.
6. 독립 환경에서 복구 방법을 검증한다.
7. AWS의 단일 쓰기 경로를 준비한다.
8. API 및 DNS를 전환한다.
9. 주요 금융 업무와 데이터 정합성을 재검증한다.

재동기화 방안은 다음 중 환경에 맞는 방법을 검토한다.

| 방법 | 설명 | 주의사항 |
|---|---|---|
| AWS DB 재구축 | VM 최종 데이터를 기준으로 AWS DB를 재구축 | 백업·복원 시간과 접근 권한 확인 |
| 역방향 복제 | VM 변경분을 AWS에 복제 | RDS 지원 방식·GTID·충돌 검증 필요 |

실제 방법은 RDS MySQL의 관리형 제약과 사용할 수 있는 복제 권한을 검증한 후 결정한다.

**검증되지 않은 역방향 복제 명령은 실행하지 않는다.**

### 8-4. Failback 완료 조건

- [ ] AWS DB가 최종 거래 데이터를 반영했다.
- [ ] AWS와 VM이 동시에 쓰기를 처리하지 않는다.
- [ ] 계좌 잔액과 원장 정합성이 확인됐다.
- [ ] GL 정합성이 확인됐다.
- [ ] 계정계 API 정상 응답을 확인했다.
- [ ] 로그인과 이체 시험에 성공했다.
- [ ] HTTPS·DNS 전환을 확인했다.
- [ ] 복귀 완료 시각과 변경 이력을 기록했다.

### 8-5. AWS 인프라 철거와의 관계

프로젝트 D-30에 따르면 S4 재측정 이후 AWS 인프라를 철거하고, 11/18 최종 발표는 온프레미스 VM에서 진행할 예정이다.

따라서 실제 AWS 인프라가 철거된 뒤에는 Failback을 즉시 실행할 수 없다.

이 경우 다음 두 항목을 명확히 구분한다.

- Failback 절차 설계 및 위험 검토
- 실제 AWS 복귀 실행 및 실측

실제 복귀를 수행하지 않았다면 완료했다고 기록하지 않는다.

---

## 9. 전환 실패 및 중단 조건

| 문제 상황 | 조치 |
|---|---|
| 원본 RDS 쓰기 차단 실패 | 승격·DNS 전환 중단 |
| Replica GTID 미적용 | 승격 중단 |
| Replica IO/SQL 오류 | 원인 분석 후 재판정 |
| VM 디스크 공간 부족 | 격리 시험 또는 전환 보류 |
| VM 계정계 API 실행 실패 | 외부 트래픽 전환 중단 |
| API의 DB 연결 실패 | DB 호스트·TLS·권한 확인 |
| HTTPS 인증서 오류 | DNS 전환 중단 |
| FE·CORS·CSRF 오류 | 외부 업무 공개 보류 |
| 데이터 정합성 오류 | 신규 쓰기 중단 및 사고 원인 분석 |
| AWS·VM 동시 쓰기 위험 | 모든 신규 쓰기 중단, 단일 Primary 확보 |
| 복귀용 데이터 재동기화 방법 미확정 | Failback 보류 |

데이터 정합성 문제가 발생한 상태에서는 서비스의 빠른 재개보다 거래 데이터 보존과 원인 분석을 우선한다.

---

## 10. 단일 장애점(SPOF) 및 추가 위험

| 위험 | 현재 구조의 영향 | 대응 방향 |
|---|---|---|
| 온프레미스 VM 1대 | VM 장애 시 DR 서비스 전체 중단 | 단일 VM 제약 기록 |
| VM 단일 디스크 | MySQL·마트 데이터 동시 영향 | 백업·공간 감시 |
| VPN 단절 | RDS → VM 복제 중단 | VPN 상태 및 복제 상태 점검 |
| 비동기 복제 | 마지막 거래 미복제 가능 | GTID·거래 정합성 확인 |
| VM 계정계 API 단일 인스턴스 | API 장애 시 대체 경로 없음 | 재기동·복구 절차 |
| DNS 전환 | TTL과 캐시에 따른 지연 | 사전 TTL 및 외부 경로 검증 |
| HTTPS 인증서 | 인증서 미준비 시 접속 실패 | P5 #849에서 검증 |
| Spring Session JDBC | DB 전환 시 로그인 세션 영향 | 재로그인·세션 정책 확인 |
| OTP·암호화 키 | 키 누락 시 인증·복호화 실패 | 비밀 주입 검증 |
| Flyway 자동 실행 | 의도하지 않은 DB DDL 실행 가능 | 기동 전 설정·이력 검증 |
| COB·자동이체 스케줄러 | 전환 도중 예상 외 DB 쓰기 발생 가능 | 쓰기 차단 범위에 포함 |
| 감사 S3 의존성 | AWS 철거 후 적재 실패 가능 | 운영 가능 범위 및 대체 정책 확인 |
| 정보계 ETL | 승격·DB 부하로 적재에 영향 가능 | 타이머·적재 일정 확인 |

현재 VM은 DR의 대체 운영 환경이지만, 단일 VM 구성이라는 제약이 있다.

따라서 AWS보다 높은 가용성을 보장하는 환경으로 간주하지 않는다.

---

## 11. 리허설 결과 기록

### 11-1. 기본 정보

| 항목 | 값 |
|---|---|
| 관련 이슈 | #886 |
| 리허설 일시 | 미정 |
| 진행 담당 | P2 |
| 협조 담당 | P5·P3·P4 |
| 테스트 환경 | 미확정 |
| 실행 승인자 | 미정 |
| 계정계 API 이미지 SHA | 미확인 |
| 실제 운영 DNS 변경 여부 | 미실시 |

### 11-2. 사전 점검 결과

| 항목 | 결과 |
|---|---|
| VM 메모리·디스크 | 11/3 점검 완료 |
| Replica IO/SQL 스레드 | Yes / Yes |
| 복제 지연 | 0초 |
| 복제 IO/SQL 오류 | 0 / 0 |
| Replica GTID | `1-10071` 관측 |
| Replica 읽기 전용 설정 | ON / ON |
| 릴리스 3 Flyway 이력 | 최신 3건 성공 확인 |
| 최종 RDS·VM GTID 비교 | 실제 전환 기준점 미확인 |
| 격리 DB 구축 | 미실시 |
| API 기동 환경 | P5 #849 확인 필요 |

위 복제 결과는 2026-11-03 점검 당시의 상태다.

실제 리허설에서는 새로운 기준점으로 다시 측정한다.

### 11-3. 전환 시각

| 단계 | 시각(KST) | 상태 |
|---|---|---|
| T0 — 장애/전환 시작 | — | 미측정 |
| 원본 쓰기 차단 | — | 미실시 |
| 최종 원본 GTID 기록 | — | 미실시 |
| Replica GTID 적용 완료 | — | 미실시 |
| T_PROMOTED — Replica 승격 | — | 미실시 |
| T_API_HEALTHY — API 기동 | — | 미실시 |
| T_DNS_CHANGED — DNS 변경 | — | 미실시 |
| T_BUSINESS_OK — 업무 정상화 | — | 미실시 |

### 11-4. 업무 검증 결과

| 시나리오 | 결과 | 근거 |
|---|---|---|
| 로그인 | 미실시 | — |
| 계좌 조회 | 미실시 | — |
| 당행이체 | 미실시 | — |
| 원장 정합성 | 미실시 | — |
| GL 정합성 | 미실시 | — |
| 만기해지 | 미실시 | — |
| 중도해지 | 미실시 | — |
| 정보계 조회 | 미실시 | — |
| HTTPS·DNS | 미실시 | — |
| Failback | 미실시 | — |

### 11-5. RTO/RPO 평가

| 항목 | 결과 |
|---|---|
| 총 RTO | 미측정 |
| Tier 1 RTO 15분 이내 | 미판정 |
| Tier 2 RTO 1시간 이내 | 미판정 |
| RPO | 미측정 |
| 데이터 유실 건수 | 미확인 |
| 중복 거래 건수 | 미확인 |
| 데이터 정합성 | 미검증 |
| 최종 판정 | 리허설 미실시 |

결과는 실제 측정 이후 근거와 함께 작성한다.

---

## 12. 실행 전 남은 작업

### P2 — DR 총괄

- [x] #886 작업 범위 확인
- [x] VM 메모리·디스크 확인
- [x] Replica IO/SQL·GTID 확인
- [x] 릴리스 3 Flyway 반영 확인
- [ ] 최종 DR 전환 판단 기준 팀 확인
- [ ] 격리 리허설 환경 및 실행 시점 확정
- [ ] 원본 쓰기 차단 방법 확정
- [ ] P5 API 기동 절차 인계
- [ ] DNS 변경 권한과 경로 확인
- [ ] 승인된 리허설 실행
- [ ] RTO/RPO 측정
- [ ] Failback 검증
- [ ] 결과 문서 및 `tasks.md` 반영

### P5 — VM 실행 환경 (#849)

- [ ] 계정계 API 이미지 및 실행 구성 전달
- [ ] API 시작·종료 명령어 전달
- [ ] API ↔ VM MySQL 접속 검증
- [ ] API 헬스체크 경로 전달
- [ ] FE·HTTPS·DNS 접속 경로 확정
- [ ] OTP·환경변수·비밀 주입 확인
- [ ] 시연 데이터와 외부 연동 준비
- [ ] AWS 인프라 철거 시점 협의

### 팀 승인 필요

- [ ] S4 측정·UAT와 충돌하지 않는 리허설 시점
- [ ] 실제 Replica 승격 허용 여부
- [ ] AWS 원본 쓰기 차단 절차
- [ ] 가비아 DNS 변경 권한·실행자
- [ ] Failback 실습 범위
- [ ] AWS 철거 전 최종 데이터 보존 기준

---

## 13. 참고 자료

### 프로젝트 문서

- [PH-54 관련 이슈 #886](https://github.com/corebank-tech/corebank-server/issues/886)
- [RDS Multi-AZ 복구 훈련 #885](https://github.com/corebank-tech/corebank-server/issues/885)
- [VM 계정계 API 실행 환경 #849](https://github.com/corebank-tech/corebank-server/issues/849)
- [RDS → VM 복제 검증 #875](https://github.com/corebank-tech/corebank-server/issues/875)
- `docs/phase2/tasks.md` — PH-54
- `docs/phase2/README.md` — D-25, D-30, D-35
- `docs/phase2/ph76_etl_ops_runbook.md`
- `docs/phase2/ph100_vpn_vm_runbook.md`
- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`

### MySQL 공식 문서

- [SHOW REPLICA STATUS](https://dev.mysql.com/doc/refman/8.4/en/show-replica-status.html)
- [STOP REPLICA](https://dev.mysql.com/doc/refman/8.4/en/stop-replica.html)
- [GTID 개념 및 저장](https://dev.mysql.com/doc/refman/8.4/en/replication-gtids-concepts.html)
- [GTID 기반 장애 복구](https://dev.mysql.com/doc/refman/8.4/en/replication-gtids-failover.html)

---

## 최종 상태

**2026-11-03 기준**

- DR 전환 절차: 문서 초안 작성
- VM 용량 점검: 완료
- Replica 복제 상태 점검: 완료
- 릴리스 3 DB 변경 반영 확인: 완료
- 격리 리허설: 미실시
- 실제 Replica 승격: 미실시
- 운영 DNS 전환: 미실시
- RTO/RPO 실측: 미실시
- Failback 실습: 미실시

실제 리허설 완료 후 측정 결과를 반영하고, 검증되지 않은 항목은 별도로 기록한다.
