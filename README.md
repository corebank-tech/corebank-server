# Corebank Server

계정계 코어뱅킹 서버. 고객·계좌·원장·이체·상품/상품가입·한도 도메인을
**헥사고날 아키텍처**로 구성하고, 레이어 의존 방향을 ArchUnit으로 자동 검증합니다.

신한DS 금융SW 풀스택 개발자 양성 과정 7기 팀 프로젝트 · 7인

---

## Tech Stack

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.7, Spring Web MVC, Validation |
| Persistence | Spring Data JPA, Querydsl, MySQL, Flyway |
| Cache / Infra | Redis, Docker Compose |
| Test | JUnit 5, Testcontainers(MySQL), ArchUnit |
| Ops | Spring Boot Actuator, GitHub Actions |
| Build | Gradle |

## Architecture

도메인별로 `api → application → domain ← adapter` 4계층을 두고,
**domain은 어떤 바깥 계층도 참조하지 않습니다.**

```
src/main/java/com/shinhan/corebank/
├── customer/          고객
├── auth/              인증
├── otp/               OTP
├── account/           계좌
├── transfer/          이체
├── autotransfer/      자동이체
├── scheduledtransfer/ 예약이체
├── product/           상품
├── subscription/      상품가입
├── terms/             약관
├── signup/            가입
├── limit/             한도
├── batch/             배치
├── adapter/           외부 연동 어댑터
└── common/            공통(응답 규격 · 오류코드 · 설정)
```

각 도메인은 아래 구조를 따릅니다.

```
<domain>/
├── api/           컨트롤러 · 요청/응답 DTO
├── application/   유스케이스 · 포트 인터페이스
├── domain/        도메인 모델 (외부 의존 없음)
└── adapter/       JPA 엔티티 · Repository 구현 · 외부 어댑터
```

의존 방향 위반은 ArchUnit 테스트가 CI에서 잡습니다.
상세: [헥사고날 아키텍처 가이드](docs/hexagonal_architecture_guide.md)

## Getting Started

### Prerequisites

- Java 21+
- Docker / Docker Compose (MySQL · Redis)

### Run

```bash
# 1. 인프라 기동 (MySQL, Redis)
docker compose up -d

# 2. 스키마 마이그레이션 + 애플리케이션 실행
./gradlew bootRun
```

### Test

```bash
./gradlew test          # 단위 · 통합(Testcontainers) · ArchUnit 전체
```

API 문서는 기동 후 `/swagger-ui/index.html`에서 확인합니다.
([Swagger UI 가이드](docs/swagger_ui_guide.md))

## Database

Flyway로 스키마를 버전 관리합니다. 마이그레이션 파일은
`src/main/resources/db/migration`에 있고, 도메인 단위로 분리했습니다.

| 파일 | 내용 |
|---|---|
| `V...create_customer_auth.sql` | 고객 · 인증 |
| `V...create_product.sql` | 상품 |
| `V...create_account.sql` | 계좌 |
| `V...create_ledger.sql` | 원장 |
| `V...create_transfer_ext.sql` | 이체 |
| `V...create_limit.sql` | 한도 |
| `V...create_subscription.sql` | 상품가입 |
| `V...create_infra.sql` | 인프라 공통 |
| `V...partition_maintenance.sql` | 파티션 관리 |
| `R__seed_master_data.sql` | 마스터 시드 데이터 (반복 실행) |

- [ERD](docs/corebank_erd.md) · [스키마 레퍼런스](docs/schema_reference.md)
- [Flyway 작성 규칙](docs/flyway_guide.md) · [파일 역할 구분](docs/flyway_file_role_guide.md)

## Conventions

팀 전체가 참조하는 규약 문서입니다. 새로 합류하면 앞의 세 개를 먼저 읽어주세요.

| 문서 | 내용 |
|---|---|
| [api_conventions.md](docs/api_conventions.md) | 공통 응답 형식 · 엔드포인트 명명 |
| [error_handling_guide.md](docs/error_handling_guide.md) | 오류코드 마스터 · 예외 처리 |
| [hexagonal_architecture_guide.md](docs/hexagonal_architecture_guide.md) | 레이어 책임 · 의존 방향 |
| [team_collaboration_guide.md](docs/team_collaboration_guide.md) | 브랜치 · PR · 코드리뷰 |
| [team_db_setup_guide.md](docs/team_db_setup_guide.md) | 로컬 DB 세팅 |
| [team_db_architecture_guide.md](docs/team_db_architecture_guide.md) | DB 아키텍처 |
| [redis_setup_guide.md](docs/redis_setup_guide.md) | Redis 세팅 |
| [otp_integration_guide.md](docs/otp_integration_guide.md) | OTP 연동 |

### ADR

설계 결정과 그 근거를 기록합니다.

- [0001. 상품가입 생성 검증](docs/adr/0001-product-subscription-creation-validation.md)
- [0002. 도메인 간 계좌 조회 방식](docs/adr/0002-cross-domain-account-read-mechanism.md)

## CI

| 워크플로 | 역할 |
|---|---|
| [`corebank.yml`](.github/workflows/corebank.yml) | 빌드 · 테스트(ArchUnit 포함) |
| [`pr_agent.yml`](.github/workflows/pr_agent.yml) | PR 자동 리뷰 ([가이드](docs/pr_agent_guide.md)) |

기본 브랜치는 `dev`입니다.
