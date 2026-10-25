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
ssh -i ~/.ssh/corebank-key.pem ubuntu@<EC2> 'mkdir -p /home/ubuntu/ph38'
scp -i ~/.ssh/corebank-key.pem build/libs/*SNAPSHOT.jar ubuntu@<EC2>:/home/ubuntu/ph38/app.jar

# EC2: 운영 compose 디렉터리에서 분리(-d) 실행한다. 서비스 환경변수(DB 접속)와 네트워크를 그대로 쓴다
cd /home/ubuntu/corebank
docker compose run -d --name ph38-baseline \
  -v /home/ubuntu/ph38/app.jar:/app/app.jar \
  corebank-server \
  --spring.profiles.active=prod,ph38-baseline \
  --app.ph38-baseline.execute=true \
  --app.ph38-baseline.rounds=3 \
  --app.ph38-baseline.warmup-rounds=0

# 진행·결과 확인 (끝나면 상태가 Exited (0))
docker logs ph38-baseline 2>&1 | grep -E "Started Corebank|PH-38 baseline|전수 대사 종료|ERROR"
docker ps -a --format '{{.Names}} {{.Status}}' | grep ph38
docker rm ph38-baseline   # 결과를 옮긴 뒤 정리
```

이렇게 정한 이유는 10/25 시행착오에서 나왔다.

- **`-d`로 분리 실행한다.** `docker compose run`은 SSH 세션의 표준 입력에 붙어 있어, 세션이 끊기면 컨테이너를 멈춘다. `nohup`을 붙여도 마찬가지다. 첫 시도(00:59)는 1회차가 끝난 직후, 두 번째 시도(01:38)는 1회차 도중(01:55)에 이렇게 꺼졌다.
- **`--rm`을 빼고 `--name`을 붙인다.** 그래야 끝난 뒤에도 `docker logs`로 결과를 읽을 수 있다. 결과를 옮긴 뒤에 지운다.
- **`--spring.main.web-application-type=none`을 쓰지 않는다.** 릴리스 2(PH-49c)부터는 `SessionLoginManager`가 `ServerProperties` 빈을 요구해서, 웹을 끄면 기동이 실패한다. 웹 서버는 컨테이너 안에서만 뜨고, `compose run`은 포트를 열지 않으므로 운영 서버와 충돌하지 않는다.
- **로그를 `/home/ubuntu/corebank`에 쓰지 않는다.** 이 디렉터리는 CD가 root로 만들어서 ubuntu 사용자가 파일을 쓸 수 없다.

측정 시간대와 락 확인:

- 다른 운영 측정(PH-49c · PH-11 · PH-28)과 시간대를 나눈다. 자동·예약이체 재확정(짝수 시 `:30`·`:45`)과 23:20~00:20은 피한다.
- 측정 뒤 락이 풀렸는지 확인한다. 컨테이너가 강제로 꺼지면 `finally`의 해제가 실행되지 않아 락이 남는다. 10/25 두 번의 중단 모두 `currently_running = 1`로 남아 수동으로 풀었다. 남은 락은 stale 기준인 6시간이 지나야 풀리고, 그 전에는 COB 대사가 건너뛴다.

```sql
SELECT * FROM batch_execution_lock WHERE job_name = 'LEDGER_RECONCILIATION_BATCH';  -- currently_running = 0
-- 강제 종료로 남았을 때만
UPDATE batch_execution_lock SET currently_running = 0 WHERE job_name = 'LEDGER_RECONCILIATION_BATCH';
```

## 2. PH-60b 300만 원장 베이스라인

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | 2026-10-25 13:00:14 ~ 14:00:33 KST |
| 측정 환경 | 기존 단일 EC2 · 이전 RDS(1차 계정, 컷오버 전). RDS는 `db.t4g.micro`, 버퍼 풀 128MB(기본값), gp3([PH-57](ph57_capacity_plan.md) §4-2) |
| 운영 이미지 / 측정 jar 커밋 | `176fe574` / `5e8a70b3`(#745. 운영 코드에 실행기만 추가) |
| 데이터 | PH-60b 운영 적재분 |
| 계좌 수 (`accounts`) | 180,011 |
| `ledger_entry` 행 수 | 약 6,425,295(`information_schema.tables.table_rows` 추정치) · 데이터+인덱스 1,946MB |
| 청크 크기 | `app.ledger-reconciliation.full-scan-chunk-size` = 1000(기본값) |
| 회차 구성 | 3회, 예열 없음(아래 "예열을 뺀 이유") |
| 직전 배포 | 릴리스 2(10/24 11:43). `transfer`·`gl_voucher`를 다시 썼다 |
| 같은 시간대 다른 작업 | 운영 서버 정상 가동. 다른 측정 없음(공지 스레드에 시간대 공지) |

### 측정 결과

| 회차 | elapsedMs | 분 | mismatches |
| --- | ---: | ---: | ---: |
| 1 | 1,212,571 | 20.2 | 0 |
| 2 | 1,186,309 | 19.8 | 0 |
| 3 | 1,188,096 | 19.8 | 0 |

| 중앙값(ms) | 최소(ms) | 최대(ms) | 종료 코드 |
| ---: | ---: | ---: | ---: |
| 1,188,096 | 1,186,309 | 1,212,571 | 0 |

- 불일치는 3회 모두 0건이다(PH-38 완료 기준 충족).
- 참고: 중단된 첫 시도(00:59 시작, 같은 jar)의 1회차는 1,200,069ms·불일치 0건이었다. 회차 간 차이는 최대 2.2%다.

### 해석

- **회당 약 20분은 예상(2~3분)의 약 8배다.** 측정 전에 중간 덩어리(계좌 1000개, 원장 29,000행)를 `BETWEEN`으로 `EXPLAIN ANALYZE`했을 때는 0.66초였다. 이걸 덩어리 수만큼 곱해 2~3분으로 잡았다. 실제로는 덩어리당 약 6.6초다. 차이의 원인은 확인하지 않았다. 앱 쿼리는 `IN (값 1000개)`를 쓰는데 실행 계획이 다를 수 있고, RDS CPU 크레딧도 영향을 줄 수 있다.
- **예열을 뺀 이유:** `ledger_entry`(1,946MB)가 버퍼 풀(128MB)보다 15배 커서 회차마다 디스크를 다시 읽는다. 실제로 1회차와 2·3회차의 차이가 2% 남짓이었다.
- **COB 영향:** 이 시간은 COB 대사 스텝의 `reconcileAll()` 부분과 같다. 이전 RDS에서는 매 영업일 COB 대사가 약 20분 걸린다.

## 3. PH-45 비교 기준

PH-45를 적용한 뒤 다음 조건을 같게 유지해 다시 잰다.

- 같은 실행기와 회차 구성(3회, 예열 없음)
- 같은 데이터(계좌 수와 `ledger_entry` 행 수 일치)
- 같은 환경. 컷오버로 환경이 바뀌면(새 RDS는 `db.t3.small`, 버퍼 풀 약 1.3GB) 이 값과 비교하지 않고 새 환경에서 개선 전을 다시 잰다
