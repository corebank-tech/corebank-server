# 🛡️ Corebank 팀원 전용 DB & Flyway 아키텍처 및 공통 클래스 가이드

> **대상**: Corebank 프로젝트 개발 팀원 전체  
> **목적**: 안전한 DB 스키마 형상 관리(Flyway) 아키텍처 배경, 실행 메커니즘, 공통 클래스 활용법 및 환경별 동작 구조 안내

---

## 1. 💡 왜 Flyway와 Testcontainers를 도입했나요?

1. **JPA `ddl-auto: update`의 위험성 원천 차단**: 
   - 실무 환경에서 JPA의 `update`는 컬럼 삭제나 타입 변경을 감지하지 못하며, 운영 DB를 망가뜨릴 위험이 큽니다.
   - 이제부터 모든 테이블 생성/변형은 `Flyway SQL 마이그레이션 파일(DDL)`로만 관리하고, JPA(Hibernate)는 오직 `validate`(자바 엔티티와 실제 DB 스키마 일치 여부 검증) 역할만 수행합니다.
2. **팀원 간 DDL 충돌 및 락(Lock) 방지**: 
   - 6명의 팀원이 동시에 개발할 때 발생하는 순번 충돌(`V1`, `V2` 등)을 막기 위해 **타임스탬프 기반 파일명 규칙**을 도입하였습니다.
3. **어디서나 똑같은 테스트 환경 (Testcontainers)**: 
   - 로컬에 DB가 켜져 있든 꺼져 있든, `./gradlew test`를 실행하면 도커를 통해 1회용 격리 DB가 자동 생성되어 테스트 후 깔끔하게 소멸됩니다.

---

## 2. 🏗️ 프로젝트 DB 및 Flyway 전체 아키텍처와 기동 메커니즘

우리 프로젝트의 DB 파이프라인은 **"Flyway(구조 생성) ➡️ Hibernate(구조 검증) ➡️ DataLoader(데이터 적재)"** 순서로 100% 완전 자동으로 유기적으로 동작합니다. 개발자나 운영자가 수동으로 DB 스크립트를 칠 필요가 전혀 없습니다.

```mermaid
flowchart TD
    A[서버 / 테스트 기동] --> B[1단계: Flyway 마이그레이션 엔진 개입]
    B -->|flyway_schema_history 조회| C{미적용 V... / R...<br/>SQL 스크립트 존재?}
    C -->|Yes| D[DDL 트랜잭션 순차 실행<br/>테이블/인덱스 생성 및 마스터데이터 삽입]
    C -->|No| E[최신 상태 유지 확인]
    D --> F[2단계: Hibernate ORM 초기화]
    E --> F
    F -->|ddl-auto: validate| G{Java @Entity 구조 ==<br/>실제 DB 테이블 구조?}
    G -->|불일치| H[🚨 기동 차단 Error:<br/>Schema-validation: missing column etc.]
    G -->|일치 완벽 통과| I[3단계: Spring Boot 서비스 오픈]
    I -->|@Profile local| J[DemoDataLoader: 로컬 시드 데이터 자동 로딩]
    I -->|@Profile prod| K[🚀 트래픽 수신 개시<br/>운영 시드 로딩 차단]
```

### 🔹 1단계: Flyway 마이그레이션 엔진 최우선 개입
- 스프링 부트 애플리케이션이 시작되거나 테스트가 실행될 때, JPA(Hibernate)가 켜지기 **직전에 Flyway가 가장 먼저 DB에 접속**합니다.
- DB의 `flyway_schema_history` 테이블을 검사하여, 아직 실행되지 않은 신규 마이그레이션 파일(`V<timestamp>__*.sql`)과 멱등성 마스터 데이터(`R__*.sql`)를 감지하고 트랜잭션 내에서 순차적으로 실행하여 DB 스키마를 최신화합니다.

