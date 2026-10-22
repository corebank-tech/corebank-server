# 정보계 마트 설계 (PH-75)

> **정본입니다.** 정보계 마트 스키마, 적재 규칙, 정합성 대조, 조회 API 계약은 이 문서를 따릅니다.
> 바꾸려면 P3과 P5가 이 문서를 먼저 고칩니다.
>
> **1차 대상**: P5 [PH-76](tasks.md#ph-76-정보계-etl)(ETL)·[PH-77](tasks.md#ph-77-정보계-조회-api)(조회 API), FE ADM-03.
> 용어(영업일·거래일)는 [glossary.md](glossary.md)를 따릅니다. 인프라 배치는 [D-25](README.md#d-25--927--리드-제안1020-팀-확정)입니다.
>
> 마트는 온프레미스 MySQL에 있습니다. 그래서 이 레포의 Flyway·[schema_reference.md](../schema_reference.md)·ERD에는 넣지 않고, DDL은 §2가 정본입니다.

---

## 1. 결정 요약

| # | 결정 | 근거 |
|---|---|---|
| 1 | 잔액 마트 한 행 = **계좌 × 기준일** 일별 스냅샷. 정합성은 **원장 대조 + GL `20100` 대조** 둘 다 | Kimball 기간 스냅샷(은행 계좌 잔액이 대표 예), OFSAA 계좌 단위 요약, BCBS 239 원칙 3(회계 데이터와 대조) |
| 2 | 기준일 = **거래일(`trade_date`)**, 영업일에만 행 | GL 집계 기준이 거래일. Fineract `m_trial_balance`·`JRNL_AGG`, OFSAA `FIC_MIS_DATE` 모두 업무 기준일 |
| 3 | `trade_date`가 NULL이면 `DATE(occurred_at)`이 영업일이면 그날, 아니면 다음 영업일로 대체 | [#515](https://github.com/corebank-tech/corebank-server/issues/515) 기존 행 채우기 규칙과 같음. 채우기 전후에 재적재해도 날짜가 같다 |
| 4 | 가입·해지 마트 = **이벤트 1건 1행**, 추가 전용 | PH-76 "전일 확정분 증분, 재적재 가능". Kimball 거래 단위 |
| 5 | 해지 기준일 = **계좌 해지 때 저장한 `closed_trade_date`** | 원장 유무와 관계없이 해지의 논리 영업일이 하나로 고정된다. 해지 출금 원장의 거래일도 이 값과 같아야 한다(§3-0) |
| 6 | 가명 키 = **HMAC-SHA256(키, `종류:id`)** 16진수(`account:5`, `customer:5`). 키는 온프레미스 ETL에만 둔다 | `customer_id`는 연속 BIGINT라 키 없는 해시는 전수 대입으로 풀린다. 종류 접두어가 없으면 같은 숫자의 계좌 키와 고객 키가 같아진다. 상태 없는 변환이라 재적재해도 같은 값 |
| 7 | 응답 `asOf` = 첫 적재일부터 **빠짐없이 `COMPLETED`인 마지막 기준일(DATE)** | 하루 한 번 마감분을 적재하므로 "어디까지 빠짐없이 반영됐는가"의 답이 날짜 하나다. 이벤트 마트는 날짜별 추가라 중간에 실패한 날이 있으면 뒤 날짜의 누계가 비므로 가장 늦은 `COMPLETED`를 쓰지 않는다(BCBS 239 원칙 4 완전성). ETL이 적재할 때 계산해 `mart_load_batch.as_of_date`에 남기고 API는 읽기만 한다 — 영업일 판정에 `holiday`가 필요한데 마트에는 달력이 없다. 적재 완료 시각은 운영용 |
| 8 | PH-77 지표는 **2개**(일별 수신 잔액 추이, 상품별 가입·해지). 채널별 거래량은 뺀다 | 마트 2종과 맞춘다. ADM-03(REQ-ADM-030)이 쓰지 않는다. [D-28](README.md#d-28--1015--p3) |
| 9 | ETL 시작 신호 = 원천의 **`business_date_cutoff(D, CLOSED)`** | 영업일 전환은 D 거래를 모두 drain한 뒤 cutoff를 같은 트랜잭션으로 남긴다(§3-0). 관측용 `batch_execution_history`는 신호로 쓰지 않는다 |

---

## 2. 테이블

테이블은 셋입니다. `mart_load_batch`는 적재 기록이라 "마트 2종"에 세지 않습니다.

```sql
-- ① 일별 계좌잔액 (계좌 × 기준일, 일별 스냅샷)
CREATE TABLE mart_daily_account_balance (
    base_date      DATE                          NOT NULL COMMENT '거래일 기준 마감일 = as-of',
    account_key    CHAR(64) CHARACTER SET ascii  NOT NULL COMMENT 'HMAC-SHA256(account:{account_id}) hex',
    customer_key   CHAR(64) CHARACTER SET ascii  NOT NULL COMMENT 'HMAC-SHA256(customer:{customer_id}) hex',
    account_type   VARCHAR(24)                   NOT NULL COMMENT 'DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS',
    product_id     BIGINT                            NULL COMMENT '입출금계좌는 NULL',
    product_group  VARCHAR(12)                       NULL COMMENT 'SAVINGS / DEPOSIT. 입출금계좌는 NULL',
    balance        BIGINT                        NOT NULL COMMENT 'base_date 마감 잔액(원)',
    load_batch_id  BIGINT                        NOT NULL,
    PRIMARY KEY (base_date, account_key),
    KEY ix_mdab_product (base_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일별 계좌잔액 마트';

-- ② 상품 가입·해지 이벤트 (이벤트 1건 = 1행, 추가 전용)
CREATE TABLE mart_product_subscription_event (
    base_date      DATE                          NOT NULL COMMENT '이벤트 거래일',
    event_type     VARCHAR(12)                   NOT NULL COMMENT 'SUBSCRIBE / CLOSE',
    account_key    CHAR(64) CHARACTER SET ascii  NOT NULL,
    customer_key   CHAR(64) CHARACTER SET ascii  NOT NULL,
    product_id     BIGINT                        NOT NULL,
    product_code   VARCHAR(20)                   NOT NULL COMMENT '적재 시점 스냅샷',
    product_name   VARCHAR(100)                  NOT NULL COMMENT '적재 시점 스냅샷',
    product_group  VARCHAR(12)                   NOT NULL,
    load_batch_id  BIGINT                        NOT NULL,
    PRIMARY KEY (base_date, event_type, account_key),
    UNIQUE KEY uk_mpse_account_event (account_key, event_type),
    KEY ix_mpse_product (base_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품 가입·해지 이벤트 마트';

-- ③ 적재 기록 (기준일 1회 적재 = 1행. asOf 와 대조 결과의 출처)
CREATE TABLE mart_load_batch (
    load_batch_id          BIGINT       NOT NULL AUTO_INCREMENT,
    base_date              DATE         NOT NULL,
    status                 VARCHAR(12)  NOT NULL COMMENT 'RUNNING / COMPLETED / FAILED / REPLACED',
    started_at             DATETIME(6)  NOT NULL,
    completed_at           DATETIME(6)      NULL COMMENT '운영용. API 로 내보내지 않는다',
    balance_row_count      BIGINT           NULL,
    event_row_count        BIGINT           NULL,
    mart_balance_total     BIGINT           NULL COMMENT 'SUM(mart_daily_account_balance.balance)',
    ledger_total           BIGINT           NULL COMMENT '원장 입금 합계 - 출금 합계 (td <= base_date)',
    gl_compare_from_date   DATE             NULL COMMENT '직전 COMPLETED base_date (D′). 첫 적재면 NULL → GL 대조 생략',
    mart_balance_movement  BIGINT           NULL COMMENT 'mart_balance_total(D) - mart_balance_total(D′)',
    gl_deposit_movement    BIGINT           NULL COMMENT 'GL 20100 (대변 합계 - 차변 합계), D′ < trade_date <= D',
    as_of_date             DATE             NULL COMMENT 'COMPLETED 로 커밋할 때 계산한 연속 워터마크(§5-4). API 의 asOf 출처',
    failure_reason         VARCHAR(200)     NULL,
    PRIMARY KEY (load_batch_id),
    KEY ix_mlb_date_status (base_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정보계 적재 기록';
```

- **PII 0건.** 계좌번호·이름·원천 ID를 싣지 않습니다. 고객과 계좌는 가명 키로만 식별합니다.
- **as-of 필수.** 두 마트 모두 `base_date`가 NOT NULL이고 기본키 첫 컬럼입니다. 재적재 범위가 기본키 앞부분과 같습니다.
- **`uk_mpse_account_event`.** 계좌 하나에 가입·해지는 각각 한 번입니다(`product_subscription.uk_sub_account`). 같은 이벤트가 다른 날짜로 두 번 들어오면 DB가 막습니다. Fineract는 이 제약 없이 애플리케이션이 거르지만, `gl_voucher`와 같은 이유로 우리는 DB에서 막습니다([tasks.md PH-24](tasks.md#ph-24-gl-분개-서비스--이체-기표-훅--glapi)).
- **잔액 행의 범위.** 첫 원장 거래일 ≤ D이고, 해지 기준일(결정 5) ≥ D인 계좌입니다. 해지일에는 0원 행이 있고 그 다음 날부터 없습니다. 원장만으로 정해지므로 재적재해도 같습니다.
- **넣지 않은 것.** 계좌 상태(현재값이라 과거 날짜를 재적재하면 틀린다), 해지 유형(P2 코드가 아직 없다), 금액·일별 증감(2차 지표가 안 쓴다). 필요하면 컬럼을 더하고 재적재합니다.
- **Fineract와 다른 점.** 금액은 `BIGINT`(AGENTS.md §3-5), 상품 코드·이름은 스냅샷(마트 DB에 상품 마스터가 없다), 잔액 행은 조밀(`m_trial_balance`는 분개가 있는 날만), 적재 기록은 상태를 직접 가진다(Spring Batch 미도입, [ADR-0005](../adr/0005-no-spring-batch-mq-api-gateway.md)).

---

## 3. 적재 규칙

### 3-0. 원천 영업일 마감 계약 (PH-76 자동 적재의 선행)

`batch_execution_history`는 스텝 하나가 끝날 때 `REQUIRES_NEW`로 남기는 관측 기록이며, 기록 저장 실패도 COB를 멈추지 않습니다. 따라서 행 하나가 보인다는 사실은 D 거래가 더는 커밋되지 않는다는 보장이 아닙니다. ETL은 이 테이블을 시작 신호나 체크포인트로 쓰지 않습니다.

자동 적재 전에 원천 DB에 아래 계약을 구현합니다(#556, 원천 스키마·거래 경로 변경은 별도 PR에서 Flyway·`schema_reference.md`·ERD를 함께 갱신).

1. date-fence는 **날짜와 무관한 단일 gate** 하나입니다. 날짜별 fence는 두지 않습니다. 어느 날짜의 fence를 잡을지 정하려면 D를 먼저 읽어야 해서 순환하기 때문입니다.
2. 모든 원장 생성, 상품가입, 계좌해지 경로는 **gate 공유 락 획득과 D 읽기를 한 연산으로** 수행합니다(예: `acquireSharedAndReadBusinessDate()`). 이 연산 밖에서 `BusinessDateProvider.today()`로 거래일을 따로 읽지 않습니다. 읽은 거래일은 원장과 이벤트 원천 컬럼에 같이 저장합니다. 거래일을 저장하는 호출자는 같은 트랜잭션에서 이 연산을 직접 부릅니다(같은 트랜잭션의 재호출은 첫 값을 돌려줍니다). 기표를 다른 서비스에 맡겨 직접 부를 수 없을 때만 그 기표 결과의 `tradeDate`를 씁니다.
3. 이 연산은 원장·이벤트를 쓰는 원천 트랜잭션 **안에서** 호출하고, gate는 그 트랜잭션의 commit/rollback까지 유지합니다. 트랜잭션 밖에서 거래일을 읽던 이체 경로(`TransferExecutionService`, `InterbankTransferService`, `InterbankSettlementService`)도 #662에서 기표·확정 트랜잭션 안으로 옮겼습니다.
4. gate는 원장·이벤트를 쓰는 가장 안쪽 트랜잭션에서만 잡습니다. gate를 잡은 트랜잭션이 `REQUIRES_NEW`로 연 트랜잭션에서 gate를 다시 잡지 않습니다. 바깥이 공유 락을 쥔 채 COB 배타 락이 기다리면 안쪽 공유 요청이 그 뒤에 서고, 바깥은 안쪽을 애플리케이션에서 기다리므로 DB가 교착을 감지하지 못합니다. 이런 중첩 구조가 이미 있습니다(자동이체·예약이체 배치의 `completeProcessing` → `TransferExecutionService.postLedger`). 지금은 바깥이 영업일을 읽지 않아 해당하지 않습니다. 락 순서(날짜 락 → 계좌 락)를 어기면 전환이 실패할 수 있고, 전환이 실패하면 그 영업일의 cutoff가 남지 않으므로 ETL도 시작하지 않습니다. 락 순서 위반 시 교착 희생자와 재시도 정책은 ADR-0008 결과를 따릅니다.
5. COB 전환은 같은 gate의 배타 락을 잡아 이미 D를 읽은 거래를 모두 commit/rollback할 때까지 기다립니다. 그 상태에서 `BUSINESS_DATE`를 다음 영업일로 전환하고, 원천 `business_date_cutoff`에 `(base_date=D, status=CLOSED, closed_at)`을 **같은 트랜잭션으로** 기록한 뒤 락을 풉니다. 영업일을 바꾸는 경로는 COB 전환(`advanceFrom`)과 달력 따라잡기(`catchUpTo`) 둘이고, 둘 다 같은 트랜잭션으로 cutoff를 남깁니다. `catchUpTo`가 영업일을 건너뛰면 떠나는 날과 건너뛴 영업일 모두 남기고, 건너뛴 날은 원장이 비어 있습니다. 도입 이전 날짜에는 행이 없고, 도입 이후의 모든 영업일에는 행이 있습니다. 가동 중단으로 건너뛴 영업일도 영업일 전환과 같은 트랜잭션에서 남깁니다. 건너뛴 날의 CLOSED도 의미가 참인 이유는, 따라잡기 UPDATE가 떠나는 날의 배타 락으로 진행 중 거래를 기다리고, 중간 날들은 서버가 멈춰 거래가 없었으며, 이후 거래는 모두 새 영업일을 읽기 때문입니다.
6. 레플리카는 `replica_preserve_commit_order = ON`으로 이 커밋 순서를 보존합니다. ETL은 레플리카에서 `business_date_cutoff.status = 'CLOSED'`인 D만 시작합니다. cutoff가 없거나 실패면 ETL도 시작하지 않습니다.

이 계약으로 cutoff보다 앞서 커밋된 행은 모두 D 이하이고, cutoff 뒤에 커밋되는 새 거래는 다음 영업일입니다. 단순 대기 시간, COB 스텝 상태, 실행 기록 행으로 이를 대체하지 않습니다.

### 3-1. 실행 단위와 시작 조건

- 한 번 실행에 기준일 D 하나를 적재합니다. `mart_load_batch`에 한 행이 생깁니다.
- 시작 조건: 레플리카의 `business_date_cutoff`에 `base_date = D`, `status = 'CLOSED'` 행이 있고 D가 아직 `COMPLETED`가 아니다(§3-0). 단, 아래 경계일보다 앞선 D는 cutoff 행 없이 시작합니다. 밀린 날짜는 오래된 것부터 하나씩 적재합니다. 한 날짜가 실패해도 다음 날짜로 넘어가고, 실패한 날짜는 재적재 대상으로 남습니다. 공개 기준(`asOf`)은 실패한 날짜 앞에 머물고(§5-4), 그 날짜를 재적재하면 이미 적재된 뒤 날짜까지 한 번에 따라잡습니다.
- **cutoff 경계일.** 운영에서 #556과 #662가 둘 다 배포된 뒤 첫 COB가 마감한 영업일 D입니다(#668 설정값). 경계일보다 앞선 D는 cutoff 없이 적재와 재적재를 허용합니다. 이미 지난 날이라 D를 읽은 거래가 늦게 커밋될 일이 없기 때문입니다. 경계일 이후의 D는 cutoff가 없으면 COB 전환이 실패한 것으로 보고 시작하지 않습니다.
    - 경계일은 `MIN(base_date)`로 계산하지 않고 ETL 설정값으로 고정합니다. 행이 없는 날짜가 경계일 이전 날짜인지 전환이 실패한 날짜인지는 행만 봐서는 구분할 수 없습니다.
    - 경계일 이전 날짜의 cutoff 행은 만들지 않습니다. gate가 실제로 기다린 적 없는 날에 CLOSED 신호를 남기게 되기 때문입니다. Fineract도 COB 마감 표시(`m_loan.last_closed_business_date`)를 추가하면서 과거 값을 채우지 않았고, 표시가 없는 계좌는 다음 COB부터 합류시키되 밀린 날짜 따라잡기에서는 뺍니다(§8).
- 이미 `COMPLETED`인 D는 자동으로 다시 적재하지 않고, 수동 재적재 모드(예: `--rebuild D`, 인자 이름은 P5가 정한다)로만 다시 만듭니다. ① 경계일 이후의 D는 `business_date_cutoff(D, CLOSED)`가 여전히 필수입니다. ② 기존 상태와 무관하게 T1에서 새 `RUNNING` 배치를 만듭니다. ③ T2에서 D의 마트 행을 원자적으로 교체하고 ④ 이전 배치를 `REPLACED`로 바꾸고 ⑤ `as_of_date`를 다시 계산합니다(§3-2, §5-4). 키 교체(§3-3), 컬럼 추가(§2), 해지 이벤트 교체(§7-5)가 이 경로를 씁니다. 실패하면 T2가 롤백되어 이전 `COMPLETED` 배치와 마트 행이 그대로 남습니다.
- 최초 적재는 원장 첫 거래일(PH-60b, 9/1)부터 마지막 COB 완료일까지 같은 방식으로 채웁니다. 경계일 이전 날짜는 cutoff 없이 적재합니다.
- 10/27 복제 시작 전(덤프 기간)에는 레플리카가 없으므로 D를 실행 인자로 받습니다.
- 기간 적재(`--from`·`--to`)는 위 순서대로 영업일만 돌고(주말은 요일, 공휴일은 `holiday`), 끝나면 실패한 날짜를 모아 보고합니다.
- 같은 기준일은 한 프로세스만 적재합니다. MySQL `GET_LOCK`을 기준일별로 잡고, 프로세스가 죽으면 연결이 끊기며 풀립니다. `GET_LOCK`은 연결 단위라 실행하는 동안 락 전용 연결 하나를 열어 둡니다(포트 호출마다 여는 연결로 잡으면 T1이 끝날 때 풀립니다).
- 레플리카에서 읽은 잔액이 0건이면 `FAILED`로 끝내고 마트는 건드리지 않습니다. 복제가 늦을 때 정상 스냅샷을 지우지 않기 위해서입니다. 이벤트는 0건이어도 정상입니다.

### 3-2. 트랜잭션 경계

| 단계 | 트랜잭션 | 하는 일 |
|---|---|---|
| ① | T1 | 6시간 넘게 `RUNNING`인 기록을 `FAILED`로 정리(비정상 종료로 본다. 은행 앱 배치 락 `BatchExecutionLockPersistenceAdapter`와 같은 기준) → `mart_load_batch`에 `(D, RUNNING)`을 넣고 커밋. 이 ID가 `load_batch_id` |
| ② | T2 | D의 기존 마트 행 삭제(재적재일 때) → 새 행 삽입 → §4 대조 → 이전 배치 `REPLACED`, 이번 배치 `COMPLETED`와 `as_of_date`(§5-4) → **한 번에 커밋** |
| ③ | T3 | T2가 롤백되면 `FAILED`·대조 값·`failure_reason`을 기록 |

- 하루치가 계좌 약 15만 행이라 한 트랜잭션에 담습니다. 실패하면 롤백으로 정리됩니다. Fineract `JRNL_AGG`는 청크마다 커밋해서 실패 시 `deleteByJobExecutionId`로 지웁니다.
- InnoDB MVCC로 조회 API는 T2 커밋 전까지 이전 데이터를 봅니다. 재적재 중에 화면이 비지 않습니다.
- **구현 방식(P5 확정, #644).** 별도 소스셋 jar(`info-etl.jar`), Spring 없이 JDBC, 헥사고날 구조입니다. 어댑터가 포트 호출 하나마다 연결과 트랜잭션 하나를 소유하므로 위 표의 단계 하나가 포트 호출 하나입니다.

### 3-3. 산출 규칙 (원천은 레플리카)

공통으로 원장 행의 거래일은 `td = COALESCE(trade_date, NEXT_BUSINESS_DAY_OR_SELF(DATE(occurred_at)))`입니다(결정 3). `NEXT_BUSINESS_DAY_OR_SELF`는 SQL 내장 함수가 아닌 의사 함수이며, PH-76 구현은 `BusinessDateProviderService`와 동일하게 주말과 `holiday` 테이블을 조회해 반드시 이를 적용합니다. `occurred_at`에는 상한을 걸지 않고 `td <= D`만으로 거릅니다. 거래일과 발생일의 간격은 일정하지 않아서([schema_reference.md](../schema_reference.md) `ledger_entry`), COB가 멈춘 동안에는 거래일이 D인 원장의 발생일이 며칠씩 뒤로 밀릴 수 있기 때문입니다. 거래일이 D 이하인 원장은 모두 cutoff 전에 커밋되므로(§3-0) 뒤늦게 도착하는 원장을 위한 재적재 규칙은 따로 두지 않습니다. 과거 날짜 재적재가 D 이후 파티션까지 읽는 비용은 §3-5 증분 방식에서 줄입니다.

**잔액**

```sql
WITH le AS (
  SELECT account_id, balance_after, occurred_at, ledger_entry_id,
         COALESCE(trade_date, NEXT_BUSINESS_DAY_OR_SELF(DATE(occurred_at))) AS td
  FROM ledger_entry
), last_entry AS (
  SELECT account_id, balance_after,
         ROW_NUMBER() OVER (PARTITION BY account_id
                            ORDER BY occurred_at DESC, ledger_entry_id DESC) AS rn
  FROM le WHERE td <= :d
), close_td AS (
  -- 해지 계좌의 원장 전체를 본다
  SELECT e.account_id,
         MAX(COALESCE(e.trade_date, NEXT_BUSINESS_DAY_OR_SELF(DATE(e.occurred_at)))) AS last_ledger_date
  FROM ledger_entry e
  JOIN account a ON a.account_id = e.account_id AND a.status = 'CLOSED'
  GROUP BY e.account_id
)
SELECT a.account_id, a.customer_id, a.account_type, a.product_id, p.product_group, l.balance_after,
       a.closed_trade_date, c.last_ledger_date
FROM last_entry l
JOIN account a  ON a.account_id = l.account_id
LEFT JOIN product p ON p.product_id = a.product_id
LEFT JOIN close_td c ON c.account_id = a.account_id
WHERE l.rn = 1
  AND (a.closed_trade_date IS NULL OR a.closed_trade_date >= :d);
```

- 잔액은 금액 합계가 아니라 `balance_after`로 구합니다. 합계로 구하면 §4 원장 대조가 같은 계산을 두 번 하게 되어 아무것도 검증하지 못합니다.
- 정렬(`occurred_at DESC, ledger_entry_id DESC`)은 적수 계산과 같습니다. 시드는 이 순서 보장의 대상이 아닙니다(#545) — §7-3.
- `close_td`는 해지일을 정하지 않고, 해지 출금 원장의 거래일이 `account.closed_trade_date`와 같은지 검증할 때만 원장 전체를 봅니다. 적재기는 조회한 `last_ledger_date`가 있을 때 `closed_trade_date`와 같은지 검사합니다. 첫 납입 전 해지처럼 원장이 없는 계좌도 `closed_trade_date`로 CLOSE 이벤트를 같은 기준일에 만듭니다. 원장 행이 한 번도 없는 계좌는 §2의 "첫 원장 거래일" 범위 밖이므로 잔액 스냅샷 행은 만들지 않습니다.

**이벤트**

- `SUBSCRIBE`: `product_subscription.status = 'SUCCESS'`이고 `subscription_trade_date = D`. `subscription_trade_date`는 가입 트랜잭션이 gate 연산(`acquireSharedAndReadBusinessDate()`)으로 읽은 거래일을 저장한 값입니다. 정기예금 초입금 원장과 정기적금의 `transaction_number = NULL` 여부에 의존하지 않습니다.
- `CLOSE`: `account.status = 'CLOSED'`이고 `product_id IS NOT NULL`, `closed_trade_date = D`인 계좌입니다. `closed_trade_date`는 해지 트랜잭션이 gate 연산으로 읽은 거래일(해지 출금 원장의 `trade_date`와 같은 값)을 저장한 값입니다. 첫 납입 전 해지에도 동일하게 CLOSE 행이 생깁니다.
- 상품 코드·이름·그룹은 적재할 때 `product`에서 복사합니다.

**가명 처리.** MySQL에는 HMAC 함수가 없고, SQL로 구현하면 키가 쿼리 로그·프로세스 목록에 남습니다. ETL 프로세스가 레플리카에서 읽은 행에 메모리에서 HMAC을 적용한 뒤 마트에 씁니다. 입력은 `account:{account_id}`, `customer:{customer_id}` 문자열입니다(결정 6). 원천 ID는 마트 쪽으로 넘어가지 않습니다. 키를 바꾸면 전 기간을 재적재합니다.

### 3-4. 읽기 전용 원칙

| 대상 | 규칙 |
|---|---|
| 레플리카 | `super_read_only = ON`. ETL 계정은 `ledger_entry`·`account`·`product`·`product_subscription`·`gl_journal_entry`·`holiday`(영업일 판정, §3-3)·`business_date_cutoff`에 `SELECT`만 |
| 마트 | 쓰기는 ETL 계정만. 정보계 API 계정은 `SELECT`만 |
| 방향 | 마트에서 계정계로 되돌려 쓰지 않는다 |
| 배치 | **마트는 레플리카와 다른 MySQL 인스턴스에 둔다**(같은 VM의 다른 포트 가능). `super_read_only`는 인스턴스 전체에 걸린다 |

### 3-5. P5에게 열어 둔 것

- 기본값은 매일 D까지의 원장을 다시 계산하는 전량 재계산입니다. 전날 마트에 그날 증감만 더하는 증분 방식은 PH-76 적재 시간 개선 재료입니다. §4 대조를 통과하면 어느 방식이든 계약 위반이 아닙니다.

---

## 4. 정합성 대조

| | ① 원장 대조 | ② GL 대조 |
|---|---|---|
| 보는 것 | 적재가 정확한가 | 계정계와 회계가 맞는가 |
| 마트 쪽 | `mart_balance_total` | `mart_balance_movement` (D′ → D) |
| 상대 | `ledger_total` = 원장 입금 합계 − 출금 합계, `td ≤ D` | `gl_deposit_movement` = GL `20100` 대변 합계 − 차변 합계, `D′ < trade_date ≤ D` |
| 판정 | 원 단위로 같다 | 원 단위로 같다. D′가 없으면 생략 |
| 불일치 | **`FAILED`**(롤백). `asOf`는 D′에 머문다 | **`COMPLETED`**로 커밋하고 차이를 남긴다. 차이는 설명 대상(BCBS 239 원칙 3) |

- 원장 대조를 실패로 두는 이유: 틀린 숫자보다 하루 전 숫자를 기준시점과 함께 보여 주는 편이 정직합니다. COB는 영향을 받지 않으므로 PH-76의 "적재 실패는 soft"와 같습니다.
- **GL은 누적이 아니라 증감으로 비교합니다.** PH-60b 시드 GL은 9/1~10/5뿐이고, 운영 훅 B(PH-24)는 10/16 릴리스로 나갑니다. 그 사이 이체는 원장에만 있습니다. 누적으로 비교하면 이 공백이 이후 모든 날짜에 남는데, 메우려면 운영 GL에 소급 전표를 넣어야 합니다(운영 DB 직접 삽입 금지). 증감으로 비교하면 공백이 있던 날만 불일치하고 이후는 일치합니다. 이자 분개처럼 늦게 붙는 기표 경로도 같은 방식으로 날짜 단위로 드러납니다.
- **대조하지 않는 것.** 이벤트 마트는 원천 조건으로 다시 세면 적재 쿼리를 반복할 뿐이라 생략합니다. 중복은 `uk_mpse_account_event`가 막습니다.
- 실패한 적재도 대조 값을 남깁니다(§3-2 T3).
- README §5 "마트 ↔ 원장 정합성" = 원장 대조를 통과한 날짜 수 ÷ 적재한 날짜 수.

**PII 0건 검증**(README §5 "마트 PII · 스캔", P3·P5). 적재마다가 아니라 PH-76 완료 시 한 번 합니다.

1. 컬럼 화이트리스트: 세 테이블의 `information_schema.columns`가 §2 DDL과 정확히 같다.
2. 값 스캔: 문자열 컬럼에 계좌번호 형식(`^[0-9]{12}$`)이 없고, `*_key` 컬럼은 64자리 16진수만 있다.

---

## 5. 조회 API

### 5-1. 경로

```
브라우저 → ALB → WAS  /admin/info/**        (세션 + GL_READ, FE 계약)
                  └─ VPN → 온프레미스 정보계 API  /info/**  (같은 응답 모양, WAS 어댑터가 전달)
```

- 경로 접두어는 기존 관리자 API(`/admin/customers`)를 따릅니다. 권한은 `GL_READ`([D-09](README.md#d-09--923--리드))입니다.
- `X-Access-Reason`은 받지 않습니다(FE `requirements-admin.md` POL-A09가 정보계를 제외).
- WAS ↔ 온프레미스 인증 방식은 P5가 정합니다.

### 5-2. 일별 수신 잔액 추이

`GET /admin/info/deposit-balances?from=2026-10-01&to=2026-10-28`

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "asOf": "2026-10-28",
    "items": [
      { "baseDate": "2026-10-01", "totalBalance": 1523400000000 },
      { "baseDate": "2026-10-02", "totalBalance": 1524100000000 }
    ]
  }
}
```

- `from`·`to`는 생략 가능합니다. 생략하면 `asOf`부터 거슬러 30일, 최대 범위는 1년입니다.
- `items`에는 `asOf` 이하의 `COMPLETED` 영업일만 들어갑니다. 휴일은 없고, `asOf` 뒤에 적재된 날짜는 실패한 날짜가 재적재될 때까지 나오지 않습니다.
- 원천은 `mart_load_batch.mart_balance_total`입니다. 15만 행 × 기간을 매번 합산하지 않습니다.

### 5-3. 상품별 가입·해지

`GET /admin/info/product-subscriptions?month=2026-10`

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "asOf": "2026-10-28",
    "month": "2026-10",
    "subscribedCount": 120,
    "closedCount": 5,
    "items": [
      { "productId": 3, "productCode": "DEP-12M", "productName": "쏠편한 정기예금",
        "productGroup": "DEPOSIT", "subscribedCount": 80, "closedCount": 3 }
    ]
  }
}
```

- `month`를 생략하면 `asOf`가 속한 달입니다. 범위는 그 달 안에서 `base_date ≤ asOf`입니다.
- 상품 이름은 `product_id`별로 가장 최근에 적재된 스냅샷입니다.

### 5-4. 공통

| 항목 | 규칙 |
|---|---|
| `asOf` | 첫 적재 기준일부터 영업일마다 `COMPLETED` 배치가 빠짐없이 있는 마지막 날짜(`yyyy-MM-dd`). 중간에 `COMPLETED` 배치가 없는 영업일(`FAILED`이거나 적재되지 않은 날)이 있으면 그 전날에 머문다. 이미 `COMPLETED`인 날짜의 재적재가 실패해도 이전 `COMPLETED` 배치가 남으므로 `asOf`는 뒤로 가지 않는다. ETL이 T2에서 `holiday`로 계산해 `as_of_date`에 남기고, API는 `completed_at`이 가장 늦은 `COMPLETED` 행의 `as_of_date`를 읽는다. 두 API가 같은 값을 쓴다. 다른 날짜를 동시에 적재하면 서로의 커밋을 못 봐 실제보다 이른 값이 남을 수 있고, 다음 적재에서 바로잡힌다 |
| 적재 이력 없음 | `200` + `"0000"`, `asOf: null`, 값은 `null`이나 빈 배열. FE는 값 대신 안내를 띄운다(REQ-ADM-032) |
| 정보계 API 연결 실패 | 오류코드로 응답. `INF9001`(500)로 [api_conventions.md](../api_conventions.md) §4-15에 등록했다 |
| PII | 고객 식별자·가명 키를 응답에 넣지 않는다(REQ-ADM-033) |
| 금액 | 원 단위 정수 |

### 5-5. ADM-03 연결 (REQ-ADM-030)

| 화면의 숫자 | 출처 |
|---|---|
| 총 예수금 잔액 | §5-2 인자 없이 → `items`의 마지막 `totalBalance` |
| 당월 신규 가입 | §5-3 인자 없이 → `subscribedCount` |
| 당월 해지 | §5-3 인자 없이 → `closedCount` |

숫자마다 그 응답의 `asOf`를 붙입니다. 두 호출 사이에 적재가 커밋되면 둘이 다를 수 있고, REQ-ADM-031은 숫자마다 기준시점을 따로 표기하므로 그대로 맞습니다. 화면 전용 요약 API는 만들지 않습니다.

---

## 6. 범위 밖

마감잔액 테이블, 채널별 거래량, 평잔(필요하면 §2 잔액 마트 위에 휴일을 채우는 뷰로 붙인다), 해지 유형 구분, 응답의 마감 시각(`cutoffAt` — `business_date_cutoff.closed_at`으로 필드를 추가해 붙일 수 있다, PH-94).

---

## 7. 확인 대기

1. **P2 — 생명주기 거래일.** PH-76 전에 `product_subscription.subscription_trade_date`와 `account.closed_trade_date`를 추가하고, 가입·해지 트랜잭션이 §3-0 gate 연산으로 읽은 거래일을 각각 저장합니다(`today()`로 따로 읽지 않습니다). 예·적금 해지는 잔액을 0으로 만드는 출금 기표도 남기고 그 원장의 `trade_date`가 `closed_trade_date`와 같은지 PH-76에서 검증합니다. 원장이 없는 첫 납입 전 해지도 `closed_trade_date`로 CLOSE 이벤트를 만들므로 제외하지 않습니다.
2. **P5 — 원천 마감 cutoff.** 구현됨(PR #670). 단일 gate와 `business_date_cutoff`를 구현했고 원장·상품가입·정정 경로를 gate로 옮겼습니다. 이체 실행 경로도 #662에서 옮겼습니다. #603의 `batch_execution_history`는 관측용으로 유지하되 ETL 시작 조건에는 쓰지 않습니다.
3. **P5·P3 — 시드 구간 원장 대조.** 시드는 원장 순서 보장(#545)의 대상이 아니어서 9/1~10/5 원장 대조가 실패할 수 있습니다. PH-76 덤프 첫 적재에서 확인하고, 실패면 원인을 P6에 넘깁니다.
4. **P3 — 가명정보 가이드라인.** 결정 6은 기술적 성질로 정했습니다. 개인정보보호위원회 「가명정보 처리 가이드라인」(2026.03) 본문과 대조가 남았습니다.
5. **P3·P5 — 해지 이벤트 날짜 이동.** `closed_trade_date`가 불변이므로 늦은 원장 기표가 와도 CLOSE 이벤트 날짜는 움직이지 않습니다. 재적재 시 같은 `(account_key, CLOSE)`를 해당 기준일 행 교체로 처리합니다. 해지 출금 원장의 거래일이 `closed_trade_date`와 다르면 원장 대조와 별도로 `FAILED`로 드러내고 원천 계약 위반으로 처리합니다.

---

## 8. 참고

- Fineract(`apache/fineract` develop, 로컬 `65b3b21a4`)
    - `UpdateTrialBalanceDetailsTasklet`, `JournalEntryRepository.findTrialBalanceLinesForDate` — GL 계정 × 거래일 잔액, 현재 영업일 제외
    - `0200_add_journal_entry_aggregation_tables.xml`, `JournalEntryAggregationJobListener`, `JournalEntryAggregationTrackingTasklet`, `JournalEntryAggregationJobReader` — 행마다 실행 ID, 실패 시 실행 ID로 삭제, 성공만 추적 기록, 집계 기준일 = 분개 업무일
    - `SavingsAccountTransaction.updateCumulativeBalanceAndDates` — 계좌 잔액은 구간 방식(`balance_end_date_derived`)
    - `0074_add_last_cob_business_date_column_to_loan.xml`, `RetrieveAllNonClosedIdServiceImpl.retrieveLoanCOBPartitions` — 마감 표시 컬럼은 과거 값 없이 추가, 정기 COB는 NULL 포함, 따라잡기는 NULL 제외
- [Kimball Group — Fact Tables](https://www.kimballgroup.com/2008/11/fact-tables/)
- [Oracle OFSAA — Overview of Account Summary Tables](https://docs.oracle.com/cd/E92917_01/PDF/8.0.9.0.0/FSDF_HTML/UG/Overview_of_Account_Summary_Tables.htm)
- [BCBS 239 — Principle 3](https://blogs.sas.com/content/sascom/2014/04/21/series-bcbs-239-principle-3/)
