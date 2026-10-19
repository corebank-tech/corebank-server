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
4. 2차 계정에서 자체 KMS 키로 스냅샷 복사
5. 복사된 스냅샷의 상태와 엔진 버전 확인

※ KMS 리허설 결과, 소요 시간 및 실제 식별자는 검증 후 기록한다.

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

## 8. 주의사항

- 기존 운영 RDS 및 파라미터 그룹은 변경하지 않는다.
- 계획 검증용 가짜 스냅샷 ID로 apply하지 않는다.
- Terraform 상태 파일과 인증 정보를 커밋하지 않는다.
- apply 및 destroy는 P5와 협의한다.
- RDS 삭제 보호와 최종 스냅샷 보관 비용을 고려한다.
