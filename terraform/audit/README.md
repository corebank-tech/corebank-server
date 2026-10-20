# PH-72-① 감사 로그 Object Lock 인프라

## 1. 목적 및 범위

P1 PH-82(WORM 감사 저장소)가 사용할 S3 Object Lock 버킷과 감사 로그 적재용 IAM 정책을 구성한다.

PH-72 전체 작업 중 2026-10-21까지 선행 적용해야 하는 범위이며, 사람·CI·AI IAM 분리, Secrets Manager, CloudTrail 등은 PH-51 이후 진행한다.

관련 이슈: #660

## 2. 아키텍처 및 관리 경계

```text
AWS WAS (P5 관리)
    |
    | IAM 역할에 corebank-audit-writer 정책 연결
    v
S3 Object Lock 버킷 (P2 관리)
    |
    +-- audit/                  감사 로그 적재
    |   +-- [PH-82 감사 로그 하위 경로]
    |   +-- [PH-82 개인정보 접근로그 하위 경로]
    |
    +-- audit/lock-test/        Object Lock 삭제 거부 검증
```

- AWS WAS에서 S3로 직접 적재한다(P1 협의 완료).
- 모든 감사 객체는 `audit/` 접두사를 사용한다.
- 감사 로그와 개인정보 접근로그의 세부 하위 경로는 P1 PH-82 구현에서 확정한다.
- 실제 IAM 역할 생성·관리와 정책 연결은 P5 PH-51 범위다.
- 객체 적재 및 삭제·변조 거부 검증은 P1 PH-82 범위다.

## 3. AWS 리소스

적용 계정: `853900964665`  
리전: `ap-northeast-2`

| 리소스 | 값 |
|---|---|
| S3 버킷 | `corebank-audit-worm-853900964665` |
| 버킷 ARN | `arn:aws:s3:::corebank-audit-worm-853900964665` |
| IAM 관리형 정책 | `corebank-audit-writer` |
| IAM 정책 ARN | `arn:aws:iam::853900964665:policy/corebank-audit-writer` |
| Object Lock | Enabled |
| 보존 모드 | GOVERNANCE |
| 기본 보존기간 | 1일 |
| Versioning | Enabled |
| 퍼블릭 접근 차단 | 4개 항목 모두 활성화 |
| 서버 측 암호화 | SSE-S3 (`AES256`) |
| Terraform 버킷 설정 | `force_destroy = true` |

### IAM 정책

`corebank-audit-writer`는 실제 감사 로그를 적재하는 애플리케이션용 정책이다.

**운영 권한**
- `audit/*`: `s3:PutObject`, `s3:GetObject`
- 버킷 조회: `s3:ListBucket`

**PH-82 검증용 임시 권한**
- 대상 경로: `audit/lock-test/*`
- `s3:DeleteObjectVersion`: IAM 권한 부족이 아닌 Object Lock에 의한 삭제 거부 확인
- `s3:GetObjectVersion`: 덮어쓰기 이후 원본 버전의 내용이 유지되는지 확인
- `s3:GetObjectRetention`: GOVERNANCE 모드 및 보존 만료일 확인
- `s3:ListBucketVersions`는 사용하지 않음. `versionId`는 업로드 응답에서 확보

**권한 제한 및 제거 계획**
- `s3:BypassGovernanceRetention`, `s3:DeleteObject`는 부여하지 않음.
- 일반 감사 로그 경로에는 `s3:DeleteObjectVersion`을 부여하지 않음.
- 검증용 삭제·조회 권한은 `audit/lock-test/*`에만 허용함.
- P1 PH-82 검증 완료 후 검증용 IAM Statement 전체를 제거할 예정임.

**주의:** GOVERNANCE 모드에서는 보존 우회 권한이 있는 주체가 명시적으로 우회할 수 있다. 일반 적재 역할에는 해당 권한을 부여하지 않는다.

## 4. Terraform 상태 관리

감사 버킷은 10/21 선적용이 필요하므로 `terraform/main`과 별도 루트 모듈에서 관리한다.

```text
terraform/
├── main/               VPC·EC2·RDS 등 (P5/P2)
└── audit/              PH-72-① 감사 버킷·IAM
    ├── versions.tf
    ├── backend.tf
    ├── main.tf
    ├── iam.tf
    ├── outputs.tf
    └── README.md
```

원격 상태:

