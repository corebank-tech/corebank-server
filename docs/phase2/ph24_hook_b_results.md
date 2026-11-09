# PH-24 훅 B 전후 이체 성능 측정 결과

> 이체 기표 훅(`GlLedgerPostingHook`)이 있는 jar와 뺀 jar를 같은 조건에서 번갈아 돌려 잰 근거와 회차별 원자료다.
> 판정 요약은 [harness_results.md §7](harness_results.md#7-ph-24-훅-b-전후--2026-10-1920-로컬).
> 재측정 시 기존 결과를 지우지 않고 추가한다.

측정: 2026-10-19 ~ 10-20 로컬

> **훅을 켜면 견딤 구간(5~13/s)에서 이체 지연 중앙값이 건당 2.6~15.6ms 늘고, 처리량과 최대 안정 처리량은 그대로다.**
> 포화 구간(15/s)은 회차 간 편차가 ON/OFF 차이보다 커서 판정하지 못했다.

훅은 릴리스 1에 함께 나가 운영에서는 전후를 잴 수 없다. 그래서 [tasks.md PH-24](tasks.md#ph-24-gl-분개-서비스--이체-기표-훅--glapi)대로 P3가 로컬에서 쟀다.

## 1. 목표

허용 저하율 수치 목표는 없다. PH-24 완료 기준(전표 1건, 차대변 일치, 훅 예외 시 롤백)에 성능 조건이 없기 때문이다.
이 측정은 **같은 수준에서 최대 안정 처리량이 줄지 않는지**와 **수준별 건당 추가 지연**만 판정한다.

## 2. 측정 조건

```
대상 코드: dev 764101e9 bootJar 1개
  ON  = 그대로
  OFF = 같은 jar에서 GlLedgerPostingHook.class만 뺌 (LedgerPostingHook 구현체 0개 — 이체 경로는 훅 커밋 adae0637 이전과 같다.
        채번 풀 빈 GlVoucherSequenceAdapter와 JournalPostingService는 남아 쓰이지 않는 커넥션 3개가 열려 있다)
  dev 764101e9는 릴리스 1 이후 머지분(#516 거래일 대입 등)을 포함한다. ON과 OFF에 똑같이 들어 있다
측정 대상: MacBook(Apple Silicon) JVM -Xms1g -Xmx1g, local 프로필 + 아래 명령의 덮어쓰기
  (SQL 로그 끔, 메인 풀 Hikari 10 · connection-timeout 3s · max-lifetime 570s — 운영과 같음. local 기본값은 timeout 30s)
  채번 풀 gl-voucher-sequence 3개는 메인 풀 설정을 복사한다(ON과 OFF 모두)
  local 프로필이라 스케줄러가 켜져 있다. 로그로 확인된 겹침은 13/s OFF 1회차의 16:15 인증 토큰 정리(42,676행 삭제) 1건이고,
  로그를 남기지 않는 정리 작업(매시 정각 등)은 겹쳤는지 확인하지 못했다
DB: Docker MySQL 8.4.10, 버퍼 풀 128MB · binlog 켬 · sync_binlog=1 (운영 RDS db.t4g.micro와 같은 값)
부하 생성: 같은 Mac의 k6 1.7.1 (harness.md §7 권장과 다름, ON/OFF에 같은 조건)
데이터 규모: PH-60 + PH-60b 누적 로컬 적재, 적재 직후 실측 — 고객 60,000 · 계좌 180,000 · 이체 3,135,000
          · 원장 6,500,000 · 전표 3,235,001 · 분개 6,470,002
수준: 5 · 10 · 13 · 15 /s 고정 (사전 탐색: ON 10/s 견딤, 15/s 열화)
회차: 수준마다 6회, 실행 순서 OFF1 → ON1 → ON2 → OFF2 → OFF3 → ON3 · 1회 3분
      · 회차마다 앱을 새로 띄우고 같은 수준 30초 워밍업을 버림
판정: 수준 판정(견딤·열화·포화)은 harness_results.md §3과 같이 회차 중앙값 지표로 한다. 회차별 판정은 표에 함께 둔다
```

커밋 adae0637 앞뒤를 따로 빌드하지 않은 이유: 그 뒤에 들어온 채번 풀 수정(61da2b5f)이 빠져 머지된 훅 최종 코드의 비용이 아니게 된다.

재현 명령이다. DB는 PH-60(`ph60_minimum_seed.md`)과 PH-60b(`ph60b_bulk_seed.md` §9)를 적재한 MySQL을 3307로 띄운 상태다.
두 시드 모두 `--spring.profiles.active=local,phase2-seed`에 각 문서의 실행 플래그(`--app.phase2-seed.minimum.execute=true`, `--app.phase2-seed.bulk.execute=true`)를 붙여 차례로 돌렸다.
`ph60_minimum_seed.md` §1-1의 `phase2-seed` 단독 명령은 `AccountStatusPort` 빈이 없어 지금 기동에 실패한다.

```bash
./gradlew bootJar -x test
cp build/libs/corebank-0.0.1-SNAPSHOT.jar hook-on.jar
cp build/libs/corebank-0.0.1-SNAPSHOT.jar hook-off.jar
zip -d hook-off.jar 'BOOT-INF/classes/com/shinhan/corebank/gl/adapter/in/transfer/GlLedgerPostingHook*.class'

# 회차마다 VARIANT=on|off 로 띄우고, 워밍업(DURATION=30s) 한 번 뒤 harness.md §6 명령(DURATION=3m)을 BASE_URL=http://localhost:18080/api/v1 로 돌린다
java -Xms1g -Xmx1g -jar hook-${VARIANT}.jar \
  --spring.profiles.active=local --server.port=18080 \
  --spring.datasource.url='jdbc:mysql://localhost:3307/minicore?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true' \
  --spring.datasource.username=root --spring.datasource.password=localpw \
  --spring.datasource.hikari.connection-timeout=3000 --spring.datasource.hikari.max-lifetime=570000 \
  --spring.jpa.show-sql=false --logging.level.org.hibernate.SQL=info
```

## 3. 수준별 결과

| 수준 | 훅 | 회차 | 이체 접수/s | 이체 성공/s | 도달률 | med | p95 | p99 | 에러율 | 유실 | 판정 |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 5/s | OFF | 1 | 5.01 | 5.01 | 100.0% | 45ms | 123ms | 317ms | 0.00% | 0 | 견딤 |
| 5/s | OFF | 2 | 5.00 | 4.98 | 100.0% | 257ms | 3324ms | 5566ms | 0.11% | 1 | 포화 |
| 5/s | OFF | 3 | 5.01 | 5.01 | 100.0% | 41ms | 83ms | 260ms | 0.00% | 0 | 견딤 |
| 5/s | ON | 1 | 5.01 | 5.01 | 100.0% | 48ms | 135ms | 415ms | 0.00% | 0 | 견딤 |
| 5/s | ON | 2 | 5.01 | 5.01 | 100.0% | 52ms | 197ms | 418ms | 0.00% | 0 | 견딤 |
| 5/s | ON | 3 | 5.01 | 5.01 | 100.0% | 43ms | 67ms | 118ms | 0.00% | 0 | 견딤 |
| 10/s | OFF | 1 | 10.01 | 10.01 | 100.0% | 58ms | 129ms | 396ms | 0.00% | 0 | 견딤 |
| 10/s | OFF | 2 | 10.01 | 10.01 | 100.0% | 54ms | 92ms | 304ms | 0.00% | 0 | 견딤 |
| 10/s | OFF | 3 | 10.01 | 10.01 | 100.0% | 55ms | 97ms | 361ms | 0.00% | 0 | 견딤 |
| 10/s | ON | 1 | 10.00 | 10.00 | 100.0% | 66ms | 123ms | 427ms | 0.00% | 0 | 견딤 |
| 10/s | ON | 2 | 10.01 | 10.01 | 100.0% | 63ms | 107ms | 399ms | 0.00% | 0 | 견딤 |
| 10/s | ON | 3 | 10.01 | 10.01 | 100.0% | 63ms | 105ms | 396ms | 0.00% | 0 | 견딤 |
| 13/s | OFF | 1 | 12.92 | 12.92 | 100.0% | 85ms | 2315ms | 3342ms | 0.00% | 14 | 포화 |
| 13/s | OFF | 2 | 13.01 | 13.01 | 100.0% | 63ms | 315ms | 671ms | 0.00% | 0 | 견딤 |
| 13/s | OFF | 3 | 13.01 | 13.01 | 100.0% | 53ms | 131ms | 368ms | 0.00% | 0 | 견딤 |
| 13/s | ON | 1 | 12.90 | 12.90 | 100.0% | 940ms | 2963ms | 3866ms | 0.00% | 19 | 포화 |
| 13/s | ON | 2 | 13.01 | 13.01 | 100.0% | 79ms | 218ms | 497ms | 0.00% | 0 | 견딤 |
| 13/s | ON | 3 | 13.01 | 13.01 | 100.0% | 60ms | 192ms | 480ms | 0.00% | 0 | 견딤 |
| 15/s | OFF | 1 | 14.18 | 14.03 | 98.5% | 1520ms | 7574ms | 10204ms | 0.64% | 108 | 포화 |
| 15/s | OFF | 2 | 13.18 | 11.99 | 92.6% | 4377ms | 10802ms | 14439ms | 4.04% | 138 | 포화 |
| 15/s | OFF | 3 | 11.98 | 11.03 | 84.0% | 4936ms | 10504ms | 13591ms | 6.17% | 133 | 포화 |
| 15/s | ON | 1 | 11.53 | 10.02 | 80.6% | 5247ms | 11316ms | 14435ms | 8.32% | 126 | 포화 |
| 15/s | ON | 2 | 9.41 | 5.34 | 66.8% | 8033ms | 14451ms | 17149ms | 18.57% | 164 | 포화 |
| 15/s | ON | 3 | 14.79 | 14.79 | 100.0% | 1090ms | 3564ms | 4720ms | 0.00% | 38 | 포화 |

| 수준 | OFF med | ON med | 차이 | OFF 성공/s | ON 성공/s |
|---:|---:|---:|---:|---:|---:|
| 5/s | 45.1ms | 47.8ms | **+2.6ms** (+5.9%) | 5.01 | 5.01 |
| 10/s | 55.4ms | 63.4ms | **+8.0ms** (+14.4%) | 10.01 | 10.01 |
| 13/s | 62.9ms | 78.5ms | **+15.6ms** (+24.8%) | 13.01 | 13.01 |
| 15/s | 4,377ms | 5,247ms | 판정 불가 | 11.99 | 10.02 |

회차 중앙값이다. 튀는 회차(5/s OFF 2회차, 13/s 1회차의 OFF와 ON)가 있어 평균을 쓰지 않았다. 차이와 퍼센트는 반올림 전 값으로 계산했다.

| 정합성 (전 회차 종료 후) | 결과 |
|---|---|
| ON 회차 성공 이체 수 = 전표 수 | 일치 (15/s ON 6,236 = 6,236) |
| OFF 회차 전표 | 0 |
| 차대변 불일치 전표 | 0 |
| `verify_integrity.sql` 원장 짝 · 잔액 오차 · 한도 적립 합 ≠ 실제 이체액 | 0 · 0 · 0 (10/19~20 전체, 이체 53,365건 중 성공 52,562건) |

53,365건은 표의 24회와 워밍업 말고도 사전 탐색(스모크, 램프업, 10~25/s 탐색)을 포함한다. 이체 행이 생기기 전에 커넥션을 못 받아 실패한 요청은 행이 없어 이 건수에 들어가지 않는다.

한도 적립은 하루 누계(`transfer_limit_daily_usage`)와 비교하므로 ON과 OFF 회차를 나눠 볼 수 없다. 두 날 모두 ON과 OFF가 섞인 하루 전체로 검사했다.

15/s 포화의 근거다. 앱 로그 건수는 워밍업 30초를 포함하고, HTTP 실패와 유실은 측정 3분 기준이다.

| 15/s 회차 | 메인 풀 대기 초과 | 처리되지 않은 예외 | HTTP 실패 | 유실 |
|---|---:|---:|---:|---:|
| OFF 1 · 2 · 3 | 124 · 869 · 1,399 | 55 · 397 · 662 | 67 · 405 · 583 | 108 · 138 · 133 |
| ON 1 · 2 · 3 | 1,773 · 3,620 · 20 | 804 · 1,674 · 9 | 772 · 1,574 · 0 | 126 · 164 · 38 |

풀 대기 초과는 전 회차 모두 메인 풀(`HikariPool-1`)에서 났고 채번 풀(`gl-voucher-sequence`)은 0건이다. HTTP 실패가 난 회차는 같은 규모의 풀 대기 초과와 처리되지 않은 예외를 함께 남겼다. 유실은 풀 대기 초과 없이도 난다 — 15/s ON 3회차(풀 대기 초과 20, HTTP 실패 0, 유실 38)와 13/s OFF 1회차(풀 대기 초과 0, 유실 14)는 지연이 늘어 k6 VU 200개가 모두 묶인 경우다.

## 4. 결론

| 항목 | 내용 |
|---|---|
| **어디까지 견뎠나** | ON과 OFF 모두 **13/s**(회차 중앙값 p95 OFF 315ms · ON 218ms, 유실 0). 회차별로는 둘 다 1회차가 포화였다. 최대 안정 처리량 변화 없음 |
| **어디서부터 이슈인가** | ON과 OFF 모두 15/s에서 3회 전부 포화. 에러는 Hikari 풀 대기 초과에서, 유실은 지연 증가에서 온다(위 근거 표) |
| **저하율** | 견딤 구간 지연 중앙값 +2.6~15.6ms(+5.9~24.8%). p95·p99는 같은 변형끼리의 회차 편차보다 차이가 작아 판정 불가 |
| **포화 구간** | 15/s 성공/s 중앙값 OFF 11.99 → ON 10.02(−16%)이지만 ON 범위가 5.34~14.79로 OFF 범위(11.03~14.03)를 덮는다. 3쌍 중 2쌍에서 ON이 낮았다 |
| **어떻게 개선할 것인가** | 해당 없음. 이 측정은 훅의 비용을 재는 것이고 훅 자체는 개선 대상이 아니다. 15/s 포화는 OFF에서도 나므로 훅 몫이 아니다. 로컬에서는 CPU를 재지 않아 포화의 근본 원인은 진단하지 않았다(운영 진단은 [harness_results.md §3-3](harness_results.md#3-3-그럼-어디가-병목인가--이체-트랜잭션-안)) |
| **상용 기준** | 훅은 견딤 구간의 처리량을 깎지 않아 **충족**. 로컬 절대값은 운영과 달라 옮길 수 있는 것은 상대 비교뿐이다. 추가 지연은 부하가 오를수록 커져(5/s 2.6ms → 13/s 15.6ms) 고정 상수로 옮길 수 없다 |
