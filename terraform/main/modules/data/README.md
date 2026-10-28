# PH-70 — RDS Terraform

## 1. 개요

- 담당: P2
- 목적: 1차 AWS 계정의 RDS 데이터를 2차 계정으로 이전하고,
  신규 3-Tier 환경에 RDS MySQL을 구성한다.
- 10/21: Terraform 구현 및 스냅샷 계정 간 복제 리허설 완료
- 10/27: 신규 AWS 네트워크 apply, RDS 복원 및 DB 검증 완료
- 10/28: 최신 스냅샷 기반 최종 컷오버 예정
- 후속: 온프레미스 MySQL 복제 및 검증

## 2. RDS 구성

| 항목 | 설정 |
|---|---|
| 엔진 | MySQL 8.4 |
| 인스턴스 | db.t3.small |
| 고가용성 | Multi-AZ |
| 스토리지 | gp3 20GiB |
| 자동 확장 | 비활성화 |
| 자동 백업 | 7일 |
| 암호화 | 활성화 |
| 외부 접근 | 비활성화 |

### 파라미터 그룹

- time_zone: Asia/Seoul
- slow_query_log: 1
- long_query_time: 2
- binlog_format: ROW
- innodb_buffer_pool_size: {DBInstanceClassMemory*3/4}
- max_connections: 기본값 유지

## 3. 의존성

- PH-50 네트워크 모듈
  - data_subnet_ids
  - rds_sg_id
- 2차 AWS 계정으로 복사한 RDS 스냅샷
- Terraform 원격 상태 S3 접근 권한

## 4. 계정 간 스냅샷 이전

1. 1차 계정에서 고객 관리형 KMS 키 생성
2. 기존 수동 스냅샷을 해당 키로 암호화하여 복사
3. 2차 계정에 KMS 사용 권한 및 스냅샷 공유
4. 2차 계정에서 해당 계정의 KMS 키로 스냅샷 복사 (이번 리허설은 AWS 관리형 `alias/aws/rds` 사용)
5. 복사된 스냅샷의 상태와 엔진 버전 확인

### 2026-10-21 복제 리허설 결과

**목적:** AWS 계정 간 암호화 RDS 스냅샷 복제 가능 여부 및 소요 시간 측정

#### 복제 경로

- 원본: `corebank-after-ph60b-20261010`
- 1차 계정 공유용: `corebank-share-20261021`
- 2차 계정 복사본: `corebank-import-20261021`
- 리전: ap-northeast-2
- 엔진: MySQL 8.4.10
- 할당 스토리지: 20 GiB

#### 복제 시간 (RDS 이벤트 기준, KST)

| 단계 | 시작 | 완료 | 소요 시간 |
|---|---|---|---|
| 1차 계정 내부 복사 | 10:50:12.671 | 10:51:14.799 | 62.128초 |
| 2차 계정 복사 | 12:24:49.763 | 12:25:51.722 | 61.959초 |
| 합계 | - | - | **124.087초** |

- 시간은 RDS Started/Finished copy 이벤트 기준이다.
- 합계는 두 복사 구간의 소요 시간을 더한 값이다.
- IAM/KMS 권한 설정 및 수동 대기 시간은 합계에 포함하지 않는다.

#### 검증 결과

- 공유 스냅샷을 2차 계정에서 조회 성공
- 2차 계정 복사본 상태: available
- 진행률: 100%
- 암호화: 활성화
- 대상 KMS: 2차 계정의 `alias/aws/rds` (AWS 관리형 키)
  - 해당 키는 고객이 직접 삭제하거나 키 정책을 변경할 수 없으며, 리허설 종료 후 정리 대상이 아니다.
- 2차 계정 복사본의 직접 원본 (`SourceDBSnapshotIdentifier`):
  `arn:aws:rds:ap-northeast-2:883218392320:snapshot:corebank-share-20261021`
- 최초 원본: `corebank-after-ph60b-20261010`
- 복제 관계: `corebank-import-20261021` ← `corebank-share-20261021` ← `corebank-after-ph60b-20261010`
- 2026-10-21 기준 실제 RDS 복원 및 데이터 검증은 미수행 상태였다.
- 2026-10-27 신규 AWS 계정에서 실제 RDS 복원 및 주요 데이터 조회 검증을 수행했다. 결과는 §6 참조.

#### 후속 작업

- 10/27 Terraform apply 및 RDS 복원·DB 검증 완료
- 실제 이전에 사용할 최신 스냅샷과 이후 변경분의 동기화 절차 확정
- 복원 후 DB 연결 및 데이터 정합성 검증

