# PH-38 전수 대사 베이스라인 측정 결과

> 300만 원장 전수 대사(`reconcileAll()`)의 개선 전 실행 시간을 기록한다.
> PH-45(대사 청크·병렬화)를 적용한 뒤 같은 실행기·동일 데이터·동일 회차 구성으로 다시 재서 비교한다.
> 재측정 시 기존 결과를 지우지 않고 추가한다.

## 0. 왜 일회성 실행기로 재는가

- 전수 대사는 #604 이후 COB 대사 스텝(`LedgerReconciliationCobStep`) 안에서만 돈다. 독립 스케줄과 수동 실행 API는 없다.
- COB는 영업일 23:30에만 돈다. 실행하면 영업일을 실제로 넘기므로 운영에서 반복 측정에 쓸 수 없다.
- 그래서 COB 대사 스텝과 같은 `reconcileAll()`을 같은 대사 락(`LEDGER_RECONCILIATION_BATCH`) 안에서 N회 돌리는 일회성 실행기를 쓴다(#744).
    - 실행기: `LedgerFullReconciliationBaselineRunner`
    - 측정은 결과를 읽기만 한다. 불일치 테이블과 영업일은 건드리지 않는다.
    - 락을 못 잡거나 예외가 나면 종료 코드가 0이 아닌 값으로 끝나고, 측정값을 남기지 않는다.
- 이 값은 COB 대사 스텝 시간 중 `reconcileAll()` 부분이다. COB 스텝에만 있는 불일치 저장과 soft/hard 판정은 포함하지 않는다.

## 1. 측정 절차 (기존 단일 EC2 · 이전 RDS)

운영 서버는 재기동하지 않는다. 운영 이미지는 그대로 두고 측정용 jar만 덮어써서 일회성 컨테이너로 실행한다.

```bash
# 로컬: main(176fe574)과 내용이 같은 dev에 #744 커밋을 얹은 브랜치에서 jar를 만든다
./gradlew bootJar
scp -i ~/.ssh/corebank-key.pem build/libs/*SNAPSHOT.jar ubuntu@<EC2>:/home/ubuntu/ph38/app.jar

# EC2: 운영 compose 디렉터리에서 실행한다. 서비스 환경변수(DB 접속)와 네트워크를 그대로 쓴다
cd /home/ubuntu/corebank
docker compose run --rm \
  -v /home/ubuntu/ph38/app.jar:/app/app.jar \
  corebank-server \
  --spring.profiles.active=prod,ph38-baseline \
  --app.ph38-baseline.execute=true \
  --app.ph38-baseline.rounds=4 \
  --app.ph38-baseline.warmup-rounds=1 \
  --spring.main.web-application-type=none \
  2>&1 | tee ph38_$(date +%Y%m%d_%H%M).log
echo "exit=${PIPESTATUS[0]}"
```

- 다른 운영 측정(PH-49c · PH-11 · PH-28)과 시간대를 나눈다. 매시 `:30`·`:45`(대사)와 23:20~00:20은 피한다.
- 측정 뒤 락이 풀렸는지 확인한다. 컨테이너를 강제로 멈추면 락이 남는다. 남은 락은 stale 기준인 6시간이 지나야 풀리고, 그 전에는 COB 대사가 건너뛴다.

```sql
SELECT * FROM batch_execution_lock WHERE job_name = 'LEDGER_RECONCILIATION_BATCH';  -- currently_running = 0
```

## 2. PH-60b 300만 원장 베이스라인

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | |
| 측정 환경 | 기존 단일 EC2 · 이전 RDS(1차 계정, 컷오버 전) |
| 운영 이미지 / 측정 jar 커밋 | `176fe574` / |
| 데이터 | PH-60b 운영 적재분 |
| 계좌 수 (`accounts`) | |
| `ledger_entry` 행 수 | |
| 청크 크기 | `app.ledger-reconciliation.full-scan-chunk-size` = 1000(기본값) |
| 회차 구성 | 4회(예열 1회 + 측정 3회) |
| 직전 배포 | 릴리스 2(10/24). `transfer`·`gl_voucher`를 다시 써서 DB 캐시가 비어 있던 상태부터 시작 |

### 측정 결과

| 회차 | 구분 | elapsedMs | mismatches |
| --- | --- | ---: | ---: |
| 1 | 예열 | | |
| 2 | 측정 | | |
| 3 | 측정 | | |
| 4 | 측정 | | |

| 중앙값(ms) | 최소(ms) | 최대(ms) | 종료 코드 |
| ---: | ---: | ---: | ---: |
| | | | |

PH-38 완료 기준에 따라 불일치는 0건이어야 한다. 0건이 아니면 수치와 함께 계좌를 기록하고 원인을 따로 본다.

## 3. PH-45 비교 기준

PH-45를 적용한 뒤 다음 조건을 같게 유지해 다시 잰다.

- 같은 실행기와 회차 구성(예열 1 + 측정 3)
- 같은 데이터(계좌 수와 `ledger_entry` 행 수 일치)
- 같은 환경. 컷오버로 환경이 바뀌면 이전 RDS 값과 비교하지 않고, 새 환경에서 개선 전을 다시 잰다
