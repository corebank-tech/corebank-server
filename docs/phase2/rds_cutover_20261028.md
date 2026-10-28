# PH-70 — 2026-10-28 RDS 최종 컷오버 복원 및 데이터 검증 결과

## 1. 개요

- 담당: P2
- 작업일: 2026-10-28 (KST)
- 리전: `ap-northeast-2`
- 목적: 1차 AWS 운영 RDS의 최신 데이터를 2차 AWS 계정으로 이전하고, 신규 RDS 복원 및 주요 데이터 정합성을 검증한다.
- 범위: 최종 스냅샷 생성, KMS 재암호화, 계정 간 복사, Terraform RDS 복원, 원본·복원본 데이터 비교
- 범위 밖: 애플리케이션 전환, DNS 전환, 실제 업무 API 검증, VM MySQL 복제

### 작업 결과

**RDS 스냅샷 이전·복원 및 주요 데이터 검증 통과**

- 최종 복사 스냅샷 `available`, 진행률 100%
- 신규 RDS `available`
- MySQL 8.4.10, Multi-AZ, 백업 7일, 삭제 보호 활성화
- 원본·복원본 주요 8개 테이블 건수 전부 일치
- 총 19,927,879건 비교, 차이 0건
- 전체 일반 테이블 47개 일치
- Flyway 최신 순번 81 및 최근 10개 이력 일치
- Flyway 실패 이력 0건
- TLS 접속 및 MySQL 주요 파라미터 확인

단, 이는 **RDS 계층 검증 결과**이며 전체 서비스 컷오버 완료를 의미하지 않는다.

## 2. 환경 및 복원 경로

### AWS 환경

| 항목 | 기존 운영 환경 | 신규 환경 |
|---|---|---|
| AWS 계정 | `883218392320` | `853900964665` |
| 리전 | `ap-northeast-2` | `ap-northeast-2` |
| RDS 식별자 | `corebank-database` | `corebank-rds` |
| 엔진 | MySQL 8.4.10 | MySQL 8.4.10 |
| DB | `minicore` | `minicore` |
| 스냅샷 작업 | 생성·재암호화·공유 | 계정 내 복사·복원 |

### 스냅샷 이전 경로

1. 기존 운영 RDS에서 최신 수동 스냅샷 생성
   - `corebank-cutover-20261028`
2. 기존 계정의 고객 관리형 KMS 키로 재암호화 복사
   - `corebank-share-cutover-20261028`
3. 기존 계정에서 신규 계정 `853900964665`에 복원 권한 공유
4. 신규 계정의 KMS 키로 재암호화 복사
   - `corebank-import-cutover-20261028`
5. Terraform에서 최종 복사본을 `snapshot_identifier`로 지정해 RDS 생성

### 최종 복사본 검증

| 항목 | 결과 |
|---|---|
| Snapshot ID | `corebank-import-cutover-20261028` |
| Status | `available` |
| PercentProgress | 100 |
| Encrypted | `true` |
| EngineVersion | `8.4.10` |
| KMS 소유 계정 | `853900964665` |
| SourceDBSnapshotIdentifier | `arn:aws:rds:ap-northeast-2:883218392320:snapshot:corebank-share-cutover-20261028` |

최종 복사본이 신규 계정 소유의 암호화된 수동 스냅샷임을 확인했다.

## 3. 단계별 소요 시간

### 3.1. CLI 관측 시간

| 단계 | 확인된 완료 시각 (KST) | 소요 시간 |
|---|---|---:|
| 기존 RDS 최신 스냅샷 생성 | 12:21:52 | 557초 |
| 공유용 KMS 재암호화 복사 | 12:25:14 | 109초 |
| 신규 계정 스냅샷 복사 | 12:29:47 | 169초 |
| Terraform RDS 복원·available 확인 | 12:53:12 | 916초 |
| **측정 시간 합계** | - | **1,751초 (29분 11초)** |

각 소요 시간은 CLI 요청 직전부터 `wait` 또는 상태 확인이 성공할 때까지의 관측 시간이다.

- 단계 사이의 사용자 조작 및 대기 시간은 합계에서 제외한다.
- AWS 이벤트 내부 처리 시간과 CLI 관측 시간은 다를 수 있다.
- 스냅샷 공유 권한 설정에는 독립적인 소요 시간 측정을 적용하지 않았다.
- 서비스 쓰기 중단부터 신규 서비스 쓰기 재개까지의 전체 컷오버 시간은 별도 확인이 필요하다.

