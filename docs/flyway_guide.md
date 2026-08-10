# 📋 Flyway 적용 가이드 — CoreBank

**전제**: Spring Boot 4.0.7 · MySQL 8.4 · `spring.jpa.hibernate.ddl-auto: validate`
---

## 1. 설정

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false      # 신규 DB. 기존 DB에 붙일 때만 true
    encoding: UTF-8                 # 한글 주석이 많아 필수
    validate-on-migrate: true
    out-of-order: false             # 버전 역순 적용 금지
  jpa:
    hibernate:
      ddl-auto: validate            # 스키마 권한은 Flyway 단독
```

`encoding: UTF-8`을 빼면 컬럼 `COMMENT`의 한글이 깨집니다. 기본값이 플랫폼 의존이라 로컬(macOS)에서는 되고 EC2에서 깨지는 식으로 갈립니다.

데이터베이스 자체는 Flyway가 만들지 않습니다. RDS에서 미리 생성하십시오.

```sql
CREATE DATABASE corebank
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

---

## 2. 프로파일별 동작

| 프로파일 | DB | Flyway | 비고 |
| --- | --- | --- | --- |
| `scratch` | 로컬 MySQL | `clean` + `migrate` 허용 | 마음껏 갈아엎는 용도 |
| `local` | 로컬 MySQL | `migrate`만 | 팀 공용 상태 재현 |
| `test` | Testcontainers | `migrate` (컨테이너마다 새로) | |
| `prod` | RDS | `migrate`만. **`clean` 금지** | |

```yaml
# prod 에서는 반드시
spring.flyway.clean-disabled: true
```

한 번이라도 `prod`에서 `clean`이 돌면 전 테이블이 날아갑니다. 기본값이 활성이므로 **명시적으로 꺼야** 합니다.

---

## 3. 규칙 4가지

**① 적용된 `V` 파일은 절대 수정하지 않습니다.**
변경은 새 파일에 `ALTER`로 씁니다.

```sql
-- V202608051430__add_account_nickname.sql
ALTER TABLE account ADD COLUMN nickname VARCHAR(30) NULL COMMENT '별칭';
```

**② 파일명은 `V{yyyyMMddHHmm}__{설명}.sql`.**
`V1__`, `V2__` 같은 순번은 쓰지 않습니다. 6명이 동시에 파일을 만들면 번호가 겹칩니다. 타임스탬프면 겹칠 일이 없습니다.

**③ 자기 도메인 파일만 만듭니다.**
남의 테이블을 고쳐야 하면 담당자에게 요청하십시오. 접두어 소유 원칙(`common_rev` §3-2)과 같습니다.

**④ 시드 데이터 분리 (V__ vs R__)**
이력 보존이 필수적인 데이터(예: 약관)나 보안 분리가 필요한 데이터는 **새 `V__` 파일에 `INSERT`** 방식으로 누적해야 합니다.
`R__` 스크립트는 체크섬이 바뀌면 전체가 재실행되므로, 과거 이력을 훼손하지 않는 **순수 교체 가능한 마스터 데이터(예: 공통 코드, 단순 상품 마스터)** 시딩에만 사용해야 합니다. (작성 시 `ON DUPLICATE KEY UPDATE` 패턴 유지)

---

## 4. `ddl-auto: validate` 통과를 위한 엔티티 주의사항

Flyway가 만든 스키마와 JPA 엔티티가 어긋나면 **부팅이 실패합니다.** 그게 의도지만, 아래 3가지는 미리 알고 있어야 헛수고를 안 합니다.

### 4-1. GENERATED 컬럼

`scheduled_transfer.active_dup_key` · `auto_transfer.active_dup_key`는 DB가 계산하는 컬럼입니다. 엔티티에 그냥 매핑하면 INSERT 때 값을 넣으려다 실패합니다.

```java
@Column(name = "active_dup_key", insertable = false, updatable = false)
private String activeDupKey;
```

아예 매핑하지 않아도 됩니다. `validate`는 **엔티티에 있는 컬럼만** 검사합니다.

### 4-2. JSON 컬럼

