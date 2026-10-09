# PH-60 최소 시드 실행·검증 가이드

## 1. 목적과 실행 방식

PH-60은 현행 `customer`, `account`, `transfer`, `ledger_entry`, `auto_transfer` 구조로 기능·성능 검증용 데이터를 만든다. 계정계·채널계 모델 분리는 후속 작업에서 다룬다.

대량 데이터 생성은 HTTP API로 노출하지 않는다. `phase2-seed` 프로필만으로는 실행되지 않으며, 정기 배포와 분리된 일회성 작업에서 `app.phase2-seed.minimum.execute=true`를 함께 지정해야 한다. 따라서 Swagger UI에 추가되는 엔드포인트는 없다.

실행 환경별 전체 명령은 로컬 시험은 §1-1, 운영 RDS 적재는 §6-4를 따른다. 일반 로컬 기동에서는 `DemoDataLoader`가 실행되지만, `phase2-seed`와 일회성 실행 플래그가 함께 활성화되면 방어 코드가 데모 시드와 기동 시 영업일 보정을 차단한다. PH-60 전용 실행은 환경을 단순하게 유지하기 위해 아래와 같이 `phase2-seed`만 사용한다.

애플리케이션 로그의 `PH-60 minimum seed ready`가 출력되면 적재와 자체 정합성 검증이 끝난 것이며, 일회성 프로세스는 성공 코드 `0`으로 자동 종료된다. 적재 또는 검증에 실패하면 애플리케이션 시작이 실패해 0이 아닌 종료 코드를 반환한다.

```text
PH-60 minimum seed ready: customers=10000, accounts=30000, transfers=235000, ledgerEntries=500000, autoTransfers=5000, elapsed=<소요 밀리초>ms
```

### 1-1. 기존 로컬 데이터를 보존하고 직접 시험하기

기존 `minicore` 데이터베이스와 분리된 `minicore_ph60` 데이터베이스에서 시험한다.

