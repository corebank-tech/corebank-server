package com.shinhan.corebank.subscription.adapter.in.baseline;

import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import java.util.Arrays;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("ph11-baseline")
@ConditionalOnProperty(name = "app.ph11-baseline.execute", havingValue = "true")
@EnableConfigurationProperties(AccumulatedDailyBalanceBaselineProperties.class)
public class AccumulatedDailyBalanceBaselineRunner implements ApplicationRunner {

    private final AccumulatedDailyBalanceUseCase accumulatedDailyBalanceUseCase;
    private final AccumulatedDailyBalanceBaselineProperties properties;
    private final Environment environment;

    public AccumulatedDailyBalanceBaselineRunner(
            AccumulatedDailyBalanceUseCase accumulatedDailyBalanceUseCase,
            AccumulatedDailyBalanceBaselineProperties properties,
            Environment environment) {

        this.accumulatedDailyBalanceUseCase = accumulatedDailyBalanceUseCase;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (environment.getProperty("app.ph13.sql-enabled", Boolean.class, false)) {
            throw new IllegalStateException("PH-11 기준선 측정에서는 app.ph13.sql-enabled=false여야 합니다.");
        }

        BaselineResult result = measure();

        String totalSecondsForLog = String.format(Locale.ROOT, "%.3f", result.totalSeconds());
        String averageMsForLog = String.format(Locale.ROOT, "%.3f", result.averageMs());
        String p95MsForLog = String.format(Locale.ROOT, "%.3f", result.p95Ms());
        String p99MsForLog = String.format(Locale.ROOT, "%.3f", result.p99Ms());
        String checksumForLog = String.format(Locale.ROOT, "%,d", result.checksum());

        log.info(
                """
            PH-11 baseline completed
            accountIdStart={}
            accountCount={}
            fromInclusive={}
            toExclusive={}
            totalSeconds={}
            averageMs={}
            p95Ms={}
            p99Ms={}
            checksum={}
            zeroResultCount={}
            fingerprintSha256={}
            """,
                properties.accountIdStart(),
                properties.accountCount(),
                properties.fromInclusive(),
                properties.toExclusive(),
                totalSecondsForLog,
                averageMsForLog,
                p95MsForLog,
                p99MsForLog,
                checksumForLog,
                result.zeroResultCount(),
                result.fingerprintSha256());
    }

    BaselineResult measure() {

        long[] elapsedNanos = new long[properties.accountCount()];

        long checksum = 0L;
        int zeroResultCount = 0;
        AccumulatedBalanceFingerprint fingerprint = new AccumulatedBalanceFingerprint();

        long totalStart = System.nanoTime();

        for (int i = 0; i < properties.accountCount(); i++) {
            long accountId = properties.accountIdStart() + i;

            long start = System.nanoTime();

            long accumulatedDailyBalance = accumulatedDailyBalanceUseCase.calculate(
                    accountId, properties.fromInclusive(), properties.toExclusive());

            elapsedNanos[i] = System.nanoTime() - start;

            if (accumulatedDailyBalance == 0L) {
                zeroResultCount++;
            }

            checksum = Math.addExact(checksum, accumulatedDailyBalance);

            fingerprint.update(accountId, accumulatedDailyBalance);
        }

        long totalElapsedNanos = System.nanoTime() - totalStart;

        long[] sorted = elapsedNanos.clone();
        Arrays.sort(sorted);

        return new BaselineResult(
                totalElapsedNanos / 1_000_000_000.0,
                Arrays.stream(elapsedNanos).average().orElse(0.0) / 1_000_000.0,
                percentile(sorted, 0.95) / 1_000_000.0,
                percentile(sorted, 0.99) / 1_000_000.0,
                checksum,
                zeroResultCount,
                fingerprint.hex());
    }

    private long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[Math.max(index, 0)];
    }

    record BaselineResult(
            double totalSeconds,
            double averageMs,
            double p95Ms,
            double p99Ms,
            long checksum,
            int zeroResultCount,
            String fingerprintSha256) {}
}
