# PH-70 — RDS Terraform

## 1. 개요

- 담당: P2
- 목적: 1차 AWS 계정의 RDS 데이터를 2차 계정으로 이전하고,
  신규 3-Tier 환경에 RDS MySQL을 구성한다.
- 10/21: Terraform 구현 및 plan 완료
- 10/26: RDS 복원
- 10/27: 앱 연결 전환 및 온프레미스 복제 시작

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
- 실제 RDS 복원 및 데이터 정합성 검증은 미수행

#### 후속 작업

- 10/26 Terraform apply 및 RDS 복원
- 실제 이전에 사용할 최신 스냅샷과 이후 변경분의
  동기화 절차 확정
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
- [ ] 10/26 실제 컷오버에 사용할 스냅샷 생성 시점 확정
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

- RDS 상태 available 확인
- 네트워크 및 보안 그룹 확인
- MySQL 접속 테스트
- Flyway validate 확인
- 원장·분개 등 주요 테이블 건수 및 정합성 확인
- 파라미터 적용값 확인
- 단계별 소요 시간 기록

## 7. 컷오버 및 복구

- 기존 앱의 쓰기 중단 시점 확정
- 최종 스냅샷 또는 증분 동기화 수행
- 데이터 정합성 확인 후 신규 RDS로 연결 전환
- 실패 시 기존 RDS 유지(Plan B)
- 10/27 이후 온프레미스 MySQL 복제 구성
- 온프레미스 MySQL 복제 연결도 TLS를 사용하도록 설정하고 검증한다.

### GTID 복제 준비

- 10/26: RDS 복원 후 실제 GTID 설정값을 확인한다.
- 초기 `OFF_PERMISSIVE`는 GTID 전환 준비 상태다.
- 10/27 복제 시작 전에 `ON_PERMISSIVE`로 전환하고 필요한 재부팅 및 트랜잭션 상태를 확인한다.
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
