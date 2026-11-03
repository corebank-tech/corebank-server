# LOCK-TUNE 채번 행 락 측정 원자료 (#786)

PR #837 본문 "측정" 표의 원본 출력입니다. [tasks.md LOCK-TUNE](tasks.md#lock-tune-락-튜닝)과 PH-30 S4 리포트의 "행 락 대기 4,999회 → 0회" 출처가 이 문서입니다.

> **로컬 마이크로벤치마크(맥북 Docker MySQL), 운영 값 아님.** 이 수치를 인용할 때는 이 문구를 같이 적습니다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 대상 | 실제 `SequenceGenerator.nextTransactionNumber()` 직접 호출 (HTTP·이체 로직·k6 없음) |
| 환경 | 맥북 8코어, Docker Desktop, Testcontainers `mysql:8.4.10` (기본 디스크 기록 설정, 측정 전용 컨테이너) |
| 커넥션 풀 | Hikari 80 (풀 대기를 빼고 락만 재려고 키움. 운영 풀 10보다 적은 8스레드에서 이미 처리량이 약 440건/초에서 멈춤. 운영 풀 포화는 재지 않음) |
| 회차 | 워밍업 200건 → 단일 스레드 2,000건(M1) → 스레드 1·8·32·64개마다 5,000건 × 3회(M2·M3) → 2건 채번 general log(M4) |
| 채번 키 | `seq_date=2099-01-01`, `channel=WB` (시드와 겹치지 않는 날짜) |
| 개선 전 | 커밋 `a91ecd07`, 2026-11-01 22:01:02 KST 실행 |
| 개선 후 | 커밋 `6e0951be`, 2026-11-01 22:14:19 KST 실행, `app.transaction-sequence.block-size=100` |

## 각 값의 뜻

- `throughput`: 5,000건 ÷ 회차 경과 시간
- `p50·p95·p99`: 호출 1건 앞뒤 `System.nanoTime()` 차이의 백분위
- `rowLockWaits` · `rowLockTimeMs` (M3): 회차 앞뒤 `SHOW GLOBAL STATUS`의 `Innodb_row_lock_waits` · `Innodb_row_lock_time` 차이
- 4,999 = 회차 동안 InnoDB가 센 행 락 대기 횟수(전역 카운터 차이). 호출마다 잰 값이 아니라서 "몇 건이 기다렸다"가 아니라 "대기가 4,999번 있었다"로 읽습니다
- M4: MySQL general log에 찍힌 2건 채번의 실제 쿼리

## 개선 전 출력 (a91ecd07)

```
commit=a91ecd07 start=2026-11-01 22:01:02
M1 single thread 2000 calls: p50=3.987ms p95=7.304ms p99=11.683ms
M2 threads=1 round=1 calls=5000 throughput=230/s p50=3.568ms p95=8.169ms p99=14.254ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=1 round=2 calls=5000 throughput=299/s p50=2.896ms p95=5.802ms p99=9.666ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=1 round=3 calls=5000 throughput=311/s p50=2.821ms p95=5.432ms p99=8.939ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=8 round=1 calls=5000 throughput=448/s p50=16.924ms p95=24.612ms p99=32.098ms | M3 rowLockWaits=4999 rowLockTimeMs=71021
M2 threads=8 round=2 calls=5000 throughput=420/s p50=17.630ms p95=27.324ms p99=36.372ms | M3 rowLockWaits=4999 rowLockTimeMs=75761
M2 threads=8 round=3 calls=5000 throughput=408/s p50=18.172ms p95=28.097ms p99=42.185ms | M3 rowLockWaits=4999 rowLockTimeMs=78054
M2 threads=32 round=1 calls=5000 throughput=398/s p50=75.035ms p95=112.098ms p99=171.226ms | M3 rowLockWaits=4999 rowLockTimeMs=379474
M2 threads=32 round=2 calls=5000 throughput=446/s p50=69.793ms p95=86.964ms p99=121.286ms | M3 rowLockWaits=4999 rowLockTimeMs=339230
M2 threads=32 round=3 calls=5000 throughput=449/s p50=68.957ms p95=82.769ms p99=135.824ms | M3 rowLockWaits=4999 rowLockTimeMs=336920
M2 threads=64 round=1 calls=5000 throughput=419/s p50=145.936ms p95=202.088ms p99=275.021ms | M3 rowLockWaits=4999 rowLockTimeMs=740299
M2 threads=64 round=2 calls=5000 throughput=435/s p50=142.959ms p95=190.465ms p99=217.425ms | M3 rowLockWaits=4999 rowLockTimeMs=712152
M2 threads=64 round=3 calls=5000 throughput=439/s p50=141.421ms p95=182.322ms p99=215.289ms | M3 rowLockWaits=4999 rowLockTimeMs=703508
M4 general log for 2 calls:
  [10] Query SET autocommit=0
  [10] Query select tsje1_0.channel,tsje1_0.seq_date,tsje1_0.last_seq,tsje1_0.updated_at from transaction_sequence tsje1_0 where tsje1_0.seq_date='2099-01-01' and tsje1_0.ch
  [10] Query SELECT @@session.transaction_read_only
  [10] Query update transaction_sequence set last_seq=62201,updated_at='2026-11-01 22:04:29.248054' where channel='WB' and seq_date='2099-01-01'
  [10] Query COMMIT
  [10] Query SET autocommit=1
  [10] Query SET autocommit=0
  [10] Query select tsje1_0.channel,tsje1_0.seq_date,tsje1_0.last_seq,tsje1_0.updated_at from transaction_sequence tsje1_0 where tsje1_0.seq_date='2099-01-01' and tsje1_0.ch
  [10] Query SELECT @@session.transaction_read_only
  [10] Query update transaction_sequence set last_seq=62202,updated_at='2026-11-01 22:04:29.250899' where channel='WB' and seq_date='2099-01-01'
  [10] Query COMMIT
  [10] Query SET autocommit=1
  [117] Query SET GLOBAL general_log=0
```