### 🔹 2단계: Hibernate ORM 스키마 유효성 검증 (`ddl-auto: validate`)
- Flyway가 테이블 생성을 완료하면, 그제서야 JPA(Hibernate)가 기동되면서 우리의 자바 `@Entity` 클래스들과 실제 DB 테이블 구조(컬럼명, 데이터 타입, 널 여부 등)가 100% 일치하는지 엄격하게 검증합니다.
- 만약 엔티티에는 필드가 있는데 SQL 마이그레이션을 안 만들었거나 타입이 다르면 **서버 기동이 즉시 차단**되어 운영 DB 파손을 원천 방지합니다.

### 🔹 3단계: 환경별 시드 데이터 로딩 및 서비스 오픈
- 검증이 통과되면 서버가 오픈됩니다. 이때 로컬 환경(`local`)에서는 `DemoDataLoader`가 작동하여 개발/시연 편의를 위한 더미 데이터(`local-demo-data.sql`)를 자동 삽입합니다. (운영 `prod`에서는 안전하게 자동 차단됨)

---

## 3. 📦 신규 작성된 핵심 공통 클래스 활용 및 상속(`extends`) 가이드

이번 작업으로 생성된 인프라 공통 클래스들은 팀원 여러분들이 **직접 코드를 구현할 때 반드시 상속하고 규칙을 준수**해야 하는 핵심 기반입니다.

### ① `BaseEntity.java` (모든 JPA 엔티티의 필수 부모 클래스)
- **역할**: 생성 시각(`createdAt`)과 수정 시각(`updatedAt`)을 공통으로 관리하며, `DATETIME(6)` 타입으로 **KST(+09:00) 시각**을 자동 주입합니다.
- **상속 방법 (필수)**: 새로 만드는 모든 비즈니스 `@Entity` 클래스는 무조건 `extends BaseEntity`를 상속해야 합니다.
  ```java
  package com.shinhan.corebank.account;
  import com.shinhan.corebank.common.entity.BaseEntity; // 👈 공통 부모 임포트
  import jakarta.persistence.Entity;

  @Entity
  public class Account extends BaseEntity { // 👈 상속받으면 createdAt, updatedAt 자동 생성!
      private String accountNumber;
      private Long balance;
      // 생성일자 필드를 직접 선언하지 마세요!
  }
  ```