- S3 백엔드: `corebank-terraform-state-853900964665`
- State key: `audit/terraform.tfstate`
- Lock: `audit/terraform.tfstate.tflock`

`terraform/main`과 별도 state를 사용하므로 감사 버킷을 먼저 적용해도 VPC·NAT·EC2·RDS 등의 리소스가 함께 생성되지 않는다.

상태 파일 및 잠금 파일의 접근 권한은 Terraform 실행 IAM 사용자에만 부여한다.

## 5. 실행 방법

### 사전 확인

```bash
cd terraform/audit
aws sts get-caller-identity
```

반드시 AWS 계정이 `853900964665`인지 확인한다.

### Terraform 검증

```bash
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan
```

변경사항이 실제로 필요한 경우 계획을 검토한 후에만 실행한다.

```bash
terraform apply
```

기존 버킷을 삭제·교체하는 계획이 표시된다면 원인을 확인하기 전에는 승인하지 않는다.

## 6. 적용 및 검증 결과

검증일: 2026-10-20  
실행 주체: `corebank-p2-terraform`

### Terraform

- `terraform validate`: 성공
- 최초 생성 대상: 6개
- IAM 정책 변경 적용: `0 added, 1 changed, 0 destroyed`
- 최종 `terraform plan` (2026-10-20): `No changes`

### AWS CLI 검증

| 항목 | 실제 검증 결과 |
|---|---|
| Object Lock 활성화 | Enabled |
| Object Lock 기본 모드 | GOVERNANCE |
| Object Lock 기본 보존기간 | 1일 |
| Versioning | Enabled |
| 퍼블릭 접근 차단 | 4개 모두 true |
| 서버 측 암호화 | AES256 |
| IAM 정책 존재 | `corebank-audit-writer` 생성 확인 |
| IAM 정책 연결 | 최초 확인 시 연결된 사용자·그룹·역할 없음 |

검증 명령어:

```bash
aws s3api get-object-lock-configuration \
  --bucket corebank-audit-worm-853900964665

aws s3api get-bucket-versioning \
  --bucket corebank-audit-worm-853900964665

aws s3api get-public-access-block \
  --bucket corebank-audit-worm-853900964665

aws s3api get-bucket-encryption \
  --bucket corebank-audit-worm-853900964665

aws iam list-entities-for-policy \
  --policy-arn arn:aws:iam::853900964665:policy/corebank-audit-writer
```

### 적용 중 확인된 사항

- 최초 적용 과정에서 IAM 권한 부족으로 일부 실행이 중단되었다.
- 권한 보완 후 재적용 과정에서 감사 버킷 삭제 및 동일 이름 재생성이 1회 발생했다.
- 이후 리소스 설정 검증과 Terraform 상태 일치 확인에 성공했다.
- IAM 정책은 P1 요청에 따라 검증용 `DeleteObjectVersion` 권한을 추가하여 재적용했다.

## 7. 팀 연동 및 남은 검증

### P1 — PH-82

**연동 방식 확정**

- 적재 경로: `audit/`
- 감사 로그와 개인정보 접근로그는 하위 경로로 분리하며, 세부 경로는 PH-82에서 확정한다.
- AWS WAS에서 S3로 직접 업로드한다.
- 별도 검증용 IAM 주체 및 사전 검증은 진행하지 않는다.

**Object Lock 검증 권한**

`audit/lock-test/*`에만 다음 권한을 임시로 부여한다.

- `s3:DeleteObjectVersion`: IAM 권한 부족이 아닌 Object Lock에 의한 삭제 거부 확인
- `s3:GetObjectVersion`: 동일 키에 덮어쓴 뒤 기존 객체 버전의 내용이 유지되는지 확인
- `s3:GetObjectRetention`: GOVERNANCE 모드 및 보존 만료일 확인

`versionId`는 업로드 응답에서 확보하므로 `s3:ListBucketVersions`는 필요하지 않다.

`s3:BypassGovernanceRetention`은 부여하지 않는다.

**구현 및 검증 일정**

- **10/28까지:** 해시 컬럼·검증 배치·목 버킷 적재 어댑터를 `dev`에 머지
- **10/30부터:** 실제 AWS WAS IAM 역할로 S3 객체 적재 및 Object Lock 통합 검증

**P1 검증 범위**