### 스냅샷 사전 검증

2차 계정으로 복사한 스냅샷에 대해 다음을 확인한다.

```bash
SNAPSHOT_ID="<실제 스냅샷 ID>"

aws rds describe-db-snapshots \
  --db-snapshot-identifier "$SNAPSHOT_ID" \
  --region ap-northeast-2 \
  --query "DBSnapshots[0].{Status:Status,AllocatedStorage:AllocatedStorage,Engine:Engine,EngineVersion:EngineVersion,Encrypted:Encrypted,KmsKeyId:KmsKeyId}" \
  --output table
```

검증 기준:

- [x] Status = available
- [x] AllocatedStorage <= 20GiB
- [x] Engine = mysql
- [x] EngineVersion = Terraform에 설정된 버전
- [x] Encrypted = true
- [x] KmsKeyId = 2차 계정에서 복사에 사용한 KMS 키
- [x] 계정 간 복사 단계별 소요 시간 기록
- [ ] 10/28 최종 컷오버에 사용할 최신 스냅샷 생성 시점 확정
- [ ] 스냅샷 이후 거래에 대한 최종 동기화 절차 확정

Terraform plan 성공만으로 스냅샷 복원 가능 여부가 보장되지는 않는다.

스냅샷 복원 이후에도 Terraform 실행 시 최초 복원에 사용한 동일한 snapshot_identifier를 전달한다.

## 5. Terraform 검증

프로젝트의 terraform/main 디렉터리에서 실행한다.

    terraform init
    terraform fmt -check -recursive
    terraform validate
    terraform plan -var="snapshot_identifier=<스냅샷 ID>"

실제 apply는 P5와 일정 및 상태를 확인한 후 진행한다.

## 6. RDS 복원 및 검증

### 2026-10-27 신규 AWS RDS 복원 리허설 결과

#### 6.1. 목적 및 검증 범위

2026-10-21 계정 간 암호화 스냅샷 복제 리허설에서 생성한
`corebank-import-20261021`을 사용해 신규 AWS 계정에 RDS를 실제 복원했다.

이번 리허설에서는 다음 항목을 확인했다.

- Terraform을 통한 신규 VPC 및 RDS 생성 가능 여부
- 스냅샷 기반 RDS 복원과 Multi-AZ 전환
- RDS 자동 백업 및 파라미터 적용 상태
- 신규 VPC 내부에서 MySQL 접속 가능 여부
- 복원된 테이블과 주요 업무 데이터 조회
- Flyway 마이그레이션 이력
- binlog 및 GTID 관련 설정

애플리케이션은 실행하지 않았으며, Flyway 마이그레이션도 수행하지 않았다.

#### 6.2. 복원 환경

| 항목 | 값 |
|---|---|
| 작업일 | 2026-10-27 |
| AWS 계정 | 2차 계정 (`853900964665`) |
| 리전 | `ap-northeast-2` |
| 복원 스냅샷 | `corebank-import-20261021` |
| 원본 데이터 기준 | 2026-10-10 스냅샷 |
| RDS 식별자 | `corebank-rds` |
| 엔진 | MySQL 8.4.10 |
| 인스턴스 | `db.t3.small` |
| 스토리지 | gp3 20GiB |
| Multi-AZ | 활성화 |
| 자동 백업 보존 기간 | 7일 |
| DB 파라미터 그룹 | `corebank-mysql84-params` |
| 외부 접근 | 비활성화 |

신규 RDS는 데이터 서브넷에 배치했으며,
RDS 보안 그룹은 WAS 보안 그룹에서 오는 TCP 3306 연결만 허용한다.

#### 6.3. Terraform 적용 결과

최초 Terraform plan:

```text
Plan: 31 to add, 0 to change, 0 to destroy.
```

- 네트워크 리소스 28개
- RDS 관련 리소스 3개

Terraform 적용 중 오류와 실행 중단이 발생했으나,
실제 AWS 리소스 상태와 Terraform state를 확인한 뒤 재진행했다.

최종적으로 누락된 App 서브넷 NAT 라우트 2개를 생성하고,
RDS 자동 백업 보존 기간을 7일로 적용했다.

최종 검증 명령:

```bash
cd terraform/main

terraform plan \
  -var="snapshot_identifier=corebank-import-20261021"
```

결과:

```text
No changes. Your infrastructure matches the configuration.
```

**판정: `snapshot_identifier`를 제외한 Terraform 관리 속성의 drift 없음.**

