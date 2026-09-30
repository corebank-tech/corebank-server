# PH-60 최소 시드 실행·검증 가이드

## 1. 목적과 실행 방식

PH-60은 현행 `customer`, `account`, `transfer`, `ledger_entry`, `auto_transfer` 구조로 기능·성능 검증용 데이터를 만든다. 계정계·채널계 모델 분리는 후속 작업에서 다룬다.

대량 데이터 생성은 HTTP API로 노출하지 않는다. `phase2-seed` 프로필만으로는 실행되지 않으며, 정기 배포와 분리된 일회성 작업에서 `app.phase2-seed.execute=true`를 함께 지정해야 한다. 따라서 Swagger UI에 추가되는 엔드포인트는 없다.

실행 환경별 전체 명령은 로컬 시험은 §1-1, 운영 RDS 적재는 §6-3을 따른다. `local` 프로필을 함께 켜면 `DemoDataLoader`까지 실행되므로 PH-60 전용 실행에는 사용하지 않는다.

애플리케이션 로그의 `PH-60 minimum seed ready`가 출력되면 적재와 자체 정합성 검증이 끝난 것이며, 일회성 프로세스는 성공 코드 `0`으로 자동 종료된다. 적재 또는 검증에 실패하면 애플리케이션 시작이 실패해 0이 아닌 종료 코드를 반환한다.

### 1-1. 기존 로컬 데이터를 보존하고 직접 시험하기

기존 `minicore` 데이터베이스와 분리된 `minicore_ph60` 데이터베이스에서 시험한다.

1. 로컬 MySQL과 Redis를 실행한다.

```bash
docker compose up -d minicore-mysql minicore-redis
```

2. DBeaver·DataGrip 또는 MySQL 클라이언트로 로컬 MySQL에 접속해 시험용 데이터베이스를 만든다.

