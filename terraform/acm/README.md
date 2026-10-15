# ACM 인증서 관리 (PH-58)

## 1. 개요

2차 코어뱅킹 인프라의 ALB HTTPS 리스너에서 사용할 ACM 인증서를 Terraform으로 관리한다.

PH-51의 ALB 구축에 앞서 인증서 ARN을 P5에게 전달해야 하므로, 전체 인프라 구축 일정(10/26)과 별개로 ACM 인증서를 선행 발급했다.

- 담당: P2
- 관련 이슈: #618
- 인증서 도메인: `api.corebank.cloud`
- AWS 계정: `853900964665` (2차 프로젝트)
- 리전: `ap-northeast-2` (서울)
- 검증 방식: DNS
- 인증서 상태: `ISSUED` (2026-10-14 확인)

## 2. Terraform 구성

```text
terraform/acm/
├── README.md
├── backend.tf
├── providers.tf
├── main.tf
├── outputs.tf
└── .terraform.lock.hcl
```

### 별도 Terraform 루트를 사용하는 이유

기존 `terraform/main/`에는 네트워크 등 전체 인프라를 구성하는 모듈이 연결되어 있다.

ACM 인증서를 먼저 발급하면서 아직 구축하지 않을 인프라가 함께 생성되는 것을 방지하기 위해 별도의 Terraform 루트와 State를 사용한다.

| 구분 | State 경로 |
|---|---|
| 전체 인프라 (`terraform/main/`) | `main/terraform.tfstate` |
| ACM (`terraform/acm/`) | `acm/terraform.tfstate` |

두 State는 동일한 S3 버킷에 저장되지만 서로 독립적으로 관리된다.

- S3 버킷: `corebank-terraform-state-853900964665`
- 리전: `ap-northeast-2`
- State 잠금: S3 Lockfile (`use_lockfile = true`)

**주의:** ACM 작업을 위해 `terraform/main/`에서 `terraform apply`를 실행하지 않는다.

## 3. AWS 인증

Terraform 실행 전 2차 AWS 계정의 IAM 사용자로 인증해야 한다.

Git Bash 기준:

```bash
export AWS_PROFILE=corebank-p2-terraform-cli

aws sts get-caller-identity
```

출력된 `Account` 값이 `853900964665`인지 확인한다.

AWS CLI 프로필 이름은 개인 로컬 설정이므로, 다른 팀원은 자신의 프로필을 사용한다.

액세스 키와 비밀 키는 저장소에 커밋하지 않는다.

## 4. Terraform 실행 방법

저장소 루트에서 ACM 디렉터리로 이동한다.

```bash
cd terraform/acm
```

### 초기화

```bash
terraform init
```

AWS Provider를 설치하고 S3 Backend에 연결한다.

### 코드 검증

```bash
terraform fmt -check
terraform validate
```

### 변경 사항 확인

```bash
terraform plan
```

실제 AWS 리소스와 Terraform 구성이 일치하면 다음 메시지가 출력된다.

```text
No changes. Your infrastructure matches the configuration.
```

### 변경 사항 적용

실제 변경이 필요한 경우에만 계획 파일을 생성한다.

```bash
terraform plan -out=acm.tfplan
```

계획에 의도하지 않은 리소스 변경이나 삭제가 없는지 확인한 후 적용한다.

```bash
terraform apply acm.tfplan
```

**주의 사항**

- 적용 전 AWS 계정 ID를 확인한다.
- ACM 외 리소스가 생성·변경·삭제되는 계획이면 적용하지 않는다.
- 기존 인증서는 이미 발급되어 있으므로 재발급을 목적으로 `apply`를 반복하지 않는다.
- `terraform destroy`를 실행하지 않는다.
- `.tfplan`, `.tfstate`, `.terraform/`은 Git에 커밋하지 않는다.
- `.terraform.lock.hcl`은 Provider 버전 재현을 위해 커밋한다.

## 5. 발급된 인증서

| 항목 | 값 |
|---|---|
| 도메인 | `api.corebank.cloud` |
| 리전 | `ap-northeast-2` |
| 검증 방식 | DNS |
| 상태 | `ISSUED` |
| 발급 확인일 | 2026-10-14 |

### 인증서 ARN

```text
arn:aws:acm:ap-northeast-2:853900964665:certificate/8e20bc8f-2f23-4b3e-8a02-42e0fe984f7b
```

Terraform에서도 확인할 수 있다.

```bash
terraform output alb_certificate_arn
```

이 ARN은 PH-51에서 P5가 ALB HTTPS 리스너를 구성할 때 사용한다.

## 6. DNS 검증

`corebank.cloud`의 권한 DNS는 가비아에서 관리한다.