`verification_request.transaction_data` · `idempotency_key.response_snapshot` · `audit_log.detail`.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "transaction_data")
private Map<String, Object> transactionData;
```

Hibernate 6+ 기본 지원이라 별도 라이브러리가 필요 없습니다.

### 4-3. 파티션 테이블

`ledger_entry`의 PK는 `(ledger_entry_id, occurred_at)` **복합키**입니다. 파티션 키가 PK에 포함돼야 한다는 MySQL 제약 때문입니다.

```java
@Entity
@IdClass(LedgerEntryId.class)
public class LedgerEntry {
    @Id private Long ledgerEntryId;
    @Id private LocalDateTime occurredAt;
    ...
}
```

단일 `@Id`로 매핑하면 `validate`가 통과해도 `save()`가 이상하게 동작합니다.

`ledger_entry_id` 컬럼 자체는 DB에서 `AUTO_INCREMENT`이지만, **Hibernate는 `@IdClass` 복합키 구성 필드에 IDENTITY 채번 전략을 지원하지 않습니다.**

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)  // ❌ 여기서 ApplicationContext 로딩 자체가 실패한다
private Long ledgerEntryId;
```

```
Caused by: org.hibernate.id.IdentifierGenerationException: Identity generation isn't supported for composite ids
```

`validate`도 아니고 `save()` 시점도 아니라 **EntityManagerFactory를 만드는 애플리케이션 부팅 시점에 바로 실패**하므로 늦게 발견되진 않지만, DB가 AUTO_INCREMENT라고 해서 그대로 `@GeneratedValue`를 붙이면 안 됩니다. `ledgerEntryId`는 저장 전 애플리케이션이 직접 값을 채워야 하며(별도 시퀀스/채번 서비스), 실제 채번 전략은 팀이 별도로 결정해야 합니다.

또한 **파티션 테이블에는 FK를 선언할 수 없어** `account_id`·`transfer_id`에 DB 제약이 없습니다. `@ManyToOne` 매핑은 가능하지만 **정합성은 애플리케이션이 집니다.**

---

## 5. 파티션 유지보수

`ledger_entry`는 2026-07 ~ 2026-12 파티션과 `pmax`를 갖고 시작합니다. 2027년 이후 데이터는 전부 `pmax`로 들어가 **프루닝 효과가 사라집니다.**

`V202608010980__partition_maintenance.sql`이 프로시저를 만들어 둡니다.

```sql
CALL add_ledger_partition('2027-01-01');
```

매월 1일 배치로 **2개월 뒤 파티션**을 미리 만드십시오. 이미 있으면 아무 일도 하지 않습니다.

---

## 6. 첫 적용 절차

마이그레이션 SQL 파일은 이미 `src/main/resources/db/migration/` 에 배치되어 있습니다.
애플리케이션을 부팅하면 Flyway가 자동으로 실행합니다.

```bash
# 1. DB 생성 (1회)
mysql -h <rds-endpoint> -u admin -p -e \
  "CREATE DATABASE corebank DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;"

# 2. 부팅 (Flyway 자동 실행)
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 확인
mysql -e "SELECT version, description, success FROM corebank.flyway_schema_history ORDER BY installed_rank;"
```

`success = 0`인 행이 있으면 그 지점에서 멈춘 것입니다. **원인을 고친 뒤 실패한 행을 지우고** 재실행하십시오.

```sql
DELETE FROM flyway_schema_history WHERE success = 0;
```

---

## 7. 자주 나는 오류

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| `Migration checksum mismatch` | 적용된 `V` 파일을 수정함 | 파일 원복. 개발 중이면 `flyway clean` 후 재적용 |
| `Schema-validation: missing column [...]` | 엔티티에 있는데 DB에 없음 | 새 `V` 파일에 `ALTER ADD COLUMN` |
| `Schema-validation: wrong column type` | `DATETIME(6)` ↔ `LocalDateTime` 정밀도 불일치 | `@Column(columnDefinition = "DATETIME(6)")` |
| 한글 `COMMENT` 깨짐 | `flyway.encoding` 미설정 | `encoding: UTF-8` |
| `Cannot add foreign key constraint` | 파일 순서를 바꿈 | 타임스탬프 순서 확인 |
| 프로시저 생성 실패 | `DELIMITER` 미인식 | 해당 파일만 단독 실행 후 `flyway repair` |

---