```sql
CREATE DATABASE IF NOT EXISTS minicore_ph60
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

3. 프로젝트 루트에서 사용하는 터미널에 맞는 명령 하나를 실행한다. `phase2-seed`만 활성화하고 로컬 DB 접속값을 명시한다. `local` 프로필은 데모 시드도 실행하므로 함께 사용하지 않는다. Flyway가 빈 데이터베이스에 스키마와 기준 상품을 먼저 넣은 뒤 PH-60 시드를 적재한다. DevTools 재시작을 끄므로 적재 완료 후 정상 종료 코드 `0`을 받을 수 있다.

Git Bash:

```bash
./gradlew bootRun --args='--spring.profiles.active=phase2-seed --app.phase2-seed.execute=true --spring.datasource.url=jdbc:mysql://localhost:3306/minicore_ph60?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true --spring.datasource.username=root --spring.datasource.password=localpw --spring.jpa.hibernate.ddl-auto=validate --spring.data.redis.host=localhost --spring.main.web-application-type=none --spring.task.scheduling.enabled=false --spring.devtools.restart.enabled=false'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=phase2-seed --app.phase2-seed.execute=true --spring.datasource.url=jdbc:mysql://localhost:3306/minicore_ph60?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true --spring.datasource.username=root --spring.datasource.password=localpw --spring.jpa.hibernate.ddl-auto=validate --spring.data.redis.host=localhost --spring.main.web-application-type=none --spring.task.scheduling.enabled=false --spring.devtools.restart.enabled=false"
```

Git Bash에서는 JDBC URL의 `&`가 셸 명령 구분자로 해석되지 않도록 `--args` 전체를 작은따옴표로 감싼다. 위 `root`와 `localpw`는 `compose.yaml`의 기본 로컬 계정 기준이며, 로컬 설정을 바꿨다면 실제 값으로 교체한다.

4. `PH-60 minimum seed ready` 로그와 종료 코드 `0`을 확인하고 §5의 검수 SQL을 `minicore_ph60`에서 실행한다. 이미 이 성공 로그가 출력됐다면 이후 Gradle 종료 코드만 실패했더라도 시드는 커밋된 상태일 수 있으므로, 바로 재실행하지 말고 먼저 건수와 정합성을 검사한다.
5. 처음부터 다시 시험하려면 운영 데이터에 손대지 말고 `minicore_ph60`만 삭제한 뒤 2단계부터 반복한다.

```sql
DROP DATABASE minicore_ph60;
```

`DROP DATABASE`는 로컬 시험용 `minicore_ph60`에만 사용한다. `minicore`와 운영 RDS에서는 실행하지 않는다.

## 2. 고정 데이터 규격

| 대상 | 건수·규칙 |
|---|---:|
| 고객 | 10,000명 |
| 계좌 | 30,000개(고객당 요구불·정기예금·적금 각 1개) |
| 성공 이체 | 235,000건 |
| 원장 | 500,000행(개시 30,000행 + 이체 출금·입금 470,000행) |
| 자동이체 | 5,000건 |
| 만기 임박 | 정기예금 100개, PH-15 적용 이후인 2026-11-01~11-30 분포 |
| `MATURED` | 0개(PH-60b 범위) |
| 기준 시각 | `2026-09-01 00:00:00` KST |
| 거래일 | `2026-09-01` (`transfer`·`ledger_entry`·GL 공통) |
| 인증용 평문 | 로그인·계좌 공통 `1234`(BCrypt 저장, 계좌비밀번호 숫자 4자리 규칙 충족) |

생성 공식과 기준 시각이 코드에 고정되어 있어 같은 버전에서는 고객·계좌·이체·원장·자동이체 값과 분포가 동일하다.

## 3. PH-60 전용 대역

| 식별자 | 시작 | 종료 |
|---|---:|---:|
| `customer_id` | 6,000,001 | 6,010,000 |
| `account_id` | 60,000,001 | 60,030,000 |
| `transfer_id` | 60,000,001 | 60,235,000 |
| `ledger_entry_id` | 60,000,001 | 60,500,000 |
| `auto_transfer_id` | 60,000,001 | 60,005,000 |
| 거래번호 시퀀스 | 6,000,000,001 | 6,000,265,000 |
| 계좌번호 은행코드 | `860` | `860` |

QA 시드의 고객·계좌·거래번호 대역과 겹치지 않는다. 각 행은 고정 PK와 거래번호를 사용하고 `INSERT IGNORE`로 생성하므로 다시 실행해도 행을 추가하거나 기존 잔액·거래를 초기화하지 않는다. 적재 후 정확한 건수와 정합성을 다시 검사하며, 일부 행만 존재하는 불완전한 상태는 오류로 종료한다.

## 4. 원장과 GL 규칙

- 모든 계좌에 개시 입금 원장 1행을 만든다.
- 성공 이체마다 동일한 `transfer_id`로 출금 1행과 입금 1행을 만든다.
- `transfer.trade_date`와 `ledger_entry.trade_date`는 개시 전표와 같은 `2026-09-01`이며 NULL을 허용하지 않는다.
- 계좌의 최종 `balance`는 입금 원장 합계에서 출금 원장 합계를 뺀 값과 같다.
- 계좌의 `last_transaction_at`은 해당 계좌 원장의 가장 최근 `occurred_at`과 같다.
- 개시 원장은 `transaction_type=OPENING`, `channel=BT`로 기록하며 상대편은 GL 개시 전표에 둔다.
- 즉시이체 원장은 `transaction_type=IMMEDIATE_TRANSFER`, `channel=WB`로 기록한다.
- 개시 잔액은 `20260901-OPN-000001` 전표 1건으로 기록한다.
- GL 분개는 현금 `10100` 차변과 예수금 `20100` 대변 각 1행이며 금액이 같다.
- PH-60b는 이 데이터를 이어서 확장하며 개시 전표를 추가로 만들지 않는다.
- 자동이체 시작일은 `2026-10-01`이며, 다음 실행일이 10월 1~28일로 분산되어 10월 배치 부하 검증에 실제 사용된다.

## 5. P4 검수 SQL

아래 두 쿼리는 모두 `0`을 반환해야 한다.

```sql
SELECT COUNT(*)
FROM (
    SELECT t.transfer_id
    FROM transfer t
    JOIN ledger_entry le ON le.transfer_id = t.transfer_id
    WHERE t.transfer_id BETWEEN 60000001 AND 60235000
      AND t.status = 'SUCCESS'
    GROUP BY t.transfer_id
    HAVING COUNT(*) <> 2
       OR SUM(le.direction = 'WITHDRAWAL') <> 1
       OR SUM(le.direction = 'DEPOSIT') <> 1
) broken_pairs;
```

```sql
SELECT COUNT(*)
FROM (
    SELECT a.account_id
    FROM account a
    JOIN ledger_entry le ON le.account_id = a.account_id
    WHERE a.account_id BETWEEN 60000001 AND 60030000
    GROUP BY a.account_id, a.balance
    HAVING a.balance <>
        SUM(CASE WHEN le.direction = 'DEPOSIT' THEN le.amount ELSE -le.amount END)
) balance_mismatches;
```

개시 잔액 전표는 다음 쿼리가 `0`이어야 한다.

```sql
SELECT ABS(
    SUM(CASE WHEN dr_cr = 'DEBIT' THEN amount ELSE 0 END)
  - SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END)
) AS debit_credit_difference
FROM gl_journal_entry
WHERE voucher_no = '20260901-OPN-000001';
```

## 6. 운영 적재 방법

### 6-1. 선택한 방식

운영 RDS에는 로컬 덤프를 옮기지 않는다. **main 배포와 별도로 운영 서버에서 배포된 JAR를 한 번만 실행해 운영 RDS에 직접 적재한다.** 이 프로세스는 HTTP 요청을 받지 않고 스케줄러도 실행하지 않으며, 적재와 자체 검증을 마치면 자동 종료된다.

자동 종료되는 것은 일회성 시드 프로세스뿐이다. 적재가 성공하면 데이터는 운영 RDS에 커밋되어 그대로 유지되므로, 이후 정상 `prod` 서버를 대상으로 수행하는 TPS·동시성 등 성능 테스트에는 지장이 없다.

정상 서비스의 배포 설정에는 `phase2-seed` 프로필과 `app.phase2-seed.execute` 플래그를 추가하지 않는다. 이 두 값은 아래 일회성 명령에만 넣는다.

역할은 다음과 같이 나눈다.

| 역할 | 담당 작업 |
|---|---|
| P6(PH-60) | 시드 코드·전용 대역·실행 절차 제공, 로컬 적재와 자체 검증 |
| main 배포 담당(P4) | 운영 RDS 사전 확인, 자동 채번 이동, 일회성 시드 프로세스 실행, 실행 로그·종료 코드·운영 적재 건수 기록 |
| P4 | 운영 적재 후 원장 쌍·잔액·차대변 검수 |
| P1 | PH-60 데이터를 사용한 TPS·동시성 성능 테스트와 결과 기록 |

P6는 시드와 실행 절차를 넘기고, 운영 실행자는 실제 적재 직후 실행 일시와 결과를 이 문서에 기록한다.

### 6-2. 실행 전 준비

1. FE·QA·다른 트랙과 점검 시간을 합의하고, 적재 중에는 성능 측정이나 대량 테스트를 실행하지 않는다.
2. 실행할 JAR가 main에 배포할 버전과 같은지 확인한다.
3. 운영 서버에 기존 `prod` 환경변수가 설정돼 있어 같은 JAR가 운영 RDS에 접속할 수 있는지 확인한다. DB 비밀번호를 명령행에 직접 적지 않는다.
4. 실행 직전 RDS 자동 백업 또는 수동 스냅샷의 완료 상태를 확인한다.
5. 운영 DB에 접속해 아래 현재 건수 쿼리를 실행하고 결과를 작업 기록에 남긴다. 처음 적재한다면 모두 `0`이어야 한다. 하나라도 `0`이 아니면 이전 실행 여부부터 확인하고 바로 재실행하지 않는다.

```sql
SELECT COUNT(*) AS customers
FROM customer WHERE customer_id BETWEEN 6000001 AND 6010000;

