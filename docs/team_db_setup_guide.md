# 🛡️ Corebank 팀원 전용 DB & Flyway 실무 개발 및 셋업 가이드

> **대상**: Corebank 프로젝트 개발 팀원 전체  
> **목적**: 신규 팀원 로컬 DB 셋업, Flyway 마이그레이션 DDL 작성 규칙, 통합 테스트 작성법 및 트러블슈팅 안내  
> **아키텍처 및 공통 클래스 안내**: [team_db_architecture_guide.md](team_db_architecture_guide.md) 참조

---

## 1. 💻 신규 팀원 1분 로컬 셋업 가이드

프로젝트를 클론(Clone)받은 후 아래 2단계만 실행하면 즉시 개발을 시작할 수 있습니다.

### 1단계: 로컬 개발용 MySQL 도커 컨테이너 기동
터미널을 열고 프로젝트 루트 디렉토리에서 아래 명령을 실행합니다.
```bash
docker compose up -d minicore-mysql
```
- **DB 접속 정보 (DBeaver / IntelliJ Datagrip 등 연동 시)**:
  - **Host**: `localhost` / **Port**: `3306`
  - **Database**: `minicore` (테스트 스크래치용: `minicore_scratch`)
  - **Username**: `root` / **Password**: `localpw`
  - **타임존 및 셋업**: 글로벌 표준인 **`UTC(+00:00)`** 및 `utf8mb4_0900_ai_ci`로 고정되어 있습니다.

### 2단계: 애플리케이션 기동 및 스키마 자동 생성 확인
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
- 애플리케이션이 켜지면서 **Flyway가 DB 마이그레이션 스크립트를 자동 실행**하여 테이블을 만들고, `DemoDataLoader`가 시드 데이터(`local-demo-data.sql`)를 삽입합니다.

---

## 2. 📜 새 테이블이나 컬럼을 추가할 때는 어떻게 하나요?

엔티티(`@Entity`) 클래스만 수정해서는 DB 테이블이 변하지 않습니다. 반드시 `src/main/resources/db/migration/` 디렉토리에 SQL 파일을 추가해야 합니다.

### ① 파일명 작성 규칙 (절대 순번 V1, V2 사용 금지 ❌)
6인 협업 충돌을 막기 위해 반드시 `V<yyyyMMdd>_<HHmm>__<snake_case_설명>.sql` 형식을 지켜주세요. (언더바 개수에 주의!)
- ⭕ 올바른 예시: `V20260728_1430__create_account_table.sql`
- ❌ 잘못된 예시: `V1__create_account.sql`, `v20260728_1430_create_account.sql`

### ② DDL 작성 규칙 (★ 핵심 금지 규칙 준수 ★)
```sql
-- V20260728_1430__create_account_table.sql 예시
CREATE TABLE IF NOT EXISTS account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    balance BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, -- 낙관적 락(Optimistic Lock) 필수 필드
    created_at DATETIME(6) NOT NULL,   -- DEFAULT CURRENT_TIMESTAMP 사용 금지!
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
```
1. **`TIMESTAMP` 타입 사용 절대 금지**: 무조건 `DATETIME(6)`을 사용합니다.
2. **`DEFAULT CURRENT_TIMESTAMP` 사용 절대 금지**: 생성/수정 시각은 DB 기본값이 아니라 **JPA Auditing(`BaseEntity`)이 UTC 시각을 주입**합니다.
3. **모든 비즈니스 엔티티는 `BaseEntity`를 상속(`extends`)**:
   ```java
   @Entity
   public class Account extends BaseEntity { // createdAt, updatedAt 자동 상속!
       ...
   }
   ```

### ③ 공통 코드/기준 데이터 추가 방법 (`R__` 스크립트)
계좌 상태 코드, 거래 유형 등 변하지 않는 기초 마스터 데이터는 **`R__<snake_case_설명>.sql`** 파일로 작성합니다. (기동 시마다 반복 실행됨)
- ⚠️ **멱등성(Idempotency) 필수**: 여러 번 실행해도 오류가 나지 않도록 무조건 `ON DUPLICATE KEY UPDATE`를 작성해야 합니다.
```sql
-- R__code_account_status.sql 예시
INSERT INTO account_status (code, name, created_at, updated_at) VALUES
  ('ACTIVE', '정상', '2026-07-28 00:00:00.000000', '2026-07-28 00:00:00.000000')
ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = VALUES(updated_at);
```

---

## 3. 🧪 통합 테스트는 어떻게 작성하나요?

새로운 Service나 Repository의 DB 연동 테스트 코드를 작성할 때, 기본 `@SpringBootTest`를 만들면 DB 연결 에러가 발생합니다.
반드시 우리가 만들어 둔 **`IntegrationTestSupport`를 상속**받아 작성해주세요!

```java
package com.shinhan.corebank.account;

import com.shinhan.corebank.IntegrationTestSupport; // 👈 공통 부모 상속
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountServiceTest extends IntegrationTestSupport {
    
    @Autowired AccountService accountService;

    @Test
    void 계좌_생성_테스트() {
        // Testcontainers가 자동으로 1회용 DB를 띄우고 Flyway 마이그레이션까지 완벽히 마친 상태에서 테스트됨!
    }
}
```

---

## 4. 🚨 트러블슈팅 & FAQ

**Q1. 마이그레이션 SQL 파일을 작성해서 기동했는데, 오타가 있어서 이미 실행된 SQL 파일 내용을 수정했더니 에러(`Validate failed: migration checksum mismatch`)가 납니다!**  
- **원인**: Flyway는 한 번 실행된 `V...` 파일의 체크섬을 `flyway_schema_history` 테이블에 기록해두고, 파일 내용이 변조되면 보안상 기동을 차단합니다.
- **해결법 (로컬 개발 시)**: 로컬 DB 컨테이너를 재생성하여 초기화하면 깨끗하게 다시 적용됩니다.
  ```bash
  docker compose down -v
  docker compose up -d minicore-mysql
  ```

**Q2. 로그에 KST(한국 시간)로 찍히는데 DB에는 9시간 이전 시간(UTC)으로 들어갑니다. 정상인가요?**  
- **정상입니다.** 글로벌 서비스 표준에 따라 DB 저장 및 서버 JVM 시스템 시간은 **UTC**로 통일되어 있으며, 개발/운영 모니터링 편의성을 위해 콘솔 로그(`logback-spring.xml`)에서만 `KST(Asia/Seoul)`로 변환하여 보여주도록 설계되었습니다. 절.대. 컨테이너나 JVM에 `TZ=Asia/Seoul`을 넣지 마시길 바랍니다.
