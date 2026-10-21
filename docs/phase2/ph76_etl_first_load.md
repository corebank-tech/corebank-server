# PH-76 정보계 ETL 첫 적재 측정 결과

> PH-76 ETL(`info-etl.jar`)의 덤프 첫 적재 시간과 원장·GL 대조, PII 검증 결과를 기록한다.
> 최종 VM·실제 복제 환경에서 다시 재면 기존 결과를 지우지 않고 아래에 추가한다.

## 1. 로컬 덤프 첫 적재 (2026-10-21)

로컬 Mac에서 잰 **기준선**이다. 코드가 원장 650만 행 규모에서 정상 동작하고 대략 몇 초대인지 보는 값이며, 운영 환경의 시간이 아니다. 최종 VM 측정은 §2에 추가한다.

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | 2026-10-21 10:23~10:35 KST |
| 측정 환경 | 로컬 Mac, Docker MySQL 8.4.10 두 개(레플리카 역할 3317, 마트 역할 3318). 버퍼 풀 1GB, binlog 끔, `innodb_flush_log_at_trx_commit=2` |
| ETL | `info-etl.jar`, `java -Xmx2g`, 브랜치 `feat/646-etl-verification-first-load` (`3d9c5587`) |
| 접속 계정 | 두 DB 모두 root (`etl-grants.sql`의 권한 분리 계정이 아님) |
| 데이터 | PH-60 최소 시드 + PH-60b 대량 시드(`phase2-seed` 프로필, 생성 약 20분) |
| 계좌 / 고객 | 180,000 / 80,000 (PH-60 30,000 + PH-60b 150,000) |
| 원장 | 6,500,000행(거래 약 325만 건의 입출금 쌍), 거래일 2026-09-01 ~ 2026-10-05 |
| GL `20100` | 약 6,470,000줄 |
| 가입 | 100,000건 |
| 레플리카 DB 크기 | 약 4.9GB (`ledger_entry` 1.9GB, `gl_journal_entry` 1.2GB, `transfer` 1.0GB) |

"300만 원장"은 거래 건수 기준이고, 행으로는 650만이다. 이슈 #646의 규모와 같다.

### 재현 방법

```bash
# 1) 레플리카 역할 DB와 마트 역할 DB
docker run -d --name etl-bench-replica -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=minicore -p 3317:3306 \
  mysql:8.4.10 --skip-log-bin --innodb-buffer-pool-size=1G --max-allowed-packet=256M --innodb-flush-log-at-trx-commit=2
docker run -d --name etl-bench-mart -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=info_mart -p 3318:3306 \
  mysql:8.4.10 --skip-log-bin --innodb-buffer-pool-size=1G --max-allowed-packet=256M --innodb-flush-log-at-trx-commit=2

# 2) 레플리카에 시드 적재 (최소 시드 → 대량 시드 순서, 각각 별도 실행)
java -jar build/libs/corebank-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,phase2-seed \
  --spring.datasource.url='jdbc:mysql://localhost:3317/minicore?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true' \
  --spring.datasource.username=root --spring.datasource.password=pw --spring.main.web-application-type=none \
  --app.phase2-seed.minimum.execute=true      # 이어서 --app.phase2-seed.bulk.execute=true

# 3) 마트 스키마 적용
docker exec -i etl-bench-mart mysql -uroot -ppw info_mart < src/infoEtl/resources/mart-schema.sql

# 4) ETL 실행 (./gradlew infoEtlJar 로 build/info-etl/info-etl.jar 생성)
export REPLICA_JDBC_URL='jdbc:mysql://localhost:3317/minicore?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8'
export REPLICA_USER=root REPLICA_PASSWORD=pw
export MART_JDBC_URL='jdbc:mysql://localhost:3318/info_mart?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8'
export MART_USER=root MART_PASSWORD=pw HMAC_KEY='<32바이트 이상>'
java -Xmx2g -jar build/info-etl/info-etl.jar --base-date=2026-10-06             # 단일 적재
java -Xmx2g -jar build/info-etl/info-etl.jar --from=2026-09-01 --to=2026-10-06  # 기간 적재
java -Xmx2g -jar build/info-etl/info-etl.jar --verify-pii                        # PII 검증
```