## 개선 후 출력 (6e0951be, 묶음 100)

```
commit=6e0951be start=2026-11-01 22:14:19 block=100
M1 single thread 2000 calls: p50=0.002ms p95=0.005ms p99=0.105ms
M2 threads=1 round=1 calls=5000 throughput=19346/s p50=0.001ms p95=0.002ms p99=0.053ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=1 round=2 calls=5000 throughput=18117/s p50=0.001ms p95=0.002ms p99=0.067ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=1 round=3 calls=5000 throughput=22287/s p50=0.001ms p95=0.001ms p99=0.027ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=8 round=1 calls=5000 throughput=10363/s p50=0.001ms p95=0.004ms p99=33.080ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=8 round=2 calls=5000 throughput=10327/s p50=0.001ms p95=0.003ms p99=29.413ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=8 round=3 calls=5000 throughput=17303/s p50=0.001ms p95=0.003ms p99=16.407ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=32 round=1 calls=5000 throughput=18921/s p50=0.001ms p95=0.003ms p99=56.693ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=32 round=2 calls=5000 throughput=21545/s p50=0.001ms p95=0.003ms p99=52.212ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=32 round=3 calls=5000 throughput=22106/s p50=0.001ms p95=0.003ms p99=51.551ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=64 round=1 calls=5000 throughput=22090/s p50=0.001ms p95=0.010ms p99=101.267ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=64 round=2 calls=5000 throughput=21000/s p50=0.001ms p95=0.002ms p99=113.611ms | M3 rowLockWaits=0 rowLockTimeMs=0
M2 threads=64 round=3 calls=5000 throughput=22540/s p50=0.001ms p95=0.004ms p99=91.234ms | M3 rowLockWaits=0 rowLockTimeMs=0
M4 general log for 2 calls:
  [10] Query SET autocommit=0
  [10] Query select tsje1_0.channel,tsje1_0.seq_date,tsje1_0.last_seq,tsje1_0.updated_at from transaction_sequence tsje1_0 where tsje1_0.seq_date='2099-01-01' and tsje1_0.ch
  [10] Query SELECT @@session.transaction_read_only
  [10] Query update transaction_sequence set last_seq=62300,updated_at='2026-11-01 22:14:59.189832' where channel='WB' and seq_date='2099-01-01'
  [10] Query COMMIT
  [10] Query SET autocommit=1
  [117] Query SET GLOBAL general_log=0
```

## 요약 (3회 중앙값)

| 동시 스레드 | 처리량 전 | 처리량 후 (참고값) | 행 락 대기 횟수 (5,000건 회차) 전 → 후 |
|---|---:|---:|---|
| 1 | 299건/초 | 19,346건/초 | 0 → 0 |
| 8 | 420건/초 | 10,363건/초 | 4,999 → 0 |
| 32 | 446건/초 | 21,545건/초 | 4,999 → 0 |
| 64 | 435건/초 | 22,090건/초 | 4,999 → 0 |

- 개선 후 general log: 2건을 받는 동안 DB 트랜잭션이 1번(`last_seq=62300`, 100개 예약)이고, 개선 전은 2번입니다.
- 개선 후 처리량은 DB를 거의 거치지 않아 JVM 잠금과 측정 코드 자체를 잰 값에 가깝고, 회차 간 흔들림이 큽니다(8스레드 10,363~17,303). "채번이 더 이상 상한이 아니다"까지만 말합니다.

## 한계

