package com.shinhan.corebank.subscription.adapter.in.baseline;

import com.shinhan.corebank.subscription.application.port.out.AccumulatedDailyBalanceSqlPort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("ph13-measure")
@ConditionalOnProperty(name = "app.ph13-measure.execute", havingValue = "true")
@EnableConfigurationProperties(Ph13MeasurementProperties.class)
@RequiredArgsConstructor
public class Ph13MeasurementRunner implements ApplicationRunner {

    private final AccumulatedDailyBalanceSqlPort sqlPort;
    private final Ph13MeasurementProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        MeasurementResult result = measure();

        log.info(
                """
                PH-13 measurement completed
                accountIdStart={}
                accountCount={}
                batchSize={}
                batchCount={}
                fromInclusive={}
                toExclusive={}
                totalSeconds={}
                averageBatchMs={}
                p95BatchMs={}
                p99BatchMs={}
                checksum={}
                zeroResultCount={}
                fingerprintSha256={}
                """,
                properties.accountIdStart(),
                properties.accountCount(),
                properties.batchSize(),
                result.batchCount(),
                properties.fromInclusive(),
                properties.toExclusive(),
                format(result.totalSeconds()),
                format(result.averageBatchMs()),
                format(result.p95BatchMs()),
                format(result.p99BatchMs()),
                result.checksum(),
                result.zeroResultCount(),
                result.fingerprintSha256());
    }

    MeasurementResult measure() {
        int accountCount = properties.accountCount();
        int batchSize = properties.batchSize();

        int batchCount = (int) ((accountCount + (long) batchSize - 1) / batchSize);

        long[] elapsedNanos = new long[batchCount];

        long checksum = 0L;
        int zeroResultCount = 0;

        long totalStart = System.nanoTime();

        AccumulatedBalanceFingerprint fingerprint = new AccumulatedBalanceFingerprint();

        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int offset = batchIndex * batchSize;
            int size = Math.min(batchSize, accountCount - offset);

            List<Long> accountIds = new ArrayList<>(size);

            for (int i = 0; i < size; i++) {
                accountIds.add(properties.accountIdStart() + offset + i);
            }

            long batchStart = System.nanoTime();

            Map<Long, Long> results =
                    sqlPort.calculate(accountIds, properties.fromInclusive(), properties.toExclusive());

            elapsedNanos[batchIndex] = System.nanoTime() - batchStart;

            if (results == null) {
                throw new IllegalStateException("SQL 집계 결과가 null입니다.");
            }

            for (Long accountId : accountIds) {
                Long balance = results.get(accountId);

                if (balance == null) {
                    throw new IllegalStateException("적수 집계 결과 누락: accountId=" + accountId);
                }

                checksum = Math.addExact(checksum, balance);
                fingerprint.update(accountId, balance);

                if (balance == 0L) {
                    zeroResultCount++;
                }
            }

            log.info(
                    "PH-13 progress: batch={}/{}, accounts={}/{}",
                    batchIndex + 1,
                    batchCount,
                    offset + size,
                    accountCount);
        }

        long totalNanos = System.nanoTime() - totalStart;

        long[] sorted = elapsedNanos.clone();
        Arrays.sort(sorted);

        return new MeasurementResult(
                totalNanos / 1_000_000_000.0,
                Arrays.stream(elapsedNanos).average().orElse(0.0) / 1_000_000.0,
                percentile(sorted, 0.95) / 1_000_000.0,
                percentile(sorted, 0.99) / 1_000_000.0,
                checksum,
                zeroResultCount,
                batchCount,
                fingerprint.hex());
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[Math.max(index, 0)];
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    record MeasurementResult(
            double totalSeconds,
            double averageBatchMs,
            double p95BatchMs,
            double p99BatchMs,
            long checksum,
            int zeroResultCount,
            int batchCount,
            String fingerprintSha256) {}
}