1. 로컬 MySQL을 실행한다. 일회성 토큰 저장소가 MySQL로 바뀌어(#580) Redis는 필요 없다.

```bash
docker compose up -d minicore-mysql
```

2. DBeaver·DataGrip 또는 MySQL 클라이언트로 로컬 MySQL에 접속해 시험용 데이터베이스를 만든다.

```sql
CREATE DATABASE IF NOT EXISTS minicore_ph60
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

3. 프로젝트 루트에서 사용하는 터미널에 맞는 명령 하나를 실행한다. `phase2-seed`만 활성화하고 로컬 DB 접속값을 명시한다. Flyway가 빈 데이터베이스에 스키마와 기준 상품을 먼저 넣은 뒤 PH-60 시드를 적재한다. DevTools 재시작을 끄므로 적재 완료 후 정상 종료 코드 `0`을 받을 수 있다.

Git Bash:

```bash
./gradlew bootRun --args='--spring.profiles.active=phase2-seed --app.phase2-seed.minimum.execute=true --spring.datasource.url=jdbc:mysql://localhost:3306/minicore_ph60?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true --spring.datasource.username=root --spring.datasource.password=localpw --spring.jpa.hibernate.ddl-auto=validate --spring.data.redis.host=localhost --spring.main.web-application-type=none --spring.task.scheduling.enabled=false --spring.devtools.restart.enabled=false'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=phase2-seed --app.phase2-seed.minimum.execute=true --spring.datasource.url=jdbc:mysql://localhost:3306/minicore_ph60?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true --spring.datasource.username=root --spring.datasource.password=localpw --spring.jpa.hibernate.ddl-auto=validate --spring.data.redis.host=localhost --spring.main.web-application-type=none --spring.task.scheduling.enabled=false --spring.devtools.restart.enabled=false"
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
| 만기 임박 | 정기예금 100개, P2 합의 범위인 2026-10-24~11-22 분포 |
| `MATURED` | 0개(PH-60b 범위) |
| 기준 시각 | `2026-09-01 00:00:00` KST |
| 거래일 | `2026-09-01` (`transfer`·`ledger_entry`·GL 공통) |
| 인증용 평문 | 로그인·계좌 공통 `1234`(BCrypt 저장, 계좌비밀번호 숫자 4자리 규칙 충족) |

생성 공식과 기준 시각이 코드에 고정되어 있어 같은 버전에서는 고객·계좌·이체·원장·자동이체 값과 분포가 동일하다.

만기 임박 범위는 PH-15 완료 예정일인 10/23에는 100개 모두 만기 전 상태를 유지하면서, 배포 이후 실제 COB 흐름에서 순차적인 `ACTIVE → MATURED` 전이를 확인할 수 있도록 P2와 합의했다. 첫 만기일인 10/24는 토요일이므로 실제 전이 시점은 PH-15의 `BusinessDateProvider` 영업일 판정과 COB 실행 일정에 따른다.

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
- 위 `860` 계좌번호는 이미 적재된 PH-60 전용 대역이다. PH-60b 신규 계좌는 이 규칙을 복사하지 않고 실제 당행 코드 `088`, 상품별 prefix와 `account_number_sequence` 예약 대역을 사용한다([ph60b_bulk_seed.md](ph60b_bulk_seed.md) §3).
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

운영 RDS에는 로컬 덤프를 옮기지 않는다. **main 배포와 별도로 운영 서버에서 배포된 Docker 이미지를 일회성 컨테이너로 한 번만 실행해 운영 RDS에 직접 적재한다.** 이 컨테이너는 HTTP 서버를 실행하지 않고, `phase2-seed` 프로필이 `SchedulingConfig`를 제외하므로 스케줄러도 등록하지 않는다. 적재와 자체 검증을 마치면 자동 삭제된다.

자동 종료되는 것은 일회성 시드 프로세스뿐이다. 적재가 성공하면 데이터는 운영 RDS에 커밋되어 그대로 유지되므로, 이후 정상 `prod` 서버를 대상으로 수행하는 TPS·동시성 등 성능 테스트에는 지장이 없다.

정상 서비스의 배포 설정에는 `phase2-seed` 프로필과 `app.phase2-seed.minimum.execute` 플래그를 추가하지 않는다. 이 두 값은 아래 일회성 명령에만 넣는다.

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
2. `/home/ubuntu/corebank/docker-compose.yml`의 `corebank-server.image`가 main에 배포한 이미지 태그인지 확인한다.
3. 같은 Compose 서비스에 운영 RDS·Flyway·Redis 접속 환경변수가 들어 있는지 확인한다. 일회성 컨테이너는 이 값을 그대로 상속하므로 DB 비밀번호를 명령행에 직접 적지 않는다.
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

운영 서버에 SSM 또는 SSH로 접속한 뒤 배포 Compose 파일이 있는 디렉터리에서 실행한다. `docker compose run`은 현재 실행 중인 정상 서비스 컨테이너를 교체하지 않고, 같은 이미지·환경변수·네트워크를 상속한 일회성 컨테이너를 포그라운드로 실행한다. 이미지의 `ENTRYPOINT`가 `java -jar app.jar`이므로 아래 값들은 애플리케이션 인자로 전달된다.

```bash
cd /home/ubuntu/corebank

docker compose run --rm corebank-server \
  --spring.profiles.active=prod,phase2-seed \
  --app.phase2-seed.minimum.execute=true \
  --spring.main.web-application-type=none \
  2>&1 | tee "$HOME/ph60-seed-$(date +%Y%m%d-%H%M%S).log"

echo "exit=${PIPESTATUS[0]}"
```

- **로그 파일**: `/home/ubuntu/corebank`는 root 소유라 `ubuntu` 계정으로는 파일을 쓸 수 없다. 로그는 `$HOME`에 남기고, 이 경로를 §7에 기록한다.
- **종료 코드**: `tee`로 파이프를 걸면 `$?`는 `tee`의 결과가 된다. 시드 프로세스의 종료 코드는 바로 다음 줄에서 `${PIPESTATUS[0]}`로 읽는다.
- **스케줄러 격리**: `phase2-seed` 프로필에서는 `SchedulingConfig`가 등록되지 않으므로 일회성 컨테이너에서 정기 배치가 실행되지 않는다.

성공 기준은 두 가지다.

- 로그에 `PH-60 minimum seed ready`가 한 번 출력된다.
- 프로세스가 스스로 끝나고 `exit=0`이 출력된다.

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
3. main 배포 담당은 실행 일시, Docker 이미지 태그 또는 커밋 SHA, 시드 적재 소요 시간, 실제 건수와 로그 위치를 §7에 기록한다. P4는 정합성 검수 결과를 같은 표에 기록한다.
4. 정상 서비스는 기존 `prod` 프로필만으로 기동한다. 배포 환경변수와 서비스 실행 명령에 `phase2-seed` 및 `app.phase2-seed.minimum.execute`가 없는지 다시 확인한다.
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
| 실행 상태 | 성공 (종료 코드 `0`) |
| 실행 일시(KST) | 2026-09-30 23:15:08 ~ 23:17:42 |
| 실행자 | P4 (main 배포 담당) |
| Docker 이미지 태그 또는 커밋 SHA | `421cee06e13c73235cfeff0beb70fa4727467d78` (릴리스 #522) |
| 소요 시간 | 적재 127,623ms (약 2분 8초). Spring 기동 포함 전체 2분 34초 |
| 고객·계좌·이체·원장·자동이체 건수 | 10,000 · 30,000 · 235,000 · 500,000 · 5,000 |
| 깨진 이체 원장 쌍 | 0 |
| 잔액 불일치 계좌 | 0 |
| 개시 전표 차대변 차이 | 0원 |
| 실행 로그 위치 | 파일로 남기지 못했다(아래 특이사항 1). 핵심 로그는 아래에 옮겨 적는다 |

```
2026-09-30 23:15:11.481 The following 2 profiles are active: "prod", "phase2-seed"
2026-09-30 23:15:19.298 Schema `minicore` is up to date. No migration necessary.
2026-09-30 23:15:33.745 Started CorebankApplication in 23.841 seconds
2026-09-30 23:17:41.426 PH-60 minimum seed ready: customers=10000, accounts=30000, transfers=235000, ledgerEntries=500000, autoTransfers=5000, elapsed=127623ms
exit=0
```

운영 환경은 EC2 1대(메모리 약 1.9GB, 적재 직전 available 904MB)와 RDS MySQL 8.4.10 `db.t4g.micro`다. 힙 옵션은 주지 않았다.

#### P4 추가 검수

§5의 세 가지에 더해 리뷰(#517)에서 고친 값이 운영 데이터에 들어갔는지 확인했다. 대역은 이체 `60000001~60235000`, 원장 `60000001~60500000`, 자동이체 `60000001~60005000`이다.

| 검수 | 결과 |
|---|---:|
| 원장 거래유형이 `IMMEDIATE_TRANSFER`·`OPENING`이 아닌 행 | 0 |
| 이체 채널이 `WB`가 아니거나 거래번호 채널 자리와 다른 이체 | 0 |
| 원장 채널과 거래번호 채널 자리가 다른 행 | 0 |
| 원장 `OPENING` 합계 | 1,060,000,000,000원 |
| 개시 전표 예수금(`20100`) 대변 | 1,060,000,000,000원 (위와 같음) |
| 발생일과 거래일이 다른 이체 원장 | 0 |
| 시작일이 등록일 이전이거나 다음 실행일이 시작일보다 빠른 자동이체 | 0 |

#### 적재 전후 확인

적재 시작·종료와 스냅샷 시각 외에는 분 단위 근사다.

| 시각(KST) | 확인 | 결과 |
|---|---|---|
| 23:02 | 릴리스 #522 배포 | 성공. Flyway `202609281930`·`202609291400`·`202609300903`·`seed holiday` 적용 |
| 23:08 | 적재 전 RDS 수동 스냅샷 | `corebank-before-ph60-20260930` (727.5MiB) |
| 23:10 | §6-2 전용 대역 건수 | 5개 테이블 모두 0 |
| 23:11 | §6-3 자동 채번 이동 | 7개 문장 성공. 이동 전 `AUTO_INCREMENT`는 account 23 · customer 7 · ledger_entry 10 · transfer 1 · auto_transfer 1 |
| 23:13 | 자동 채번 확인 | `SET SESSION information_schema_stats_expiry = 0;` 실행 후 조회(DataGrip 실행 이력으로 순서 확인). §6-3 최솟값과 모두 일치. `ledger_entry_id_sequence`는 60,500,001 |
| 23:18 | 정상 서비스 | `corebank-server` healthy(재시작 없음), health `UP` |
| 23:20 | 적재 후 RDS 수동 스냅샷 | `corebank-after-ph60-20260930` (1.29GiB). 10/1 00:10 배치 이전 상태 |
| 23:31 | 시드 계정 로그인·전체계좌조회 | `ph60_user_00001` 로그인 성공. 계좌 `860100000001`·`860200000001`·`860300000001` 반환 |
| 10/1 00:10 | 적재 후 첫 이체 배치 | 시드 자동이체 179건 모두 `SUCCESS` (아래 "적재 후 첫 배치") |
| 10/2 14:48 | 자동 채번 사후 확인 | 10/1 이후 새로 생긴 이체 1,850건은 `60235001~60236850`, 원장 3,358행은 `60500001~60503358`. 둘 다 시드 종료값 바로 다음 번호부터 시작했고 원장 번호 중복은 0 |

#### 특이사항

1. **실행 로그를 파일로 남기지 못했다.** `/home/ubuntu/corebank`는 배포 스크립트가 root로 만든 디렉터리라 `ubuntu` 계정으로 `tee ph60-seed.log`를 쓰면 `Permission denied`가 난다. 적재 자체에는 영향이 없다. §6-4 명령을 `$HOME`에 로그를 남기도록 고쳤다.
2. **운영 RDS는 자동 백업이 꺼져 있다.** 특정 시점 복원을 쓸 수 없어 되돌리기 수단은 위 수동 스냅샷 두 개뿐이다. `corebank-after-ph60-20260930`은 PH-30 베이스라인 측정(10/8)이 끝날 때까지 지우지 않는다.
3. **자동 채번 확인 쿼리 앞에 `SET SESSION information_schema_stats_expiry = 0;`을 실행했다.** `information_schema.TABLES`의 `AUTO_INCREMENT`는 캐시된 값이라 방금 바꾼 값 대신 이전 값이 보일 수 있어서다. §6-3 확인 쿼리에는 이 줄이 없다. 이번에 캐시 문제를 실제로 겪지는 않았고, 적재 후 새로 생긴 이체·원장 번호로도 이동이 반영됐음을 확인했다(위 표 10/2 행).
4. **일회성 컨테이너에서도 스케줄러는 등록된다.** `--spring.task.scheduling.enabled=false`로는 `@EnableScheduling`이 꺼지지 않아, 영업일 맞추기를 뺀 나머지(매시 정각 멱등키 정리, 00:10 이체 배치, 02:00 대사)는 그 시각에 컨테이너가 떠 있으면 실행된다. 이번에는 23:15~23:17에 실행해 겹치지 않았다. §6-1의 "스케줄러도 실행하지 않으며"를 바로잡고 §6-4에 실행 시각 기준을 넣었다.
5. **배포된 프론트 메인 화면에는 시드 계좌가 보이지 않는다.** 대표계좌 목록이 고정 예시 데이터를 쓰기 때문이며 서버 응답은 정상이다. 은행코드 `860`과는 무관하다.
6. **은행코드 `860` 계좌 사이의 자동이체는 배치에서 성공한다.** 아래 첫 배치에서 179건이 모두 성공했다. 즉시이체 경로는 확인하지 않았다.

#### 적재 후 첫 배치

시드 자동이체 중 다음 실행일이 10/1인 179건이 적재 직후인 10/1 00:10 배치에서 처음 실행됐다. 적재 후 스냅샷(`corebank-after-ph60-20260930`)은 이 배치 이전 상태다.

| 확인 | 결과 |
|---|---|
| 배치 로그 | 자동이체 배치 00:10:00.691 시작 ~ 00:10:16.444 종료. 이어서 재확정·예약이체·예약이체 재확정 배치가 00:10:16.583에 종료 |
| 실행 결과(`auto_transfer_execution`) | `SUCCESS` 179건, 실패 0건. 실행 시각 00:10:01.009 ~ 00:10:16.399 (약 15.4초) |
| 다음 실행일 | 10/1에 남은 건 없음. 2026-11-01 60건 · 2027-01-01 60건 · 2027-04-01 59건, 모두 `NORMAL` (주기 1·3·6개월) |
| 배치 후 잔액 불일치 계좌(§5 (2)와 같은 쿼리) | 0 |