### 측정 결과

| 지표 | 결과 |
| --- | ---: |
| 하루 단일 적재 (10/06, 잔액 180,000행, 이벤트 8,313건) | 27.7초 |
| 하루 재적재 `--rebuild` (10/06, 같은 데이터, 해지 계좌 검사 추가 후 `448b49dd`) | 33.9초, 33.2초 (2회) |
| 기간 적재 9/1~10/6 영업일 | 22일 성공, 1일(10/06) 이미 완료라 건너뜀, 실패 0 |
| 기간 적재 총 소요 | 586초 (약 9.8분, 적재 22건 합 584.4초) |
| 하루당 적재 시간 | 최소 10.5초, 최대 42.6초, 평균 26.6초 |
| 마트 최종 행 수 | 잔액 2,968,612 / 이벤트 100,000 / 적재 기록 23 |
| 마트 크기 | 약 1.3GB |
| PII 검증 | 통과. 문자열 컬럼 12개 검사, 위반 0건 (36초) |

하루 적재 시간은 날짜가 갈수록 늘었다(잔액 행이 84,806 → 171,687로 늘어난 영향과 같은 PC에서 테스트를 돌린 부하가 겹쳤다).

### 대조 결과

| 대조 | 결과 |
| --- | --- |
| 원장 대조 (info_mart.md §4 ①) | 23개 배치 모두 통과. 시드 구간(9/1~10/2) 포함이며 #545로 실패할 수 있다던 우려는 해소됐다 |
| GL 대조 (§4 ②) | 22일 모두 마트 증감 = GL 증감(0). 첫 적재일은 직전 완료일이 없어 생략 |
| `asOf` | 9/1부터 끊김 없이 이어져 10/06까지 갱신 |

### 특이사항

- 로컬 Docker 수치라 운영 VM과 다르다. 측정 중에 같은 PC에서 테스트도 돌려 부하가 섞였다.
- 시드 이체는 계좌 간 이동뿐이라 마트 합계가 매일 1,060,000,000,000으로 같고 증감이 0이다. 그래서 GL 대조는 0과 0이 맞은 것이며, 실제 차이를 잡아낸 측정이 아니다. 불일치 처리(COMPLETED로 커밋하고 차이를 남김)는 통합 테스트로 검증했다.
- 잔액 쿼리는 7일 상한 없이 원장 전체를 훑는데(설계 §3-3) 하루 20~40초였다. 증분 방식(설계 §3-5)은 최종 VM 측정에서 시간이 길게 나오면 그때 검토한다.
- 읽기·변환·쓰기 구간별 시간과 JVM 최대 사용 메모리는 분해해서 재지 않았다.
- 재적재(`--rebuild`) 33~34초는 첫 적재 27.7초와 조건이 다르다. 이미 완료된 날짜의 기존 행을 지우고 이전 배치를 `REPLACED`로 바꾸는 비용이 더해진다. 그 사이 추가된 해지 계좌 검사(`EXISTS`)는 이 데이터에 해지 계좌가 없어 비용이 거의 없다(직접 비교 대상이 아님).
- **NULL 거래일 올림 비용은 기본 측정에 들어 있지 않다.** 시드 원장은 `trade_date`가 NULL인 행이 0건이다. NULL 행마다 영업일 올림 서브쿼리가 돌므로 운영 데이터에 NULL 행이 있으면 시간이 늘며, 늘어나는 정도는 아래 "NULL 거래일 비율별 하루 재적재 시간"에 재 두었다.
- **한쪽만 있는 기표(타행이체)가 없다.** 시드 이체는 모두 입출금 쌍이다. 10/19~10/20에 머지된 타행 출금·되돌림은 원장 1행만 남긴다. 원장 대조는 `balance_after`와 기표 금액의 부호 합만 비교해 쌍을 전제하지 않으므로, 한쪽 기표를 입력으로 쓰는 기존 통합 테스트로 이미 검증된다. GL 20100 증감이 실제 타행 흐름과 맞는지(송신은 20100 차변, 되돌림은 20100 대변)는 타행 거래가 쌓인 뒤 첫 실제 대조에서 확인한다. GL 불일치는 적재를 막지 않고 차이를 남긴다.
- 실제 운영 거래량이 시드보다 늘면 원장 전체를 훑는 쿼리 시간도 늘어난다.

