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
      compute/        ALB·대상그룹·HTTPS 리스너, WAS 인스턴스 프로파일·SSM 역할, 시작 템플릿·ASG (P5, PH-51 ①②③)
      data/           RDS MySQL 8.4 · DB 서브넷 그룹 · 파라미터 그룹 (P2, plan 완료)
      vpn/            VPN 게이트웨이 · Customer Gateway · Site-to-Site VPN · 라우트 전파 (P5, PH-100-②)
```

## 지금 상태

- `network` 모듈: plan 통과(28개 리소스), **apply는 아직 안 함**
- `data` 모듈: RDS·DB 서브넷 그룹·파라미터 그룹 구현, plan 통과
- `compute` 모듈(ALB): 대상 그룹 헬스체크는 `/api/v1/actuator/health`(앱 context-path가 `/api/v1`). 인증서 ARN은 `terraform/acm` 상태에서 읽는다. plan 통과
- `vpn` 모듈: 터널 1개, 정적 라우팅 `192.168.0.0/24`. plan 통과, 온프레미스 VM 쪽 설정 절차는 [ph100_vpn_vm_runbook.md](../docs/phase2/ph100_vpn_vm_runbook.md)
- 통합 Terraform plan: 51 to add, 0 to change, 0 to destroy (ALB·IAM·ASG·VPN 포함)
- 일정: 10/27 P2 리허설 apply(리허설 스냅샷, `dev` 머지분 기준), 10/28 최종 스냅샷 복원 · 본 apply · DNS 컷오버


## 데이터 계층 (P2, PH-70)

- RDS MySQL 8.4, Multi-AZ, gp3 20GiB
- DB 서브넷 그룹 및 파라미터 그룹 관리
- 기존 RDS 스냅샷 복원 방식
- 10/21 Terraform plan 완료, 10/27 리허설 apply · 10/28 본 apply 예정

설정 및 복원 절차: [PH-70 RDS Terraform](main/modules/data/README.md)


## P2가 쓸 출력값

`modules/data`에서 이렇게 참조하면 됨:

| 출력값 | 용도 |
|---|---|
| `module.network.vpc_id` | VPC 참조 |
| `module.network.data_subnet_ids` | RDS 서브넷 그룹 |
| `module.network.rds_sg_id` | RDS에 붙일 보안그룹 |

통합 apply 직후 P2에게 넘기는 값(루트 `terraform output`으로도 나온다):

| 출력값 | 용도 |
|---|---|
| `alb_arn` | WAF 연결 |
| `https_listener_arn` | 리스너 참조 |
| `alb_dns_name` | Route 53/가비아 DNS 전환 |
| `alb_zone_id` | Route 53 별칭 레코드 |
| `target_group_arn` | ASG(#695)가 서버를 붙일 대상 그룹 |

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
terraform plan -out=main.tfplan -var="snapshot_identifier=<스냅샷 ID>"
# 출력의 "Plan: N to add, 0 to change, 0 to destroy"가 예상한 개수인지, 예상 밖의 변경·삭제가 없는지 눈으로 확인한다
terraform apply main.tfplan
```

- 계획 파일(`main.tfplan`)은 커밋하지 않는다(`.gitignore`의 `*.tfplan`).
- 저장한 계획은 apply 때 확인 질문 없이 바로 실행된다. plan 출력을 확인한 직후에 apply하고, 시간이 지났거나 코드·상태가 바뀌었으면 다시 plan한다.
- 예상 개수는 apply 범위에 따라 다르다. `dev`에 머지된 모듈만 올라간다: 네트워크·RDS만이면 31개, 컴퓨트(ALB·IAM·ASG)까지 머지되면 44개, VPN까지 머지되면 51개다.

### 실제 apply 단위 (PH-51 + PH-70-②)

