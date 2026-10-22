# PH-28b 회계 결함 주입과 탐지율 결과

> 회계 결함을 일부러 넣었을 때 어느 탐지 수단이 잡는지 기록한다. 결정과 근거는 [ADR-0009](../adr/0009-gl-integrity-controls.md)에 있다.
> 탐지율은 2절 행렬 테스트가 정본이고, 3절과 4절은 결함 없는 60b에서 잰 오탐 기준선과 실행계획이다.

## 1. 결론

| 항목 | 결과 |
|---|---|
| 기존 탐지 수단 3개 | 10건 중 7건 탐지 |
| 원장 전표 대사 추가 후 | 10건 중 10건 탐지 |
| DB가 주입을 막은 경우 | 1건 |
| 정상 거래 오탐 | 행렬 0건, 60b 35일 323만 5천 거래 0건 |
| 점검 응답 시간 | 60b 거래일 하루 중앙값 5.8초, 최대 23.3초 |

기존 수단 3개는 P4 원장 잔액 대사, 전표 차대변 점검, 시산표다. PH-28b가 더한 원장 전표 대사는 원장 거래마다 전표가 하나 있고 그 전표의 차변 합계와 대변 합계가 원장 금액과 같은지 보며, 원장 없이 남은 고아 전표 수도 함께 센다.

## 2. 결함 위치와 탐지 수단 행렬

`GlIntegrityCheckFaultDetectionTest`가 결함 하나마다 정상 거래 두 건으로 장부를 새로 만들고, 한 거래에만 결함을 넣은 뒤 탐지 수단 4개를 모두 돌린다. 기대값은 테스트의 `Fault` 표에 단언으로 들어 있어, 탐지 쿼리가 바뀌어 결과가 달라지면 CI가 깨진다. 결함을 넣지 않은 거래가 어느 수단에도 잡히지 않는 것도 매번 확인한다.

| # | 결함 | 주입 방법 | P4 대사 | 전표 차대변 | 시산표 | 원장 전표 대사 | 판정 |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | GL 금액변조 | 대변 분개 금액 +1 | | O | O | O | 기존 수단 탐지 |
| 2 | GL 편측기표 | 대변 분개 삭제 | | O | O | O | 기존 수단 탐지 |
| 3 | GL 분개누락 | 전표와 분개 삭제 | | | | O | 추가 수단만 탐지 |
| 4 | GL 중복기표 | 같은 유형과 참조 키로 전표 복제 | | | | | DB가 막음 |
| 5 | GL 중복기표 | 다른 참조 키로 전표 복제 | | | | O | 추가 수단만 탐지, 고아 전표 |
| 6 | GL 중복기표 | 같은 전표에 차변과 대변 분개 쌍 추가 | | | | O | 추가 수단만 탐지 |
| 7 | 원장 금액변조 | 입금 행 금액 +1 | O | | | | 기존 수단 탐지 |
| 8 | 원장 편측기표 | 입금 행 삭제 | O | | | | 기존 수단 탐지 |
| 9 | 원장 누락 | 출금 행과 입금 행 삭제 | O | | | O | 기존 수단 탐지, 고아 전표 |
| 10 | 원장 중복기표 | 같은 거래번호로 두 행 추가 | O | | | | 기존 수단 탐지 |
| 11 | 원장 중복기표 | 새 거래번호로 두 행 추가 | O | | | O | 기존 수단 탐지 |

- 4번은 `uk_gl_voucher_tx_type_reference_key`가 INSERT를 거부한다. 탐지율 분모에서 뺀다.
- 원장 결함은 계좌 잔액을 그대로 둔 채 넣었다. 잔액까지 함께 맞춘 원장 결함은 P4 대사도 잡지 못하며 이 행렬의 범위 밖이다.
- 7번과 10번을 원장 전표 대사가 잡지 못하는 것은 원장 금액을 거래번호별 최솟값으로 읽기 때문이다. 입금 행만 커지면 최솟값이 그대로라 전표와 맞아 보인다. 원장 내부 정합성은 P4 대사가 맡는다.
- 탐지 수단별로 나누면 GL 결함은 원장 전표 대사가, 원장 결함은 P4 대사가 빠짐없이 잡는다. 둘 중 하나만으로는 10건을 채우지 못한다.

## 3. 60b 오탐 기준선

결함을 넣지 않은 60b에서 거래일 35일을 모두 점검했다. 60b는 읽기만 했다.

| 항목 | 값 |
|---|---|
| 데이터 | PH-60과 PH-60b를 로컬 측정 DB에 새로 적재. 거래 323만 5천, 원장 650만, 전표 323만 5천 1, 분개 647만 2 |
| 적재 시간 | PH-60 32초, PH-60b 35분 37초. 두 시드 모두 자체 검증 통과 |
| `trade_date`가 비어 있는 원장 행 | 0 |
| 대사한 원장 거래 | 3,235,000. 60b 300만과 PH-60 23만 5천의 합과 같다 |
| 대사 대상 밖으로 건너뛴 거래 | 0 |
| 차대변 불일치 전표 | 35일 모두 0 |
| 원장과 맞지 않는 전표 | 35일 모두 0 |
| 고아 전표 | 35일 모두 0, 모두 확정값 |
| 시산표 차대변 | 35일 모두 일치 |

거래일별 응답 시간이다. 앱에서 HTTP 요청을 보내고 응답을 받을 때까지를 쟀다.