SELECT COUNT(*) AS accounts
FROM account WHERE account_id BETWEEN 60000001 AND 60030000;

SELECT COUNT(*) AS transfers
FROM transfer WHERE transfer_id BETWEEN 60000001 AND 60235000;

SELECT COUNT(*) AS ledger_entries
FROM ledger_entry WHERE ledger_entry_id BETWEEN 60000001 AND 60500000;

SELECT COUNT(*) AS auto_transfers
FROM auto_transfer WHERE auto_transfer_id BETWEEN 60000001 AND 60005000;
```

### 6-3. 자동 채번을 시드 대역 밖으로 이동

일반 요청이 PH-60 전용 ID를 먼저 사용하지 않도록 **시드 프로세스를 실행하기 전에** 운영 RDS에서 아래 SQL을 실행한다. 이미 자동 번호가 더 큰 값까지 진행됐다면 MySQL은 값을 낮추지 않으므로 그대로 실행해도 된다.

```sql
ALTER TABLE customer AUTO_INCREMENT = 6010001;
ALTER TABLE account AUTO_INCREMENT = 60030001;
ALTER TABLE transfer AUTO_INCREMENT = 60235001;
ALTER TABLE ledger_entry AUTO_INCREMENT = 60500001;
ALTER TABLE auto_transfer AUTO_INCREMENT = 60005001;