### 3.2. 원본 스냅샷 생성

| 항목 | 시각 |
|---|---|
| AWS SnapshotCreateTime | 2026-10-28 12:12:51.858 KST |
| CLI available 확인 | 2026-10-28 12:21:52 KST |
| CLI 관측 소요 시간 | 557초 |

`SnapshotCreateTime`은 AWS 스냅샷 메타데이터의 시각이다.
이 시각과 CLI 완료 시각의 차이를 AWS 내부 백업 처리 시간으로 간주하지 않는다.

### 3.3. 신규 RDS 복원

| 항목 | 시각 |
|---|---|
| Terraform apply 시작 | 2026-10-28 12:37:56 KST |
| available 최초 관측 | 2026-10-28 12:53:12 KST |
| CLI 관측 소요 시간 | **916초 (15분 16초)** |

최초 available 확인 시각은 10초 간격으로 실행한 AWS CLI 상태 모니터링 결과이다.
실제 AWS 내부 완료 시각과는 폴링 간격만큼 차이가 있을 수 있다.

### 3.4. AWS RDS 이벤트 기준 처리 시간

2026-10-28 AWS RDS `describe-events` 결과를 기준으로
스냅샷 생성·복사와 RDS 내부 설정 작업 시간을 측정했다.

AWS 이벤트 시각은 UTC에서 KST(UTC+9)로 변환했으며,
동일 작업의 시작·완료 이벤트 간 차이를 밀리초 단위로 계산했다.

#### 스냅샷 생성 및 복사

| 단계 | 시작(KST) | 완료(KST) | 소요 시간 |
|---|---|---|---:|
| 원본 수동 스냅샷 생성 | 12:12:39.421 | 12:21:22.876 | 523.455초 |
| 공유용 KMS 재암호화 복사 | 12:23:56.324 | 12:24:58.352 | 62.028초 |
| 신규 계정 스냅샷 복사 | 12:27:27.199 | 12:29:29.523 | 122.324초 |
| **3개 구간 합계** | - | - | **707.807초 (11분 47.807초)** |

합계는 AWS 이벤트로 시작과 완료를 확인한 세 작업 구간의 합이다.
단계 사이의 수동 대기, IAM/KMS 권한 확인 및 공유 설정 시간은 제외한다.

#### 신규 RDS 복원 및 설정 이벤트

| 시각(KST) | AWS 이벤트 |
|---|---|
| 12:40:49.498 | DB instance restarted |
| 12:41:03.544 | Restored from snapshot |
| 12:41:07.130 | CloudWatch Logs Export enabled |
| 12:42:09.352 | DB 파라미터 그룹 변경 시작 |
| 12:43:10.656 | DB 파라미터 그룹 변경 완료 |
| 12:43:12.384 | Multi-AZ 전환 시작 |
| 12:49:55.450 | Multi-AZ 전환 완료 |
| 12:49:57.795 | DB instance shutdown |
| 12:50:17.720 | DB instance restarted |
| 12:53:12 | CLI에서 최초 available 관측 |
| 12:54:30.204 | Enabled automated backups |
| 12:54:34.633 | DB instance shutdown |
| 12:54:44.990 | DB instance restarted |
| 12:55:25.295 | Backing up DB instance |
| 12:57:58.001 | Finished DB Instance backup |

주요 내부 작업 소요 시간:

| 작업 | AWS 이벤트 간 소요 시간 |
|---|---:|
| DB 파라미터 그룹 변경 | 61.304초 |
| Multi-AZ 전환 | **403.066초 (6분 43.066초)** |
| 자동 백업 실행 | 152.706초 |

#### CLI 관측 시간과 AWS 이벤트 시간의 차이

| 단계 | CLI 관측 | AWS 이벤트 |
|---|---:|---:|
| 원본 스냅샷 생성 | 557초 | 523.455초 |
| 공유용 KMS 복사 | 109초 | 62.028초 |
| 신규 계정 스냅샷 복사 | 169초 | 122.324초 |
| RDS 복원~최초 available | 916초 | 전체 구간 대응 이벤트 없음 |

CLI 관측 시간은 요청·상태 폴링 및 응답 대기를 포함하는
클라이언트 관측값이다.

AWS 이벤트 시간은 AWS가 기록한 특정 내부 작업 구간이며,
두 측정값은 시작·완료 기준이 달라 직접적인 성능 비교 대상이 아니다.

