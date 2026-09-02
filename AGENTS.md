# AGENTS.md

이 파일은 **코딩 에이전트를 위한 진입점**이다. 사람용 개요는 [README.md](README.md)를 본다.
상세 규약은 여기 옮겨 적지 않고 §5 색인에서 링크로 끌어간다. **150줄을 넘기지 않는다.**

## 1. 정본 우선순위

문서끼리 어긋나면 이 순서로 따른다.

> **이슈의 인수기준(`REQ-*`) > `docs/adr/` > `docs/` 규약 문서 > 기존 코드 관행**

기존 코드가 규약과 다르면 코드가 틀린 것이다. 다만 그 수정은 **지금 작업 범위 안에서만** 하고,
범위 밖이면 고치지 말고 PR 본문에 한 줄 남긴다.

## 2. 명령

```bash
./gradlew test                                     # 전체 — 단위·Testcontainers·ArchUnit
./gradlew test --tests "*ArchitectureTest"         # 아키텍처 규칙만
./gradlew spotlessApply                            # 커밋 전 필수. CI의 spotlessCheck가 막는다
docker compose up -d minicore-mysql minicore-redis  # 로컬 인프라 (MySQL·Redis)
./gradlew bootRun                                  # Flyway 마이그레이션 후 기동
```

통합 테스트는 Testcontainers로 MySQL을 띄우므로 Docker가 떠 있어야 한다.

## 3. 절대 규칙

각 규칙 뒤의 **센서**는 이 규칙을 자동으로 잡는 장치다. `없음`이면 사람과 에이전트만 지킬 수 있으니
그 항목은 특히 조심한다.

1. **`domain` 패키지는 `application`·`adapter`를 참조하지 않고, JPA 애노테이션(`@Entity`·`@Table`)을 갖지 않는다.**
   영속성은 `adapter/out`의 JPA 엔티티가 맡고 Mapper로 변환한다.
   근거 [hexagonal_architecture_guide.md](docs/hexagonal_architecture_guide.md) · 위반 사례 PR #37
   센서: `product`·`subscription`만 (`ProductArchitectureTest`·`SubscriptionArchitectureTest`)

2. **다른 도메인은 `<domain>.api` 패키지를 통해서만 호출한다.**
   상대 도메인의 `application`·`domain`·`adapter`를 직접 참조하지 않는다.
   `api/`는 컨트롤러 자리가 아니라 도메인 간 계약이다(예: `limit.api.TransferLimitProvider`).
   근거 [ADR-0002](docs/adr/0002-cross-domain-account-read-mechanism.md)
   센서: `terms`만 (`TermsArchitectureTest`)

3. **성공 응답은 항상 HTTP `200` + `code="0000"`. `201`·`204`를 쓰지 않는다.**
   `data`에 성공/실패를 다시 담지 않는다(`data.result = "SUCCESS"` 패턴 폐기).
   판정값이 의미 있으면 `{"available": true}`처럼 의미 있는 필드로 표현한다.
   근거 [api_conventions.md](docs/api_conventions.md) §1 (REQ-CMN-007) · 센서: 없음

4. **오류는 `ErrorCode` + `BusinessException`으로만 던진다.**
   새 코드는 `api_conventions.md` §4 오류코드 마스터에 등록한다.
   **응답에 계정 존재 여부·스택트레이스·SQL·내부 경로를 노출하지 않는다.**
   실패 경로가 둘 이상이면 응답 본문뿐 아니라 **연산량과 응답 시간까지** 같은지 본다.
   근거 [error_handling_guide.md](docs/error_handling_guide.md), REQ-NFR-017 · 사고 PR #329 · 센서: 없음