INSERT INTO transaction_sequence (seq_date, channel, last_seq, updated_at)
VALUES ('2026-09-01', 'WB', 6000265000, NOW(6))
ON DUPLICATE KEY UPDATE last_seq = GREATEST(last_seq, VALUES(last_seq));

INSERT IGNORE INTO ledger_entry_id_sequence(sequence_id) VALUES (60500000);
```

실행 후 다음 쿼리로 각 `AUTO_INCREMENT`가 시드 종료값보다 큰지 확인한다.

```sql
SELECT TABLE_NAME, AUTO_INCREMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('customer', 'account', 'transfer', 'ledger_entry', 'auto_transfer')
ORDER BY TABLE_NAME;
```

최솟값은 `customer=6010001`, `account=60030001`, `transfer=60235001`, `ledger_entry=60500001`, `auto_transfer=60005001`이다.

### 6-4. 일회성 시드 프로세스 실행

운영 서버에 SSM 또는 SSH로 접속한 뒤 정상 서비스 프로세스와 별개의 터미널에서 실행한다. `<JAR_PATH>`는 실제 배포 JAR의 절대 경로로 바꾼다. 백그라운드 서비스로 등록하지 말고 종료 코드까지 확인할 수 있도록 포그라운드에서 한 번만 실행한다.

```bash
java -jar <JAR_PATH> \
  --spring.profiles.active=prod,phase2-seed \
  --app.phase2-seed.execute=true \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false