특히 `Restored from snapshot` 이벤트는 전체 RDS 준비 완료를
의미하지 않는다.

RDS는 12:53:12에 처음 available로 관측되었으나,
이후 자동 백업 활성화, DB 재시작 및 백업 완료 이벤트가 추가로 발생했다.

따라서 다음 시간을 별도로 구분한다.

- Terraform apply 시작: 12:37:56 KST
- 최초 available 관측: 12:53:12 KST
- 최초 available까지 CLI 관측: 916초 (15분 16초)
- 마지막으로 확인된 자동 백업 완료 이벤트: 12:57:58.001 KST

12:57:58.001은 마지막으로 수집한 백업 완료 이벤트 시각이며,
서비스 전체 컷오버 완료 또는 최종 DB 정상화 확인 시각으로
간주하지 않는다.

동일 RDS 식별자의 오전 09:33~09:36 삭제 이벤트는
이전 인스턴스에 관한 기록이므로 최종 복원 시간에서 제외한다.

## 4. Terraform 복원 및 RDS 설정 검증

### 4.1. Terraform 적용

복원에 사용한 스냅샷:

`corebank-import-cutover-20261028`

Terraform 실행 명령:

```bash
terraform plan \
  -var="snapshot_identifier=corebank-import-cutover-20261028" \
  -out=cutover-rds-restore.tfplan

terraform apply cutover-rds-restore.tfplan
```

최종 적용 전에 확인한 계획:

```text
Plan: 1 to add, 0 to change, 0 to destroy.
```

생성 대상:

`module.data.aws_db_instance.main`

기존 네트워크 리소스를 교체하지 않고 RDS 1개만 생성하도록 계획을 확정했다.

복원 후 Terraform state에서 `snapshot_identifier`가
`corebank-import-cutover-20261028`임을 확인했다.

### 4.2. AWS 실제 설정

| 항목 | 검증 결과 |
|---|---|
| Identifier | `corebank-rds` |
| Status | `available` |
| Engine | MySQL 8.4.10 |
| InstanceClass | `db.t3.small` |
| AllocatedStorage | 20 GiB |
| StorageType | `gp3` |
| MultiAZ | `true` |
| StorageEncrypted | `true` |
| KMS 계정 | `853900964665` |
| DBSubnetGroup | `corebank-rds-subnet-group` |
| SecurityGroup | `sg-0a0991d8a04bcbeb6` |
| BackupRetentionPeriod | 7일 |
| DeletionProtection | `true` |
| PubliclyAccessible | `false` |
| ParameterGroup | `corebank-mysql84-params` |
| ParameterApplyStatus | `in-sync` |
| PendingModifiedValues | `{}` |

신규 RDS 엔드포인트:

`corebank-rds.c7wwueokc5v4.ap-northeast-2.rds.amazonaws.com:3306`

AWS 조회 결과에서 주요 설정이 Terraform의 목표 구성과 일치했다.

## 5. MySQL 접속 및 보안 검증

신규 RDS는 퍼블릭 접근이 비활성화되어 있으므로 VPC 내부 EC2에서 Session Manager를 이용해 접속했다.

- EC2 OS: Ubuntu 24.04
- MySQL CLI: 8.0.46
- RDS MySQL 서버: 8.4.10
- 인증: 기존 운영 RDS 스냅샷에 포함된 DB 계정
- 접속: TLS 인증서 번들 및 인증서 검증 옵션 사용

### 연결 확인

```sql
SELECT VERSION();
SHOW DATABASES;
USE minicore;
SELECT DATABASE();
SHOW SESSION STATUS LIKE 'Ssl_cipher';
```

| 항목 | 원본 RDS | 신규 RDS |
|---|---|---|
| 서버 버전 | 8.4.10 | 8.4.10 |
| `minicore` | 존재 | 존재 |
| TLS 암호군 | `TLS_AES_128_GCM_SHA256` | `TLS_AES_256_GCM_SHA384` |
| TLS 암호화 연결 | 확인 | 확인 |

두 환경의 TLS 암호군은 다르지만 양쪽 모두 암호화 연결을 사용했다.

## 6. 원본·복원본 데이터 검증

### 6.1. 검증 방법

기존 운영 RDS와 신규 RDS에서 동일한 읽기 전용 SQL을 실행해 결과를 대조했다.