- 맥북 Docker MySQL이라 락 1회 시간이 RDS와 다릅니다. 운영 값은 재지 않았습니다.
- 측정 테스트는 저장소에 커밋하지 않은 임시 코드입니다. 아래 코드가 실행 당시 파일이라 고치지 않고 둡니다.
- 이 코드는 `--tests`로 혼자 돌리는 전제입니다. 중간에 실패하면 `general_log` 설정과 스레드 풀을 되돌리지 않으므로, 다른 테스트와 같이 돌리지 않습니다.
- 서버 2대 처리량, 이체 1건 전체 시간 변화는 재지 않았습니다.

## 재현

```bash
# 아래 파일을 src/test/java/com/shinhan/corebank/transfer/adapter/out/persistence/ 에 두고
git checkout a91ecd07   # 개선 전 (개선 후는 6e0951be, @TestPropertySource에 block-size=100 추가)
./gradlew test --tests "*SequenceLockMeasureTest"
cat /tmp/m786.txt   # 결과는 이 파일에 이어 쓰입니다
```

## 측정 테스트 코드 (SequenceLockMeasureTest.java, 개선 전 실행본)

```java
package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.test.context.TestPropertySource;

// #786 개선 전·후 측정용 임시 테스트. 커밋하지 않는다.
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=80")
class SequenceLockMeasureTest extends IntegrationTestSupport {

    private static final LocalDate SEQ_DATE = LocalDate.of(2099, 1, 1);
    private static final TransferChannel CHANNEL = TransferChannel.WB;
    private static final String OUT = System.getProperty("m786.out", "/tmp/m786.txt");

    @Autowired
    private SequenceGenerator sequenceGenerator;

    @Autowired
    private JdbcConnectionDetails connectionDetails;

    @Test
    void measure() throws Exception {
        try (PrintWriter out = new PrintWriter(new FileWriter(OUT, true))) {
            for (int i = 0; i < 200; i++) {
                sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
            }

            long[] single = run(1, 2000);
            out.printf("M1 single thread 2000 calls: p50=%.3fms p95=%.3fms p99=%.3fms%n",
                    pct(single, 50), pct(single, 95), pct(single, 99));

            for (int threads : new int[] {1, 8, 32, 64}) {
                for (int round = 1; round <= 3; round++) {
                    long[] before = rowLockStatus();
                    long start = System.nanoTime();
                    long[] lat = run(threads, 5000);
                    double sec = (System.nanoTime() - start) / 1e9;
                    long[] after = rowLockStatus();
                    out.printf(
                            "M2 threads=%d round=%d calls=5000 throughput=%.0f/s p50=%.3fms p95=%.3fms p99=%.3fms"
                                    + " | M3 rowLockWaits=%d rowLockTimeMs=%d%n",
                            threads, round, 5000 / sec, pct(lat, 50), pct(lat, 95), pct(lat, 99),
                            after[0] - before[0], after[1] - before[1]);
                }
            }

            out.println("M4 general log for 2 calls:");
            try (Connection root = rootConnection(); Statement st = root.createStatement()) {
                st.execute("TRUNCATE mysql.general_log");
                st.execute("SET GLOBAL log_output='TABLE'");
                st.execute("SET GLOBAL general_log=1");
                sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
                sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
                st.execute("SET GLOBAL general_log=0");
                try (ResultSet rs = st.executeQuery(
                        "SELECT thread_id, command_type, LEFT(CAST(argument AS CHAR), 160) FROM mysql.general_log"
                                + " WHERE command_type IN ('Query','Execute','Prepare') ORDER BY event_time")) {
                    while (rs.next()) {
                        out.printf("  [%d] %s %s%n", rs.getLong(1), rs.getString(2), rs.getString(3));
                    }
                }
            }
            out.flush();
        } finally {
            try (Connection root = rootConnection(); Statement st = root.createStatement()) {
                st.execute("DELETE FROM transaction_sequence WHERE seq_date='2099-01-01'");
            }
        }
    }

    private long[] run(int threads, int total) throws Exception {
        long[] latencies = new long[total];
        AtomicInteger next = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                int i;
                while ((i = next.getAndIncrement()) < total) {
                    long s = System.nanoTime();
                    sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
                    latencies[i] = System.nanoTime() - s;
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();
        return latencies;
    }

    private static double pct(long[] values, int p) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(idx, 0)] / 1e6;
    }

    private long[] rowLockStatus() throws Exception {
        long waits = 0;
        long time = 0;
        try (Connection root = rootConnection();
                Statement st = root.createStatement();
                ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_%'")) {
            while (rs.next()) {
                if ("Innodb_row_lock_waits".equals(rs.getString(1))) {
                    waits = rs.getLong(2);
                } else if ("Innodb_row_lock_time".equals(rs.getString(1))) {
                    time = rs.getLong(2);
                }
            }
        }
        return new long[] {waits, time};
    }

    private Connection rootConnection() throws Exception {
        return DriverManager.getConnection(
                connectionDetails.getJdbcUrl(), "root", connectionDetails.getPassword());
    }
}
```
