# Corebank

Corebank 프로젝트는 Spring Boot 기반의 백엔드 애플리케이션입니다.

## 🛠 Tech Stack & Versions

- **Language**: Java 21
- **Framework**: Spring Boot 4.0.7
- **Build Tool**: Gradle
- **Dependencies**:
  - **Web**: Spring Web MVC, Validation
  - **Data**: Spring Data JPA, MySQL Connector/J, Flyway
  - **Ops**: Spring Boot Actuator, DevTools
  - **Test**: Testcontainers (MySQL), JUnit Platform
  - **Utility**: Lombok

## 🚀 Getting Started

### Prerequisites
- Java 21 이상
- Gradle

### Build
```bash
# 프로젝트 빌드
./gradlew build
```

### Run
```bash
# 애플리케이션 실행
./gradlew bootRun
```

### Test
```bash
# 테스트 코드 실행
./gradlew test
```


## 🏛️ Architecture & Developer Playbook

- **[🏗️ DB & Flyway 아키텍처 및 공통 클래스 가이드](docs/team_db_architecture_guide.md)**: Flyway 도입 배경, 서버 기동 흐름, `BaseEntity`·`IntegrationTestSupport` 상속 규칙 및 환경별 5종 YAML 설정 안내
- **[💻 실무 개발 및 로컬 DB 셋업 가이드](docs/team_db_setup_guide.md)**: 신규 팀원 1분 도커 셋업, 새 테이블/컬럼 추가 시 마이그레이션 SQL 작성 규칙(12대 금지 규칙) 및 트러블슈팅 FAQ
- **[📋 Flyway 적용 가이드](docs/flyway_guide.md)**: v3 스키마 마이그레이션 파일 구조, 프로파일별 동작, 4대 규칙, `ddl-auto: validate` 엔티티 주의사항, 파티션 유지보수 및 자주 나는 오류
- **[📐 테이블 스키마 레퍼런스](docs/schema_reference.md)**: 23개 테이블 · 241개 컬럼 상세 — 키·인덱스·CHECK 제약·컬럼별 담기는 정보 안내
- **[📊 DB ERD v3.0](docs/corebank_erd.md)**: 23개 테이블 간 관계 다이어그램 (Mermaid)
- **[🔗 공통 API 규칙](docs/api_conventions.md)**: 응답 형식·오류코드 마스터·Enum 마스터·필드명 통일 규칙 — API 계약의 유일한 원본
- **[⚠️ 오류코드 공통 인프라 가이드](docs/error_handling_guide.md)**: `ErrorCode`/`BusinessException`/`ApiExceptionHandler` 사용법 및 도메인별 오류코드 Enum 작성법
- **[🤝 팀 협업 가이드](docs/team_collaboration_guide.md)**: 정기 회의·커뮤니케이션 규칙, 코드리뷰 로테이션·Pn 룰, Git 브랜치/커밋/PR 컨벤션
- **[🤖 PR-Agent 사용 가이드](docs/pr_agent_guide.md)**: AI 코드 리뷰(Claude Haiku 4.5 하이브리드) 도입 배경, 자동/수동 리뷰 명령어 및 예산($10/월) 모니터링 가이드