```sql
USE minicore;

SELECT 'customer' AS table_name, COUNT(*) AS row_count FROM customer
UNION ALL
SELECT 'account', COUNT(*) FROM account
UNION ALL
SELECT 'transfer', COUNT(*) FROM transfer
UNION ALL
SELECT 'ledger_entry', COUNT(*) FROM ledger_entry
UNION ALL
SELECT 'gl_journal_entry', COUNT(*) FROM gl_journal_entry
UNION ALL
SELECT 'gl_voucher', COUNT(*) FROM gl_voucher
UNION ALL
SELECT 'auto_transfer', COUNT(*) FROM auto_transfer
UNION ALL
SELECT 'audit_log', COUNT(*) FROM audit_log;
```

### 6.2. 비교 결과

| 테이블 | 원본 RDS | 신규 RDS | 차이 |
|---|---:|---:|---:|
| `customer` | 60,004 | 60,004 | 0 |
| `account` | 180,011 | 180,011 | 0 |
| `transfer` | 3,199,155 | 3,199,155 | 0 |
| `ledger_entry` | 6,624,475 | 6,624,475 | 0 |
| `gl_journal_entry` | 6,547,904 | 6,547,904 | 0 |
| `gl_voucher` | 3,273,952 | 3,273,952 | 0 |
| `auto_transfer` | 15,000 | 15,000 | 0 |
| `audit_log` | 27,378 | 27,378 | 0 |
| **합계** | **19,927,879** | **19,927,879** | **0** |

**판정: 주요 8개 테이블의 원본·복원본 건수가 모두 일치한다.**

원본 RDS의 조회 시간은 27.99초였다.
이 시간은 `COUNT(*)` 조회 시간이지 전체 복원 시간이나 업무 API 성능 수치가 아니다.

행 전체의 값에 대한 체크섬 비교는 수행하지 않았으므로,
건수 일치가 모든 데이터 값의 일치를 증명하지는 않는다.

### 6.3. 전체 테이블 수

```sql
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'minicore'
  AND table_type = 'BASE TABLE';
```

| 항목 | 원본 | 신규 |
|---|---:|---:|
| 일반 테이블 수 | 47 | 47 |

**판정: 전체 일반 테이블 수 일치.**

### 6.4. Flyway 이력

```sql
SELECT
    installed_rank,
    version,
    description,
    success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

SELECT COUNT(*) AS failed_migrations
FROM flyway_schema_history
WHERE success = 0;
```

| 항목 | 원본 | 신규 |
|---|---|---|
| 최신 installed_rank | 81 | 81 |
| 최신 version | `202610230925` | `202610230925` |
| 최신 description | `create event publication` | `create event publication` |
| 최근 10개 이력 | 동일 | 동일 |
| 실패 이력 | 0건 | 0건 |

**판정: 확인한 Flyway 최근 이력 및 실패 건수가 일치한다.**

애플리케이션을 실제 실행해 현재 코드 기준 Flyway validate/migrate를
수행한 결과는 이 검증에 포함하지 않는다.

## 7. MySQL 파라미터 검증

신규 RDS에서 다음 SQL을 실행했다.

```sql
SHOW VARIABLES WHERE Variable_name IN (
    'log_bin',
    'binlog_format',
    'gtid_mode',
    'enforce_gtid_consistency',
    'time_zone',
    'require_secure_transport'
);
```

| 파라미터 | 확인값 |
|---|---|
| `log_bin` | ON |
| `binlog_format` | ROW |
| `gtid_mode` | OFF_PERMISSIVE |
| `enforce_gtid_consistency` | ON |
| `require_secure_transport` | ON |
| `time_zone` | Asia/Seoul |

binlog 활성화 및 ROW 형식 적용은 확인했다.

그러나 `gtid_mode=OFF_PERMISSIVE`이므로 GTID 기반 자동 위치 지정 복제가
준비 완료된 상태로 볼 수 없다.

VM MySQL과 `SOURCE_AUTO_POSITION=1`을 사용하려면
소스 RDS의 `gtid_mode=ON` 전환 및 검증이 선행되어야 한다.
파일·위치 기반 복제를 선택하는 경우에는 해당 방식의 설정과 검증이 별도로 필요하다.

## 8. 수행 중 문제 및 조치