- 실제 감사 객체 적재 성공 확인
- 특정 `versionId`의 삭제 거부 확인
- 동일 키 재업로드 후 원본 버전의 내용 유지 확인
- Object Lock 보존 모드 및 만료일 조회
- 감사 로그 무결성 검증

Object Lock은 동일 키에 새 버전을 업로드하는 것까지 차단하지 않는다.
따라서 기존 버전의 보존 여부와 내용 무결성을 검증한다.

### P5 — PH-51 / PH-55

**합의 완료**

- PH-51의 WAS 인스턴스 프로파일 역할에 `corebank-audit-writer` 정책을 연결한다.
- `terraform/audit/`의 독립 state(`audit/terraform.tfstate`) 운영에 동의했다.
- PH-55 destroy 절차에 audit 스택의 별도 destroy를 포함한다.

**예정 작업**

- **10/26:** PH-51 apply로 WAS IAM 역할 생성 후 `corebank-audit-writer` 정책 연결
- IAM 정책 연결 완료 후 P2에 결과 공유
- **PH-55:** audit 스택의 별도 destroy 절차 및 실행 권한 확인

### P2 — PH-72-① 후속

**완료**

- S3 Object Lock 버킷 및 적재용 IAM 정책 생성·적용
- GOVERNANCE 1일, Versioning, 퍼블릭 접근 차단, AES256 암호화 확인
- P1 요청에 따른 Object Lock 검증용 IAM 권한 반영
- `terraform/audit/` 독립 state 구성
- 최종 `terraform plan`: `No changes`

**남은 작업**

- 10/26 이후 P5의 WAS IAM 정책 연결 결과 확인
- P1의 PH-82 통합 검증 결과 확인
- **PH-82 검증 완료 후:** `audit/lock-test/*`의 검증 전용 IAM Statement 제거 및 Terraform 재적용
- 검증용 권한 제거 후 `terraform plan`으로 최종 상태 확인

사람·CI·AI IAM 분리, Secrets Manager, CloudTrail 등 PH-72의 나머지 작업은 별도로 진행한다.

## 8. Destroy 주의사항

S3 Object Lock은 보존기간 중인 객체 버전의 삭제를 제한한다.

`force_destroy = true`라도 보호 중인 객체 버전이 무조건 삭제되는 것은 아니다.

- P5가 destroy 수행 주체다.
- 마지막 객체 적재 이후 보존기간이 경과했는지 확인한다.
- 실제 객체 버전 및 삭제 마커 잔존 여부를 확인한다.
- 별도 `terraform/audit` state를 사용하므로 audit 스택의 destroy를 따로 수행해야 한다.
- 일반 WAS 적재 역할에는 `s3:BypassGovernanceRetention`을 부여하지 않는다.
- 해당 버킷의 마지막 적재 및 삭제 계획을 P1·P5와 사전에 합의한다.

관련 근거: `docs/phase2/README.md` D-08 · D-11 · §3-8

### Destroy 실행 주체 및 권한

- 실행 담당: P5 (PH-55)
- 실제 AWS IAM 실행 주체: PH-55에서 확정 및 기록
- 운영 WAS의 `corebank-audit-writer` 정책은 destroy에 사용하지 않는다.

**기본 원칙: 보존기간이 지난 뒤에만 destroy한다.**

1. P1의 감사 로그 적재를 중지한다.
2. 객체의 각 버전에 설정된 보존 만료일을 확인한다.
3. 모든 객체 버전의 보존기간이 지난 뒤 `terraform/audit`에서 destroy한다.
4. 삭제 마커 및 객체 버전의 잔존 여부를 확인한다.

P5의 destroy 실행 주체에는 S3 버킷·객체 버전 조회와 삭제에 필요한
권한(`s3:ListBucketVersions`, `s3:DeleteObject`,
`s3:DeleteObjectVersion`, `s3:DeleteBucket` 등)이 필요하다.
실제 부여 여부는 PH-55에서 검증한다.

현재 `s3:BypassGovernanceRetention`은 운영 WAS 적재 정책에 없으며,
P5의 destroy 실행 주체에 부여되었는지도 확인되지 않았다.

보존기간 중 강제 삭제가 필요한 예외 상황에서는 별도 승인된
destroy 주체에만 `s3:BypassGovernanceRetention`을 부여하는 방안을
P5와 협의한다. 일반 destroy에서는 사용하지 않는다.

`force_destroy = true`는 Object Lock의 보존 제한을 무시하는 설정이 아니다.