`snapshot_identifier`는 `ignore_changes` 대상이므로, 이 plan 결과만으로 실제 복원 원본 스냅샷의 일치를 확인할 수 없다.
최종 컷오버 전 RDS 복원 요청의 `DBSnapshotIdentifier` 또는 CloudTrail 기록으로 복원 원본을 별도 확인하고 결과를 기록한다.

#### 6.4. RDS 복원 및 Multi-AZ 전환

AWS RDS 이벤트 기준 시각은 다음과 같다.

| 시각 (KST) | 이벤트 |
|---|---|
| 15:42:07 | DB instance restarted |
| 15:42:20 | Restored from snapshot |
| 15:42:30 | CloudWatch Logs Export 활성화 |
| 15:43:32 | DB 파라미터 그룹 변경 |
| 15:44:33 | DB 파라미터 그룹 업데이트 완료 |
| 15:44:34 | Multi-AZ 전환 시작 |
| 15:51:17 | Multi-AZ 전환 완료 |

**Multi-AZ 전환 시간: 약 6분 43초**

최초 RDS 생성 요청 시각과 `available` 도달 시각을 정확히 기록하지 못했으므로,
전체 스냅샷 복원 소요 시간은 미측정으로 구분한다.

이벤트의 `Restored from snapshot` 시각을
전체 RDS 구성 완료 시각으로 간주하지 않는다.

최종 확인 상태:

| 항목 | 결과 |
|---|---|
| DBInstanceStatus | `available` |
| MultiAZ | `true` |
| DBParameterGroup | `in-sync` |
| BackupRetentionPeriod | `7` |
| Terraform plan | `No changes` |

#### 6.5. 신규 VPC 내부 MySQL 접속 검증

신규 RDS는 프라이빗 서브넷에 배치되어 외부 PC에서 직접 접속할 수 없다.

따라서 P5가 생성한 일회용 EC2를 통해 MySQL 접속을 검증했다.

| 항목 | 값 |
|---|---|
| 임시 EC2 이름 | `corebank-rehearsal-tmp` |
| 인스턴스 ID | `i-0854cab2d5277e152` |
| OS | Ubuntu 24.04 LTS |
| 인스턴스 타입 | `t3.micro` |
| 사설 IP | `10.30.10.66` |
| 보안 그룹 | `corebank-sg-was` |
| 접속 방식 | AWS 콘솔 Session Manager |
| MySQL 클라이언트 | 8.0.46 |
| RDS MySQL 서버 | 8.4.10 |

검증 절차:

1. AWS 콘솔의 Session Manager로 임시 EC2에 접속
2. Ubuntu 패키지 저장소 URL을 HTTPS로 변경
3. MySQL 클라이언트 설치
4. RDS CA 인증서 번들 다운로드
5. TLS 암호화 연결을 사용해 신규 RDS 접속
6. MySQL 버전, 데이터베이스, 테이블 및 데이터 조회

TLS 확인:

```sql
SHOW SESSION STATUS LIKE 'Ssl_cipher';
```

결과:

```text
TLS_AES_256_GCM_SHA384
```

**판정: 신규 VPC의 WAS 보안 그룹을 사용하는 EC2에서 RDS MySQL 접속 성공. TLS 암호화 연결 확인.**

`Ssl_cipher` 결과로 TLS 암호화 연결을 확인했다.
접속 명령의 `--ssl-ca` 및 `--ssl-mode` 옵션 기록이 없어, 인증서 검증 수행 여부는 이 기록만으로 확인할 수 없다.

#### 6.6. 데이터베이스 및 테이블 검증

데이터베이스 목록:

```text
information_schema
minicore
mysql
performance_schema
sys
```

애플리케이션 DB: `minicore`

`information_schema.TABLES` 기준 일반 테이블 수: **35개**

주요 업무 테이블 존재 여부와 데이터 건수를 확인했다.

```sql
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

결과:

| 테이블 | 건수 |
|---|---:|
| `customer` | 60,003 |
| `account` | 180,011 |
| `transfer` | 3,156,174 |
| `ledger_entry` | 6,539,857 |
| `gl_journal_entry` | 6,470,002 |
| `gl_voucher` | 3,235,001 |
| `auto_transfer` | 15,000 |
| `audit_log` | 4,644 |
| **합계** | **19,660,692** |

- 조회 시간: 24.27초
- 조회 범위: 주요 업무 테이블 8개
- 데이터 변경 쿼리 실행 없음

24.27초는 해당 `COUNT(*)` 조회의 실행 시간이며,
전체 스냅샷 복원 시간이나 일반 서비스 쿼리 성능 수치가 아니다.

또한 원본 RDS의 동일 시점 건수와 직접 비교하지 않았으므로,
이 결과만으로 전체 데이터 정합성을 보장하지 않는다.

#### 6.7. Flyway 이력 검증

`flyway_schema_history` 테이블 존재 확인.

```sql
SELECT
    installed_rank,
    version,
    description,
    success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