- `terraform/main`은 `network`·`compute`·`data`·`vpn`이 **같은 루트 모듈**이고 상태 파일(`main/terraform.tfstate`)도 하나다. 그래서 "컴퓨트만 apply"나 "RDS만 apply"가 따로 있지 않고, `terraform apply` 한 번에 `dev`에 머지된 모듈이 함께 올라간다(네트워크, ALB·IAM·ASG, P2의 RDS 복원, VPN 게이트웨이와 라우팅).
- `snapshot_identifier`는 필수 변수라 **컴퓨트만 올리려 해도 실제 스냅샷 ID가 필요하다.** apply 전에 P2가 최종 스냅샷 ID(10/28 것)를 확정해서 알려 준다. plan 검증용 값(리허설 스냅샷 `corebank-import-20261021` 등)으로 본 apply하지 않는다(10/27 P2 리허설 apply만 리허설 스냅샷을 쓴다).
- 컴퓨트·VPN PR(#750 → #752 → #756)은 P2의 리허설 apply가 끝난 뒤 머지한다. 먼저 머지되면 `dev` apply 때 서버(ASG)까지 올라가 옛 DB에 붙는다.
- 루트 apply를 P2와 P5 중 누가 실행할지는 **apply 전에 협의해서 확정**한 뒤, 그 사람이 **한 번의 통합 apply**로 진행한다.
- apply 직후 P2에게 넘기는 값은 위 "통합 apply 직후" 표를 따른다.

### 서버 부팅 확인 (apply 후)

- 서버 접속: Session Manager (인스턴스는 EC2 콘솔이나 ASG에서 확인)
- 부팅 로그: `/var/log/corebank-bootstrap.log` (apt 실패, Parameter Store 읽기 실패가 여기에 나온다)
- 컨테이너: `docker ps`에서 `corebank-server`와 `corebank-mock-external`이 Up인지
- 앱 헬스: `curl -s http://localhost:8080/api/v1/actuator/health`
- 대상 그룹: EC2 > 대상 그룹 > `corebank-tg-was`에서 healthy 2대인지

### 이미지 버전 (릴리스마다 수동 갱신)

시작 템플릿은 커밋 SHA 태그로 이미지를 고정한다(`modules/compute/variables.tf`의 `app_image`, `mock_external_image`). 서버 한 대만 교체돼도 두 서버가 항상 같은 버전이 되게 하려는 것이다. 새 릴리스를 올릴 때는 SHA를 바꿔 apply하고 ASG를 새로고침한다. 배포 스크립트를 ASG용으로 바꾸는 PH-55에서 자동화한다.

## 모의 대외기관을 VM 한 곳으로 옮기기 (VPN 터널 성립 뒤)

컷오버 때는 서버마다 모의 대외기관 컨테이너를 띄운다(`interbank_host`가 비어 있는 기본값). 서버가 2대라 거래 장부가 따로 생기므로, 터널이 선 뒤 VM의 모의 서버 한 곳으로 옮긴다.

1. VM에서 모의 서버를 띄우고 9300 포트를 확인한다.
2. `modules/network/security_groups.tf`에서 WAS 보안 그룹에 VM으로 나가는 TCP 9300 egress를 추가한다.
3. `modules/compute`의 `interbank_host`에 VM 주소를 넣고 apply한다. 시작 템플릿 새 버전만 생기고 떠 있는 서버는 그대로다.
4. 진행 중인 타행 이체가 없는 시간에 서버를 한 대씩 교체한다. 교체된 서버에는 모의 서버 컨테이너가 뜨지 않는다. 한 대가 healthy가 된 뒤 다음 대를 교체한다.
5. 되돌릴 때는 `interbank_host`를 비우고 같은 방식으로 교체한다.

모의 서버 장부는 컨테이너 메모리에만 있어서 교체하면 사라진다.

## 아직 미정인 것

- util 노드용 SG(8000) 규칙은 안 넣음 — D-25(하이브리드 인프라) 팀 확인(O-11) 끝나기 전까지 보류