| 구분 | 거래 수 | 개선 전 | 개선 후 |
|---|---:|---:|---:|
| 9/1 | 354,706 | 35.0초 | 23.3초 |
| 그 밖의 평일 | 약 6만 8천 | 10.4초 ~ 23.8초 | 4.7초 ~ 10.5초 |
| 그 밖의 주말 | 약 1만 6천 | 10.4초 ~ 21.6초 | 3.8초 ~ 10.1초 |
| 9/25 | 400,000 | 46.7초 | 20.0초 |
| 10/3, 10/4 | 60,779 | 19.8초 ~ 20.0초 | 3.1초 ~ 4.1초 |
| 10/1, 10/2, 10/5 | 약 29만 3천 | 36.6초 ~ 38.6초 | 12.4초 ~ 13.3초 |
| 35일 합계 | 3,235,000 | 12분 8초 | 4분 25초 |
| 하루 중앙값 | | 19.2초 | 5.8초 |

## 4. 실행계획

9/25 기준 `EXPLAIN ANALYZE`다. 원문은 `perf/sql/ph28b/explain_hot_day.sql`과 `explain_voucher_totals.sh`로 다시 뽑을 수 있다.

| 쿼리 | 접근 | 개선 전 | 개선 후 |
|---|---|---:|---:|
| 원장 거래 첫 청크 1,000건 | `ix_le_txno` 범위 스캔, `occurred_at` 인덱스 조건 | 18.4초 | 2.2초 |
| 차대변 불일치 전표 | `gl_voucher` PK 범위 스캔, 분개 FK 인덱스 조회 | 14.5초 | 3.7초 |
| 대상 유형 전표 수 | `gl_voucher` PK 범위 스캔 | 0.3초 | 0.3초 |
| 청크 하나의 전표 합계 | `uk_gl_voucher_tx_type_reference_key` 커버링 범위 스캔 | 24ms | 24ms |

- **원장 거래 첫 청크.** 개선 전에는 거래번호 하한이 거래일 14일 전이라, 그 앞 2주치 거래번호를 모두 읽고 `trade_date`로 버린 뒤에야 첫 행이 나왔다. 하한을 직전 영업일 다음 날로 바꿔 그날 거래번호부터 읽는다. 9/25에 남은 2.2초는 60b가 휴일인 9/24에도 거래일을 찍어 둬서 그날 거래번호를 지나가는 시간이다.
- **차대변 불일치 전표.** 개선 전에는 전체 건수를 창 함수로 같이 내느라 40만 전표를 임시 테이블에 모아 집계했다. 전표번호 하나로 묶으면 PK 순서대로 흘려 집계한다. 건수는 결과를 읽으며 센다.
- 이후 청크는 커서 뒤부터 읽으므로 9/25 기준 청크 400개가 각각 수십 ms다. 하루 응답 시간의 대부분이 이 반복이다.

## 5. 관찰

- 60b는 휴일인 9/24, 9/25, 9/26, 10/3, 10/5에도 거래일을 찍는다. 런타임에서는 휴일 거래의 거래일이 다음 영업일이 된다. 탐지 결과에는 영향이 없지만, 거래일 분포를 실제 운영과 같다고 볼 수는 없다.
- 남은 응답 시간은 `trade_date`에 인덱스가 없어 거래번호 범위를 훑는 비용이다. 원장 `trade_date` 인덱스를 붙일지는 GL-IDX와 같은 방식으로 따로 판단한다.

## 6. 재현

```bash
# 측정 전용 MySQL. 로컬 개발 DB와 볼륨을 나눈다
docker run -d --name ph28b-perf-mysql -e MYSQL_ROOT_PASSWORD=localpw -e TZ=Asia/Seoul -p 3307:3306 \
  -v ph28b-perf-mysql-data:/var/lib/mysql -v "$PWD/docker/mysql/init:/docker-entrypoint-initdb.d" mysql:8.4.10 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+09:00 \
  --innodb-buffer-pool-size=134217728

./gradlew bootJar -x test
URL='jdbc:mysql://localhost:3307/minicore?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true'
SEED="--spring.profiles.active=local,phase2-seed --spring.main.web-application-type=none --spring.datasource.url=$URL --spring.datasource.username=root --spring.datasource.password=localpw"
java -jar build/libs/corebank-0.0.1-SNAPSHOT.jar $SEED --app.phase2-seed.minimum.execute=true
java -jar build/libs/corebank-0.0.1-SNAPSHOT.jar $SEED --app.phase2-seed.bulk.execute=true

# local 프로필이 GL_READ 권한을 가진 adminreader 관리자 계정을 넣는다
java -Xms1g -Xmx1g -jar build/libs/corebank-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local --server.port=18080 --spring.datasource.url="$URL" \
  --spring.datasource.username=root --spring.datasource.password=localpw

docker exec -i ph28b-perf-mysql mysql -uroot -plocalpw -t minicore < perf/sql/ph28b/null_trade_date.sql
BASE_URL=http://localhost:18080/api/v1 ADMIN_ID=adminreader ADMIN_PW='Admin1234!' \
  perf/sql/ph28b/run_baseline.sh 2026-09-01 2026-10-05 > baseline.tsv
docker exec -i ph28b-perf-mysql mysql -uroot -plocalpw minicore < perf/sql/ph28b/explain_hot_day.sql
perf/sql/ph28b/explain_voucher_totals.sh
```

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-10-22 | 최초 작성. 행렬 11가지, 60b 오탐 기준선 35일, 쿼리 두 개 개선 전후 실행계획 |
