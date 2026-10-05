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
      compute/        자리만 (ALB·ASG, PH-51)
      data/           자리만 (RDS·ElastiCache, P2 담당)
```

## 지금 상태

- `network` 모듈: plan 통과(29개 리소스), **apply는 아직 안 함**
- 실제 apply는 S3 스프린트(10/19~23 리허설 → 10/26 본 apply)에 진행
- 지금은 코드만 보고 `modules/data`를 짜면 됨 — 실제 ID 값은 apply 후에 채워짐

## P2가 쓸 출력값

`modules/data`에서 이렇게 참조하면 됨:

| 출력값 | 용도 |
|---|---|
| `module.network.vpc_id` | VPC 참조 |
| `module.network.data_subnet_ids` | RDS 서브넷 그룹 |
| `module.network.rds_sg_id` | RDS에 붙일 보안그룹 |
| `module.network.cache_sg_id` | ElastiCache에 붙일 보안그룹 |

**주의**: `alb`·`was`·`rds`·`cache` 네 보안그룹의 인바운드·아웃바운드 규칙은 모두 `modules/network/security_groups.tf`에서만 관리한다. 다른 모듈(`modules/data` 등)에서 `aws_security_group_rule`로 같은 보안그룹에 규칙을 추가하면 이 파일의 인라인 규칙과 충돌해 apply가 깨진다. RDS·ElastiCache에 추가 포트가 필요하면 이 파일을 고쳐달라고 요청한다.

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