확인된 마지막 이력:

| installed_rank | version | description | success |
|---|---|---|---|
| 54 | NULL | seed master data | 1 |
| 53 | 202610061901 | insert process result status timeout code | 1 |
| 52 | 202610061900 | alter transfer status comment timeout | 1 |
| 51 | 202610051913 | add transfer correction chain | 1 |
| 50 | 202610051500 | create gl voucher sequence | 1 |

실패 이력 확인:

```sql
SELECT COUNT(*) AS failed_migrations
FROM flyway_schema_history
WHERE success = 0;
```

결과:

```text
failed_migrations = 0
```

**판정: 스냅샷에 저장된 Flyway 이력에서 실패 기록 없음.**

단, 애플리케이션을 실행하지 않았으므로
현재 코드 기준의 Flyway `validate` 및 `migrate` 성공 여부는 검증하지 않았다.

#### 6.8. MySQL 파라미터 검증

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

결과:

| 파라미터 | 적용값 |
|---|---|
| `log_bin` | ON |
| `binlog_format` | ROW |
| `gtid_mode` | OFF_PERMISSIVE |
| `enforce_gtid_consistency` | ON |
| `time_zone` | Asia/Seoul |
| `require_secure_transport` | ON |

binlog가 활성화되어 있고 ROW 형식이 적용된 것을 확인했다.

GTID는 `OFF_PERMISSIVE` 상태이므로,
온프레미스 MySQL과의 GTID 기반 복제 성공을 의미하지 않는다.

GTID 전환 및 실제 복제 연결은 후속 PH-70-② 작업에서 검증한다.

#### 6.9. Terraform 적용 중 발생한 문제와 조치