| 문제 | 원인 | 조치 및 결과 |
|---|---|---|
| 기존 RDS 조회 AccessDenied | `rds:DescribeDBInstances` IAM 권한 부족 | 기존 계정 IAM 권한 보완 후 조회 성공 |
| KMS DescribeKey ValidationException | 실제 ARN 대신 설명용 자리표시자 전달 | 기존 공유용 KMS 키 ARN 조회 후 정상 확인 |
| 보안 그룹 3개 교체 계획 | PH-18 브랜치의 한글 description과 dev의 영어 description 차이 | dev의 설명으로 맞춘 후 RDS 1개 생성 계획 확인 |
| Ubuntu apt 저장소 접속 실패 | EC2에서 외부 HTTP 80 연결 타임아웃, IPv6 경로 없음 | 저장소 HTTPS 변경 및 IPv4 사용 후 MySQL CLI 설치 성공 |

Terraform 브랜치 비교 결과:

- `modules/data/main.tf`: dev와 PH-18 브랜치의 GitHub 파일 동일
- `modules/network/security_groups.tf`: description 문자열 차이 존재
- 최초 복원 plan: `4 to add, 3 to destroy`
- 수정 후 복원 plan: `1 to add, 0 to change, 0 to destroy`

기존 보안 그룹의 불필요한 교체를 방지했다.

## 9. 이전 리허설과의 차이

| 항목 | 10/27 리허설 | 10/28 최종 복원 |
|---|---:|---:|
| 일반 테이블 | 35개 | 47개 |
| Flyway 최신 순번 | 54 | 81 |
| 주요 8개 테이블 건수 | 19,660,692 | 19,927,879 |

주요 테이블 건수는 **267,187건 증가**했다.

단, 10/27 리허설은 10/10 기준 스냅샷을 사용했고,
10/28 복원은 당일 최신 스냅샷을 사용했다.
따라서 두 결과의 차이는 서로 다른 데이터 기준 시점에 따른 비교이며,
원본·복원본 일치 검증은 10/28에 별도로 수행했다.

## 10. 완료 판정 및 후속 작업

### 이번 RDS 작업에서 확인한 항목

- [x] 최신 운영 RDS 수동 스냅샷 생성
- [x] 고객 관리형 KMS 키로 재암호화
- [x] 신규 계정으로 암호화 스냅샷 복사
- [x] Terraform을 통한 신규 RDS 복원
- [x] RDS available 및 Multi-AZ 확인
- [x] 백업 7일, 삭제 보호, 파라미터 적용 확인
- [x] 신규 VPC에서 MySQL TLS 접속
- [x] 주요 8개 테이블 원본·복원본 건수 일치
- [x] 전체 일반 테이블 47개 일치
- [x] Flyway 최근 이력 일치 및 실패 0건 확인
- [x] binlog 및 주요 MySQL 파라미터 확인
- [x] AWS 이벤트 기준 스냅샷 생성·복사 및 RDS 설정 작업 시간 수집

### 별도 검증 또는 후속 작업

- [ ] 전체 행 값의 체크섬 기반 정합성 검증
- [ ] 현재 애플리케이션의 Flyway validate/migrate 및 부팅 검증
- [ ] 핵심 API 및 실제 업무 거래 검증
- [ ] ALB·DNS 서비스 전환 결과 확인
- [ ] 신규 서비스 쓰기 재개 시각 및 전체 쓰기 중단 시간 기록
- [ ] VM MySQL binlog 복제 및 GTID 방식 최종 결정·검증
- [ ] 컷오버 후 Plan B 및 복구 경로 확인

수집한 AWS 이벤트만으로는 전체 RDS 복원 구간의 내부 처리 시간을 산정할 수 없다(§3.4). 복원부터 최초 available까지의 CLI 관측 시간은 916초다.

기존 운영 RDS와 10/28 수동 스냅샷은 후속 작업 및
롤백 정책이 확정되기 전까지 보존한다.

### 최종 판정

**2026-10-28 PH-70 RDS 스냅샷 이전·복원 완료. 주요 8개 테이블 건수 및 확인한 Flyway 최근 10개 이력·실패 건수 일치 확인.**

애플리케이션·DNS 전환 등 전체 서비스 컷오버의 최종 완료 여부는
별도 검증 결과를 통해 판정한다.

## 11. 관련 문서 및 이슈

- PH-70 RDS Terraform: `terraform/main/modules/data/README.md`
- 10/21 복제 리허설: #682 / PR #683
- 10/27 복원 리허설: #767 / PR #768
- RDS Terraform 모듈: #650
- PH-51 컷오버 및 복구: #696