5. **금액·잔액은 원 단위 정수, 금리는 `BigDecimal`.**
   DB는 금액이 전부 `BIGINT`다. `double`·`float`으로 금액을 계산하지 않는다.
   원화는 소수 자리가 없는 통화라 정수 원 단위가 정본이다. **금액을 `BigDecimal`로 바꾸지 마라.**
   금리 계산은 `BigDecimal`로 하되 `RoundingMode`를 명시하고 원 단위로 떨어뜨린다
   (`SubscriptionMaturityCalculator` 참고). `BigDecimal`은 `appliedRate`·`baseRate` 같은 금리 전용이다.
   **NOT NULL 컬럼은 `long`, NULL 허용 컬럼은 `Long`** — 타입이 DB 제약을 그대로 표현한다.
   `Long`끼리 `==`로 비교하지 않는다. 128원부터 조용히 어긋난다. `equals`나 `longValue()`를 쓴다.
   근거 [corebank_erd.md](docs/corebank_erd.md) · 센서: 없음 (도메인별 편차 정리는 #358)

6. **스키마 변경은 세 곳을 함께 갱신한다** — Flyway 증분 파일 + [schema_reference.md](docs/schema_reference.md) + [corebank_erd.md](docs/corebank_erd.md).
   `local`·`test`·`prod` 프로필은 `ddl-auto: validate`라서 Flyway 없이 엔티티만 고치면 기동이 깨진다.
   `V__`/`R__`/`seed` 구분은 [flyway_file_role_guide.md](docs/flyway_file_role_guide.md)를 따른다.
   센서: 없음 (기동 시 `validate` 실패로 늦게 드러난다)

## 4. 이 프로젝트에서 자주 나는 실수

- **신규 테스트가 시드 데이터와 채번 대역을 공유해 로컬은 통과하고 CI만 깨진다.**
  테스트 전용 prefix 대역을 쓴다. (PR #342 → #343 → #347)
- **`@Transactional` 안에서 "조회 후 검증"하면 TOCTOU가 난다.**
  유니크 제약이나 락으로 막는다. 동시성·원장·한도는 에이전트가 못 잡는 영역이다. (PR #256 → #274)
- **영속성 컨텍스트 캐시 때문에 테스트가 통과처럼 보인다.** `flush`/`clear` 후 다시 조회해 검증한다.

<!-- 각 도메인 담당자가 자기 도메인에서 자주 나는 실수를 한 줄씩 추가한다. 근거(PR·이슈 번호)를 같이 적는다. -->

## 5. 문서 색인

경로와 한 줄 설명만 둔다. 필요한 것만 열어서 읽는다.

**무엇을 만들기 전**

| 문서 | 언제 여는가 |
|---|---|
| [hexagonal_architecture_guide.md](docs/hexagonal_architecture_guide.md) | 패키지 배치·레이어 책임·요청이 DB까지 가는 흐름 |
| [api_conventions.md](docs/api_conventions.md) | 공통 응답·HTTP 상태·오류코드 마스터·Enum·필드명 |
| [error_handling_guide.md](docs/error_handling_guide.md) | `ErrorCode`/`BusinessException` 구현, 도메인 오류코드 추가 |
| [adr/](docs/adr/) | 되돌리기 어려운 설계 결정 — 0001 상품가입 검증, 0002 도메인 간 계좌 조회 |

**DB · 마이그레이션**

| 문서 | 언제 여는가 |
|---|---|
| [schema_reference.md](docs/schema_reference.md) | 28개 테이블 268개 컬럼 정의 |
| [corebank_erd.md](docs/corebank_erd.md) | 테이블 관계, 금액·시각 타입 계약 |
| [flyway_guide.md](docs/flyway_guide.md) | Flyway 설정과 마이그레이션 작성 규칙 |
| [flyway_file_role_guide.md](docs/flyway_file_role_guide.md) | `V__`·`R__`·`seed` 파일명과 역할 구분 |
| [team_db_architecture_guide.md](docs/team_db_architecture_guide.md) | Flyway 형상관리 구조·공통 클래스·환경별 동작 |
| [team_db_setup_guide.md](docs/team_db_setup_guide.md) | 로컬 DB 셋업, 통합 테스트 작성법, 트러블슈팅 |

**도메인 연동**

| 문서 | 언제 여는가 |
|---|---|
| [otp_integration_guide.md](docs/otp_integration_guide.md) | OTP는 Redis·Entity 직접 참조 없이 `otp.api`로만 호출 |
| [redis_setup_guide.md](docs/redis_setup_guide.md) | Redis 로컬·배포 셋업 (약관 열람 이력 TTL 30분) |
| [product_api_fe_handoff.md](docs/product_api_fe_handoff.md) | 상품 API의 FE 인계 계약 — 실제 요청·응답 예시 |

**협업 · 검증**

| 문서 | 언제 여는가 |
|---|---|
| [team_collaboration_guide.md](docs/team_collaboration_guide.md) | 브랜치·커밋·PR 규약, 코드리뷰 `Rn` 등급 |
| [pr_agent_guide.md](docs/pr_agent_guide.md) | PR-Agent·CodeRabbit 하이브리드 리뷰 운영 |
| [swagger_ui_guide.md](docs/swagger_ui_guide.md) | Swagger UI 접속과 Security 예외 처리 |

## 6. 브랜치 · 커밋 · PR

- 브랜치: `type/{이슈번호}-{설명}` — 예 `feat/338-scheduled-transfer-withdrawal-account-id`
- 커밋: `type(도메인): 작업 내용` — 예 `feat(account): 출금계좌 등록 여부 추가`
- PR 제목: `[type/도메인] 작업 내용` — 예 `[fix/account] 상품 채번 행 누락으로 가입 실패 (#342)`
- **PR base는 항상 `dev`.** `main` 머지는 곧 EC2 배포다.
- 리뷰 등급 `R1`~`R5`와 로테이션은 [team_collaboration_guide.md](docs/team_collaboration_guide.md) §5.