### NULL 거래일 비율별 하루 재적재 시간

같은 데이터에서 원장 행의 `trade_date`를 임시로 NULL로 만들고(원래 값은 백업 테이블에 두었다가 복원), 하루 재적재(`--rebuild`, 10/06)를 쟀다. 목적은 운영에 거래일이 NULL인 기존 행(#515 채우기 전)이 있을 때의 비용을 가늠하는 것이다.

| NULL 비율 | NULL 행 수 | 하루 재적재 | 0% 대비 |
| --- | ---: | ---: | ---: |
| 0% | 0 | 33.2~33.9초 | 기준 |
| 10% | 650,000 | 51.5초 | +18초 (약 1.5배) |
| 100% | 6,500,000 | 230.8초 | +197초 (약 7배) |

- 증가폭이 비율에 거의 비례한다(NULL 1%당 약 2초). NULL 행이 적으면 영향이 작고, 전부 NULL이어도 4분 안에 끝난다.
- **결과는 달라지지 않았다.** 세 경우 모두 잔액 180,000행, 합계 1,060,000,000,000, 원장 대조 통과였다. 올림 규칙의 결과가 시드의 실제 거래일과 같다는 뜻이다.
- 복원 뒤 행 수, NULL 0건, `SUM(TO_DAYS(trade_date))`가 원본과 일치함을 확인했고, 복원 후 하루 적재는 34.9초였다.
- 운영의 NULL 행 수를 모르므로 `SELECT COUNT(*) FROM ledger_entry WHERE trade_date IS NULL` 결과를 받으면 위 표로 영향을 가늠할 수 있다.

### 부록: 날짜별 적재 시간 (기간 적재, 초)

| 기준일 | 시간 | 기준일 | 시간 | 기준일 | 시간 |
| --- | ---: | --- | ---: | --- | ---: |
| 09-01 | 10.5 | 09-14 | 22.6 | 09-23 | 38.7 |
| 09-02 | 19.5 | 09-15 | 24.3 | 09-28 | 34.6 |
| 09-03 | 18.9 | 09-16 | 25.2 | 09-29 | 33.1 |
| 09-04 | 18.4 | 09-17 | 24.3 | 09-30 | 31.4 |
| 09-07 | 20.4 | 09-18 | 42.6 | 10-01 | 35.8 |
| 09-08 | 19.4 | 09-21 | 37.1 | 10-02 | 32.7 |
| 09-09 | 20.7 | 09-22 | 31.1 | | |
| 09-10 | 20.8 | | | | |
| 09-11 | 22.2 | | | | |

## 2. 최종 VM 재측정 (예정)

최종 VM과 접속 정보를 받으면 같은 데이터·같은 명령으로 다시 재고 이 절에 추가한다. 기록할 항목:

- VM 사양(코어, 메모리, 디스크)과 MySQL 설정, 레플리카·마트 인스턴스 분리 여부
- 시드 덤프 이동 방법과 소요 시간(레플리카 DB 약 4.9GB)
- 하루 단일 적재, 기간 적재, PII 검증 시간과 JVM 최대 메모리
- 로컬 기준선(§1)과의 차이
- 운영 데이터의 NULL `trade_date` 행 수(위 NULL 비율 표로 비용 환산)와 타행이체 같은 한쪽만 있는 기표가 포함된 GL 대조 결과
