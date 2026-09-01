# Corebank Server

계정계 코어뱅킹 서버. 고객·계좌·원장·이체·상품/상품가입·한도 도메인을
**헥사고날 아키텍처**로 구성하고, 레이어 의존 방향을 ArchUnit으로 검증합니다.

신한DS 금융SW 풀스택 개발자 양성 과정 7기 팀 프로젝트 · 6인

---

## Tech Stack

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.7, Spring Web MVC, Spring Security, Validation |
| Persistence | Spring Data JPA, Querydsl, MySQL, Flyway |
| Cache / Infra | Redis, Docker Compose |
| Test | JUnit 5, Testcontainers(MySQL), ArchUnit |
| Ops | Spring Boot Actuator, GitHub Actions |
| Build | Gradle |

## Architecture

도메인별로 `adapter(in) → application → domain ← adapter(out)` 계층을 두고,
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
├── adapter/           공통 예외 핸들러 등 전역 어댑터
└── common/            공통(응답 규격 · 오류코드 · 설정)
```

각 도메인은 대체로 아래 구조를 따릅니다.

```
<domain>/
├── adapter/in/web/  컨트롤러 · 요청/응답 DTO
├── application/     유스케이스(service) · 포트 인터페이스(port)
├── domain/          도메인 모델 (외부 의존 없음)
├── adapter/out/     JPA 엔티티 · Repository 구현 · 외부 어댑터
└── api/             다른 도메인에 공개하는 계약 (포트 인터페이스 · Command · DTO)
```

`api/`는 컨트롤러 자리가 아니라 **도메인 간 계약** 패키지입니다. 다른 도메인은
`limit.api.TransferLimitProvider`처럼 이 패키지를 통해서만 접근하고, 상대 도메인의
`application`·`domain`·`adapter`를 직접 참조하지 않습니다. 그래서 `api/`는 바깥에서
호출할 일이 있는 도메인에만 있습니다 — `customer` `account` `limit` `otp` `terms`
`auth` `signup` 일곱 개입니다. 배경은
[ADR 0002](docs/adr/0002-cross-domain-account-read-mechanism.md)를 참고하세요.

의존 방향은 ArchUnit 테스트로 검증합니다. 현재 `product`·`subscription`에 계층 방향
규칙이, `terms`에 "외부는 `terms.api`로만 접근" 규칙이 걸려 있고, 나머지 도메인이
같은 구조를 갖추는 대로 확대합니다.

아직 구조를 다 갖추지 않은 도메인도 있습니다. `terms`는 `api/`와 `adapter/out/`만
있고, `batch`는 `domain/` 없이 `application/`과 `adapter/`로만 구성됩니다.

상세: [헥사고날 아키텍처 가이드](docs/hexagonal_architecture_guide.md)

## Getting Started

### Prerequisites

- Java 21 (Gradle 데몬이 `gradle/gradle-daemon-jvm.properties`로 21에 고정됩니다)
- Docker / Docker Compose (MySQL · Redis)

### Run

```bash
# 1. 인프라 기동 (MySQL, Redis)
docker compose up -d minicore-mysql minicore-redis

# 2. 스키마 마이그레이션 + 애플리케이션 실행
./gradlew bootRun
```

`docker-compose.yml`에는 배포용 `corebank-server` 서비스도 함께 정의되어 있습니다.
로컬에서는 위처럼 인프라 두 개만 지정해 띄웁니다.

### Test

```bash
./gradlew test          # 단위 · 통합(Testcontainers) · ArchUnit 전체
```

API 문서는 기동 후 `http://localhost:8080/api/v1/swagger-ui/index.html`에서
확인합니다. ([Swagger UI 가이드](docs/swagger_ui_guide.md))

## Database

Flyway로 스키마를 버전 관리합니다. 마이그레이션 파일은
`src/main/resources/db/migration`에 있습니다.

초기 스키마는 도메인 단위로 나눠 두었습니다.

| 파일 | 내용 |
|---|---|
| `V...create_customer_auth.sql` | 고객 · 인증 |
| `V...create_product.sql` | 상품 |
| `V...create_account.sql` | 계좌 |
| `V...create_ledger.sql` | 원장 |
| `V...create_transfer_ext.sql` | 이체 |
| `V...create_limit.sql` | 한도 |
| `V...create_subscription.sql` | 상품가입 |
| `V...create_commoncode.sql` | 공통코드 |
| `V...create_infra.sql` | 인프라 공통 |
| `V...partition_maintenance.sql` | 파티션 관리 |
| `R__seed_master_data.sql` | 마스터 시드 데이터 (반복 실행) |

이후 스키마 변경은 `add_*` · `alter_*` · `drop_*` 형태의 증분 파일로 쌓입니다.
파일명 규칙과 `V__`/`R__` 구분은 아래 문서를 따릅니다.

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

### 코드 포맷

[Spotless](https://github.com/diffplug/spotless) + Palantir Java Format으로 서식을 자동 통일합니다.
들여쓰기 공백 4칸, 최대 120자입니다. 커밋 전에 실행해 주세요.

```bash
./gradlew spotlessApply
```

CI에서 `spotlessCheck`가 실패하면 위 명령을 실행하고 다시 커밋하면 됩니다.
IDE는 `.editorconfig`를 자동으로 따르므로 별도 설정이 필요 없습니다.
정렬을 유지해야 하는 구간은 `// spotless:off` ~ `// spotless:on`으로 감싸세요.

일괄 포맷 커밋이 `git blame`을 가리지 않도록 최초 1회 설정합니다.

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

### ADR

설계 결정과 그 근거를 기록합니다.

- [0001. 상품가입 생성 검증](docs/adr/0001-product-subscription-creation-validation.md)
- [0002. 도메인 간 계좌 조회 방식](docs/adr/0002-cross-domain-account-read-mechanism.md)

## CI / CD

| 워크플로 | 역할 |
|---|---|
| [`corebank.yml`](.github/workflows/corebank.yml) | PR · push 시 빌드 · 테스트 / `main` push 시 EC2 배포 |
| [`pr_agent.yml`](.github/workflows/pr_agent.yml) | PR 자동 리뷰 ([가이드](docs/pr_agent_guide.md)) |

기본 브랜치는 `dev`이고 평소 PR은 `dev`로 보냅니다. `main`에 머지되면
`corebank.yml`이 Docker 이미지를 빌드해 EC2에 배포하므로, `main` 머지는 곧 배포입니다.
