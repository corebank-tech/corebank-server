# PH-11 적수 베이스라인 측정 결과

> PH-11 계좌별 단건 조회 방식의 개선 전 성능을 기록한다.
> PH-13에서 원장 집계 SQL로 전환한 뒤 동일 EC2·동일 데이터·동일 계산 기간과 checksum을 기준으로 비교한다.
> 재측정 시 기존 결과를 지우지 않고 추가한다.

## 1. PH-60 3만 계좌 베이스라인

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | 2026-10-08 12:36~12:38 KST |
| 측정 환경 | 기존 단일 EC2 |
| Docker 이미지 / commit SHA | `c12687e9025c306037c4dd71a4379a4ffe7f70e7` |
| 데이터 | PH-60 |
| accountId 시작 | `60000001` |
| 계좌 수 | `30,000` |
| 계산 기간 | `[2026-09-01, 2026-10-01)` |
| 실행 방식 | 계좌별 `AccumulatedDailyBalanceUseCase.calculate()` 반복 호출 |

### 실행 명령

```bash
docker compose run --rm corebank-server \
  --spring.profiles.active=prod,ph11-baseline \
  --app.ph11-baseline.execute=true \
  --app.ph11-baseline.account-id-start=60000001 \
  --app.ph11-baseline.account-count=30000 \
  --app.ph11-baseline.from-inclusive=2026-09-01 \
  --app.ph11-baseline.to-exclusive=2026-10-01 \
  --spring.main.web-application-type=none
```

### 측정 결과

| 지표 | 결과 |
| --- | ---: |
| totalSeconds | `101.983s` |
| averageMs | `3.394ms` |
| p95Ms | `5.793ms` |
| p99Ms | `11.714ms` |
| checksum | `31,800,000,000,000` |
| zeroResultCount | `0` |
| exit code | `0` |

`zeroResultCount=0`으로 PH-60 계좌 30,000건 전체가 측정 대상에 포함됐음을 확인했다.

측정 완료 후 Spring application context와 HikariCP가 정상 종료됐고,
일회성 컨테이너의 종료 코드는 `0`이었다.

## 2. PH-60b 15만 계좌 베이스라인

PH-60b 운영 RDS 적재 및 정합성 검수 완료 후 동일 방식으로 측정한다.

측정 시 다음 항목을 함께 기록한다.

- 실행 일시
- Docker 이미지 / commit SHA
- accountId 범위
- 계산 기간
- totalSeconds
- averageMs
- p95Ms
- p99Ms
- checksum
- zeroResultCount
- exit code

## 3. PH-13 비교 기준

PH-13에서는 집계 SQL 구현에 맞게 측정 경로를 조정한다.

다만 개선 전후 비교를 위해 다음 조건은 동일하게 유지한다.

- 동일 단일 EC2
- 동일 데이터
- 동일 계산 기간
- 동일 대상 계좌 수
- 동일 checksum

PH-13 적용 후 checksum이 PH-11과 일치하는지 확인하고 전체 수행시간을 비교한다.