- ⚠️ **주의**: SQL 마이그레이션(DDL) 작성 시 절대로 `DEFAULT CURRENT_TIMESTAMP`를 쓰지 마세요! 시각 생성은 DB가 아니라 자바 JPA Auditing(`BaseEntity` + `JpaAuditingConfig`의 `Clock.system(ZoneId.of("Asia/Seoul"))`)이 담당합니다.
- ℹ️ **예외 — 다른 도메인 테이블의 부분 매핑**: 이 규칙은 "테이블 하나 = 정식(대표) 엔티티 하나"를 전제로 합니다. 다른 도메인이 소유한 테이블을 자기 도메인 목적으로 일부 컬럼만 떼어 매핑하는 경량 엔티티(예: `transfer` 도메인이 `account` 테이블의 락·잔액 컬럼만 매핑하는 `AccountLockJpaEntity`)는 대상이 아닙니다. 이런 부분 매핑이 `createdAt`/`updatedAt`을 실제로 읽지 않는다면 `BaseEntity`를 상속하지 않아도 됩니다 — 상속하면 그 도메인이 관여할 때마다 테이블 소유 도메인의 감사 컬럼(`updated_at`)에 의도치 않은 추가 쓰기가 발생하기 때문입니다.
- ⚠️ **단, 부분 매핑을 언제 써도 되는지는 따로 판단하세요 (ADR-0002)**: 위 예외는 "부분 매핑을 이미 쓰기로 정했을 때 `BaseEntity`를 어떻게 할지"에 대한 규칙이지, 부분 매핑 자체를 권장하는 규칙이 아닙니다. 판단 기준은 **그 접근이 쓰기·락을 포함하는가**입니다.
  - **쓰기·락이 필요하면 부분 매핑**: 락의 수명이 호출자 트랜잭션에 묶여야 해서 공개 UseCase 경계 뒤로 밀어낼 수 없습니다. `transfer`의 `AccountLockJpaEntity`(`SELECT ... FOR UPDATE` + 잔액·`last_transaction_at` 변경)가 여기 해당합니다.
  - **읽기 전용 조회면 소유 도메인의 공개 UseCase(인 포트)를 호출**: 판정 규칙이 소유 도메인 한 곳에만 남고, 스키마 변경이 기동 실패가 아니라 컴파일 에러로 드러납니다. 예 — `subscription`의 `AccountLookupAdapter` → `account`의 `WithdrawableAccountQueryUseCase`(#263), `AccountNumberQueryAdapter` → `AccountNumberQueryUseCase`(#177). 이때 호출 도메인의 아웃 포트와 값 객체는 그대로 두고 **어댑터에서 변환**해서, 타 도메인 타입이 application 계층으로 새지 않게 합니다.
  - `autotransfer`·`scheduledtransfer`의 `AccountLookupJpaEntity`는 읽기 전용이라 이 기준상 UseCase 대상이지만 아직 전환 전입니다 — 남아 있다는 이유로 새 코드에서 따라 쓰지 마세요. 대안 검토와 결정 근거는 `docs/adr/0002-cross-domain-account-read-mechanism.md`에 있습니다.

### ② `IntegrationTestSupport.java` (모든 통합 테스트의 필수 부모 클래스)
- **역할**: `@SpringBootTest` 실행 시 로컬 PC에 DB가 켜져 있지 않아도, **Testcontainers가 알아서 MySQL 8.4 1회용 도커 DB를 띄우고 Flyway 마이그레이션까지 마친 완벽한 테스트 환경**을 제공합니다.
- **상속 방법 (필수)**: DB 연동이나 스프링 컨텍스트가 필요한 모든 통합 테스트는 무조건 `extends IntegrationTestSupport`를 상속해야 합니다.
  ```java
  package com.shinhan.corebank.account;
  import com.shinhan.corebank.IntegrationTestSupport; // 👈 테스트 부모 임포트
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;

  class AccountServiceTest extends IntegrationTestSupport { // 👈 상속만 하면 DB 환경 셋업 끝!
      
      @Autowired AccountService accountService;

      @Test
      void 계좌_이체_통합테스트() {
          // 1회용 격리 DB에서 안전하고 빠른 테스트 수행!
      }
  }
  ```

---

## 4. 🌍 환경별(`local` vs `test` vs `prod`) 동작 방식 요약 비교표

우리의 5종 YAML 설정(`application-*.yml`)은 각 실행 환경의 목적에 맞춰 완벽히 분리되어 작동합니다.

| 구분 | 로컬 개발 (`local`) | 통합 테스트 (`test`) | 운영 배포 (`prod`) |
| :--- | :--- | :--- | :--- |
| **사용 DB** | 로컬 도커 (`minicore-mysql`) | Testcontainers (1회용 도커 DB) | AWS RDS MySQL 8.4 |
| **접속 계정** | `root` / `localpw` | Testcontainers 자동 임시 계정 | **`app_user`** / **`flyway_user`** (DDL/DML 권한 분리) |
| **Flyway 동작** | 시작 시 자동 실행 (`V...` + `R...`) | 시작 시 매번 빈 DB에 전체 실행 | 시작 시 미적용 스크립트만 자동 실행 |
| **JPA DDL 모드** | `validate` (스키마 불일치 시 에러) | `validate` | `validate` |
| **시드 데이터** | `DemoDataLoader` 자동 로딩 ⭕ | 자동 로딩 ❌ (순수 테스트 데이터만) | 자동 로딩 ❌ (보안 및 데이터 보호) |
| **타임존 관리** | DB/JVM/로그 모두 **KST** (변환 없음) | DB/JVM/로그 모두 **KST** (변환 없음) | DB/JVM/로그 모두 **KST** (변환 없음) |