| 문제 | 원인 | 조치 |
|---|---|---|
| EIP 처리 중 권한 오류 | `ec2:DescribeAddressesAttribute` 권한 부족 | IAM 권한 보완 |
| 보안 그룹 생성 실패 | `description`의 한글이 EC2 API 제약에 위배 | ASCII 문자열로 변경 (#763) |
| RDS 및 NAT Gateway 재생성 계획 발생 | Terraform 실행 중 Ctrl+C로 중단되어 리소스가 tainted 상태로 기록 | AWS 실제 상태 확인 후 `terraform untaint` |
| NAT 라우트 누락 | 이전 apply 중단으로 생성되지 않음 | App NAT 라우트 2개 생성 |
| 자동 백업 보존 기간 미적용 | RDS에서 7일 변경이 대기 상태 | 즉시 적용 후 실제 값 7일 확인 |

중단 이후 RDS와 NAT Gateway가 실제 AWS에서 생성되었는지 먼저 확인한 뒤
`untaint`를 수행했다.

기존 RDS와 NAT Gateway를 불필요하게 재생성하지 않고,
최종 Terraform plan에서 `No changes`를 확인했다.

#### 6.10. 임시 리소스 정리

데이터 검증 종료 후 리허설용 임시 리소스를 정리했다.

| 리소스 | 결과 |
|---|---|
| 임시 EC2 `corebank-rehearsal-tmp` | 삭제 완료 |
| IAM 역할 `corebank-rehearsal-tmp-ssm` | 삭제 완료 |
| IAM 인스턴스 프로파일 | 별도 삭제 여부 미확인 |
| P2 사용자 임시 SSM 접근 정책 | 별도 삭제 여부 미확인 |

신규 RDS, VPC, NAT Gateway, 서브넷 및 보안 그룹은 유지한다.

공유 Terraform state는 P5와 함께 사용하므로,
리허설 RDS의 교체나 인프라 삭제는 별도 합의 후 진행한다.

#### 6.11. 최종 판정 및 미검증 범위

**판정: 2026-10-27 RDS 스냅샷 복원 및 주요 데이터 조회 리허설 성공**

확인 완료:

- [x] Terraform을 통한 신규 네트워크 및 RDS 생성
- [x] 스냅샷 기반 RDS 복원
- [x] Multi-AZ 적용
- [x] RDS `available` 확인
- [x] DB 파라미터 그룹 `in-sync` 확인
- [x] 자동 백업 보존 기간 7일 확인
- [x] Terraform `No changes` 확인
- [x] 신규 VPC 내부 MySQL 접속
- [x] TLS 암호화 연결
- [x] `minicore` 데이터베이스 및 35개 테이블 확인
- [x] 주요 8개 테이블 데이터 조회
- [x] Flyway 실패 이력 0건 확인
- [x] binlog·GTID·시간대·TLS 관련 파라미터 확인
- [x] 임시 EC2 및 IAM 역할 삭제

미검증:

- [ ] 원본과 복원본의 전체 데이터 건수 및 체크섬 대조
- [ ] 현재 애플리케이션 코드 기준 Flyway validate/migrate
- [ ] 애플리케이션 기동 및 API 정상 동작
- [ ] 온프레미스 MySQL binlog 복제
- [ ] 실제 컷오버 시점의 전체 소요 시간
- [ ] 임시 인스턴스 프로파일 및 사용자 SSM 정책 정리 상태

이번 리허설은 2026-10-10 데이터 기준 스냅샷으로 수행했다.

2026-10-28 최종 컷오버에서는 기존 RDS 쓰기를 중단하고
최신 스냅샷을 생성·복사한 뒤 신규 RDS에 다시 복원해야 한다.

최종 스냅샷 복원 후에는 DB 연결·데이터·백업 설정을 다시 확인하고,
P5의 컴퓨트 apply 및 애플리케이션 연동 검증을 진행한다.

### 후속 작업 시 주의사항

- `snapshot_identifier`만 변경해도 현재 RDS가 자동 교체되지는 않는다.
  (`ignore_changes = [snapshot_identifier]`)
- 현재 RDS에는 `deletion_protection = true`가 적용되어 있다.
- 리허설 RDS 삭제 시 고정된 `corebank-rds-final` 스냅샷 이름 충돌을 방지해야 한다.
- RDS 삭제·교체 전 Terraform state 및 P5의 동시 작업 여부를 확인한다.
- 신규 RDS 복원 후 실제 엔드포인트 주소를 다시 확인한다.
- 기존 계정 RDS와 최종 컷오버·롤백용 스냅샷은 임의로 삭제하지 않는다.

## 7. 컷오버 및 복구

- 기존 앱의 쓰기 중단 시점 확정
- 최종 스냅샷 또는 증분 동기화 수행
- 데이터 정합성 확인 후 신규 RDS로 연결 전환
- 실패 시 기존 RDS 유지(Plan B)
- 최종 컷오버 일정에 맞춰 온프레미스 MySQL 복제 구성
- 온프레미스 MySQL 복제 연결도 TLS를 사용하도록 설정하고 검증한다.

### GTID 복제 준비

- 최신 스냅샷으로 RDS를 복원한 뒤 실제 GTID 설정값을 다시 확인한다.
- 10/27 리허설에서 확인한 `OFF_PERMISSIVE`는 GTID 전환 준비 상태다.
- 복제 시작 전에 `ON_PERMISSIVE`로 전환하고 필요한 재부팅 및 트랜잭션 상태를 확인한다.
- 익명 트랜잭션이 모두 처리된 것을 확인한 뒤 `ON` 전환 여부를 결정한다.
- GTID 단계 변경은 Terraform 설정에 반영하고 P5와 재부팅 시간을 협의한다.
- 온프레미스 복제 연결의 TLS 설정과 GTID 자동 위치 지정 동작을 검증한다.

## 8. 주의사항

- 기존 운영 RDS 및 파라미터 그룹은 변경하지 않는다.
- 계획 검증용 가짜 스냅샷 ID로 apply하지 않는다.
- Terraform 상태 파일과 인증 정보를 커밋하지 않는다.
- apply 및 destroy는 P5와 협의한다.
- RDS 삭제 보호와 최종 스냅샷 보관 비용을 고려한다.

### RDS 삭제 및 정리 절차

공유 Terraform 상태를 사용하므로 P5와 협의 없이 destroy하지 않는다.

1. 기존 RDS 데이터 및 백업 보존 필요 여부를 확인한다.
2. `deletion_protection = false`로 변경하고 plan/apply한다.
3. `final_snapshot_identifier`가 기존 스냅샷 이름과
   충돌하지 않는지 확인한다.
4. 승인된 RDS 삭제 작업을 수행하고 최종 스냅샷 생성 여부를 확인한다.
5. 보존 기간이 지난 스냅샷을 정리한다.
   스냅샷 암호화에 사용한 고객 관리형 KMS 키는 더 이상 참조하는 스냅샷이나 리소스가 없는지 확인한 뒤 별도 정리한다. 
   AWS 관리형 `alias/aws/rds`는 직접 삭제할 수 없으므로 정리 대상에서 제외한다.

최종 스냅샷은 RDS 삭제 후에도 저장 비용이 발생할 수 있다.
