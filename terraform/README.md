# CoreBank 인프라 Terraform 

## 시작하기 전에 (처음 실행하는 사람 전원)

1. 로컬에 설치
   ```bash
   brew tap hashicorp/tap
   brew install hashicorp/tap/terraform awscli
   ```
2. AWS 콘솔(IAM) → **본인 명의 IAM 사용자**를 각자 따로 만든다 (키 공유 금지)
   - 콘솔 액세스: 체크 안 함 (CLI 전용)
   - 권한: `AdministratorAccess`를 바로 주지 않는다 — 아래 4개만 직접 연결 (PR #535 리뷰 반영, 최소권한)
     - `AmazonVPCFullAccess`
     - `AmazonEC2FullAccess`
     - `AmazonS3FullAccess`
     - `IAMReadOnlyAccess`
   - 작업하다 `AccessDenied` 에러가 나면, 에러 메시지에 나온 서비스의 정책을 그때 추가로 붙인다 (미리 다 예측해서 넓히지 않는다)
   - 액세스 키 생성 → 사용 사례 "CLI" 선택 → CSV 다운로드
3. 로컬에 자격증명 등록
   ```bash
   aws configure
   # Access Key ID / Secret Access Key / region: ap-northeast-2
   aws sts get-caller-identity   # 본인 ARN 뜨면 성공
   ```

## 폴더 구조

```
terraform/
  bootstrap/        상태 저장용 S3 버킷 (최초 1회만 적용, 이미 완료)
  main/
    modules/
      network/       VPC·서브넷·NAT·라우팅·SG (P5, plan 완료)
      compute/        ALB·대상그룹·HTTPS 리스너 (P5, PH-51 ①). ASG·IAM은 아직 없음(#694·#695)
      data/           RDS MySQL 8.4 · DB 서브넷 그룹 · 파라미터 그룹 (P2, plan 완료)
```

## 지금 상태

- `network` 모듈: plan 통과(28개 리소스), **apply는 아직 안 함**
- `data` 모듈: RDS·DB 서브넷 그룹·파라미터 그룹 구현, plan 통과
- `compute` 모듈(ALB): 대상 그룹 헬스체크는 `/api/v1/actuator/health`(앱 context-path가 `/api/v1`). 인증서 ARN은 `terraform/acm` 상태에서 읽는다. plan 통과
- 통합 Terraform plan: 35 to add, 0 to change, 0 to destroy (ALB 추가 후)
- 실제 apply는 10/26, DNS 컷오버는 10/27 예정


## 데이터 계층 (P2, PH-70)

- RDS MySQL 8.4, Multi-AZ, gp3 20GiB
- DB 서브넷 그룹 및 파라미터 그룹 관리
- 기존 RDS 스냅샷 복원 방식
- 10/21 Terraform plan 완료, 10/26 apply 예정

설정 및 복원 절차: [PH-70 RDS Terraform](main/modules/data/README.md)


## P2가 쓸 출력값

`modules/data`에서 이렇게 참조하면 됨:

| 출력값 | 용도 |
|---|---|
| `module.network.vpc_id` | VPC 참조 |
| `module.network.data_subnet_ids` | RDS 서브넷 그룹 |
| `module.network.rds_sg_id` | RDS에 붙일 보안그룹 |

컴퓨트 apply 직후 P2에게 넘기는 값(루트 `terraform output`으로도 나온다):

| 출력값 | 용도 |
|---|---|
| `alb_arn` | WAF 연결 |
| `https_listener_arn` | 리스너 참조 |
| `alb_dns_name` | Route 53/가비아 DNS 전환 |
| `alb_zone_id` | Route 53 별칭 레코드 |
| `target_group_arn` | ASG(#694)가 서버를 붙일 대상 그룹 |

**주의**: `alb`·`was`·`rds` 세 보안그룹의 인바운드·아웃바운드 규칙은 모두 `modules/network/security_groups.tf`에서만 관리한다. 다른 모듈(`modules/data` 등)에서 `aws_security_group_rule`로 같은 보안그룹에 규칙을 추가하면 이 파일의 인라인 규칙과 충돌해 apply가 깨진다. RDS에 추가 포트가 필요하면 이 파일을 고쳐달라고 요청한다.

## bootstrap 상태 파일 보관

`terraform/bootstrap`은 S3 버킷을 만드는 코드 자체라, 이 코드의 상태 파일
(`terraform/bootstrap/terraform.tfstate`)은 S3가 아니라 **로컬에만** 있다(현재 P5 컴퓨터).
이 파일을 지우면 Terraform이 "이 버킷을 이미 만들었다"는 걸 잊어버려서, 다시 `apply`할 때
"이미 있는 이름인데 또 만들려 함" 충돌이 난다. 지우거나 옮기지 않는다.

## 실행 방법

```bash
cd terraform/main
terraform init
terraform plan
```

## 아직 미정인 것

- util 노드용 SG(8000) 규칙은 안 넣음 — D-25(하이브리드 인프라) 팀 확인(O-11) 끝나기 전까지 보류