ACM 인증서 발급을 위해 가비아에 다음 CNAME 레코드를 추가했다.

### ACM 검증용 CNAME

**레코드 타입**

```text
CNAME
```

**호스트 (가비아 입력값)**

```text
_4a8ae8d0c9ee10df1586170c95d9df20.api
```

**전체 도메인**

```text
_4a8ae8d0c9ee10df1586170c95d9df20.api.corebank.cloud
```

**값 (CNAME 대상)**

```text
_0c07389a6c7c09d604883127ac425a3f.wzccmgtwzk.acm-validations.aws.
```

Terraform에서도 검증 레코드를 확인할 수 있다.

```bash
terraform output alb_dns_validation_records
```

### DNS 검증 확인

```bash
nslookup -type=CNAME _4a8ae8d0c9ee10df1586170c95d9df20.api.corebank.cloud 8.8.8.8
```

2026-10-14 기준, Google DNS에서 ACM 검증용 CNAME이 정상 조회되는 것을 확인했다.

### DNS 관리 주의 사항

- 검증용 CNAME은 ACM 인증서 자동 갱신을 위해 유지한다.
- 기존 `api.corebank.cloud`의 ALB 연결 CNAME은 변경하지 않는다.
- `corebank.cloud`의 네임서버(NS)는 변경하지 않는다.
- 가비아 DNS 레코드는 현재 Terraform 관리 대상이 아니다.
- 운영 DNS 전환은 별도 일정과 절차에 따라 진행한다.

## 7. 인증서 상태 확인

AWS CLI에서 다음 명령으로 확인할 수 있다.

```bash
aws acm describe-certificate \
  --certificate-arn arn:aws:acm:ap-northeast-2:853900964665:certificate/8e20bc8f-2f23-4b3e-8a02-42e0fe984f7b \
  --region ap-northeast-2 \
  --query "Certificate.Status" \
  --output text
```

정상 발급 상태:

```text
ISSUED
```

2026-10-14에 `ISSUED` 상태를 확인했다.

`aws_acm_certificate_validation` 리소스를 통해 DNS 검증 완료를 기다리므로, Terraform 적용이 정상 완료되면 인증서는 `ISSUED` 상태다.

### 인증서 검증 및 교체 정책

- `aws_acm_certificate_validation`으로 인증서 발급 완료를 확인한다.
- 검증용 DNS CNAME은 가비아에서 수동 관리한다.
- 기존 인증서의 의도치 않은 삭제를 방지하기 위해 `prevent_destroy = true`를 적용한다.
- ACM과 ALB는 별도 Terraform State에서 관리하므로, 인증서 교체는 자동으로 이루어지지 않는다.

### 인증서 교체 절차

1. 신규 ACM 인증서를 별도 리소스로 발급한다.
2. 필요한 DNS CNAME을 등록하고 ISSUED 상태를 확인한다.
3. P5와 협의하여 ALB HTTPS 리스너의 인증서 ARN을 변경한다.
4. HTTPS 연결을 검증한다.
5. 기존 인증서가 더 이상 사용되지 않는 것을 확인한 후 제거한다.

기존 인증서를 사용하는 동안에는
`prevent_destroy`를 해제하거나 인증서를 임의로 삭제하지 않는다.

## 8. 다른 인프라 작업과의 관계

### PH-51: ALB HTTPS 리스너

P5는 이번에 발급된 ACM 인증서 ARN을 사용하여 ALB HTTPS 리스너를 구성한다.

- 인증서 발급: P2
- ALB 및 HTTPS 리스너 구성: P5
- 전체 인프라 적용 예정일: 2026-10-26

ACM 인증서가 발급되었다고 해서 ALB HTTPS 리스너가 생성된 것은 아니다.

### CloudFront 인증서

CloudFront에서 사용자 지정 도메인을 사용하려면 `us-east-1` 리전의 ACM 인증서가 필요하다.

현재 발급된 인증서는 서울 리전의 ALB용 인증서다.

CloudFront용 도메인은 미확정 상태이므로, 도메인 확정 후 별도로 구성한다.

## 9. 검증 결과

2026-10-14 기준:

| 검증 항목 | 결과 |
|---|---|
| `terraform init` | 성공 |
| `terraform fmt -check` | 성공 |
| `terraform validate` | 성공 |
| 최초 `terraform plan` | `1 to add, 0 to change, 0 to destroy` |
| `terraform apply` | ACM 인증서 생성 완료 |
| 가비아 DNS 검증 CNAME 조회 | 성공 |
| ACM 인증서 상태 | `ISSUED` |
| 적용 후 `terraform plan` | `No changes` |

인증서 ARN 전달 및 PH-51 연동 결과는 후속 작업에서 확인한다.