echo $?
```

성공 기준은 두 가지다.

- 로그에 `PH-60 minimum seed ready`가 한 번 출력된다.
- 프로세스가 스스로 끝나고 `echo $?`가 `0`을 출력한다.

적재 또는 정합성 검증에 실패하면 시작 예외와 함께 `0`이 아닌 종료 코드로 끝난다. 실패했을 때는 정상 서비스의 배포 설정을 바꾸거나 프로세스를 자동 재시작하지 않는다. 오류 로그와 §6-2의 건수를 보존하고 원인을 확인한 뒤 재실행 여부를 결정한다. 시드 적재는 하나의 트랜잭션이므로 실패하면 롤백되지만, 운영 DB 건수로 실제 상태를 다시 확인해야 한다.

### 6-5. 이미 운영 RDS에 적재한 경우

성공한 PH-60 시드는 같은 운영 RDS에 다시 적재하지 않는다. 고정 PK와 `INSERT IGNORE`를 사용하더라도, 적재 후 배치나 테스트가 계좌 잔액·자동이체 상태를 변경하면 재실행 검증이 실패할 수 있고 APPEND-ONLY 원장을 초기 상태로 되돌릴 수도 없다.

- 같은 시나리오를 처음부터 다시 시험해야 하면 PH-60 적재 전 스냅샷으로 만든 새 RDS 복제본에서 실행한다. 기존 운영 행을 삭제해서 초기화하지 않는다.
- 데이터 규모를 늘려야 하면 PH-60을 반복하지 않고 PH-60b가 별도 ID 대역으로 추가 적재한다. 적재 전에 PH-60b 종료값 밖으로 자동 채번을 다시 이동한다.
- 이전 실행이 실패했다면 오류 원인을 수정한 뒤 §6-2의 전용 대역 건수를 확인한다. 트랜잭션이 모두 롤백되어 건수가 `0`인 것을 확인한 경우에만 같은 PH-60 실행을 다시 시도한다.
- 이전 실행의 성공 여부가 불명확하거나 전용 대역에 일부 행이 남았다면 재실행하지 않고 실행 로그와 DB 상태를 먼저 확인한다.

### 6-6. 적재 후 검수와 정상 배포

1. §6-2의 건수 쿼리를 다시 실행한다. 결과는 고객 `10,000`, 계좌 `30,000`, 이체 `235,000`, 원장 `500,000`, 자동이체 `5,000`이어야 한다.
2. §5의 원장 쌍·잔액·차대변 검수 SQL을 실행한다. 세 결과가 모두 `0`이어야 한다.
3. main 배포 담당은 실행 일시, JAR 버전 또는 커밋 SHA, 시드 적재 소요 시간, 실제 건수와 로그 위치를 §7에 기록한다. P4는 정합성 검수 결과를 같은 표에 기록한다.
4. 정상 서비스는 기존 `prod` 프로필만으로 기동한다. 배포 환경변수와 서비스 실행 명령에 `phase2-seed` 및 `app.phase2-seed.execute`가 없는지 다시 확인한다.
5. 정상 서비스의 헬스 체크와 주요 조회 API를 확인한다. 이후 배포에서는 시드 프로세스를 다시 실행하지 않는다.

## 7. 측정 결과

### 7-1. 로컬 검증

2026-09-30 로컬 MySQL 8.4.10, JDBC 배치 1,000건 기준 리뷰 반영 후 최초 재적재는 **81,217ms(약 1분 21초)**였다.

| 검증 | 결과 |
|---|---:|
| 고객 | 10,000 |
| 계좌 | 30,000 |
| 성공 이체 | 235,000 |
| 원장 | 500,000 |
| 자동이체 | 5,000 |
| 깨진 이체 원장 쌍 | 0 |
| 잔액 불일치 계좌 | 0 |
| 마지막 거래 시각 불일치 계좌 | 0 |
| NULL 또는 기준일 불일치 이체·원장 | 0 |
| 거래 채널·거래유형 불일치 | 0 |
| 개시 전표 차대변 차이 | 0원 |

환경별 저장장치·DB 사양에 따라 적재 시간은 달라질 수 있다.

### 7-2. 운영 RDS 적재 기록

운영 실행자는 적재 직후 아래 표에 실행 일시, 소요 시간, 실제 건수와 정합성 검수 결과를 기록한다. 로컬 결과를 운영 결과로 복사하지 않는다.

| 항목 | 운영 결과 |
|---|---|
| 실행 상태 | 미실행 |
| 실행 일시(KST) | 미실행 |
| 실행자 | 미실행 |
| JAR 버전 또는 커밋 SHA | 미실행 |
| 소요 시간 | 미실행 |
| 고객·계좌·이체·원장·자동이체 건수 | 미실행 |
| 깨진 이체 원장 쌍 | 미실행 |
| 잔액 불일치 계좌 | 미실행 |
| 개시 전표 차대변 차이 | 미실행 |
| 실행 로그 위치 | 미실행 |
