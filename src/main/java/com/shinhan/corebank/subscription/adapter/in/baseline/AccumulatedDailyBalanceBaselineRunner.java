package com.shinhan.corebank.subscription.adapter.in.baseline;

import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import java.util.Arrays;
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
@Profile("ph11-baseline")
@ConditionalOnProperty(name = "app.ph11-baseline.execute", havingValue = "true")
@EnableConfigurationProperties(AccumulatedDailyBalanceBaselineProperties.class)
@RequiredArgsConstructor
public class AccumulatedDailyBalanceBaselineRunner implements ApplicationRunner {

    private final AccumulatedDailyBalanceUseCase accumulatedDailyBalanceUseCase;
    private final AccumulatedDailyBalanceBaselineProperties properties;

    // 1. Spring이 러너를 실행하면 가장 먼저 여기로 들어옴
    @Override
    public void run(ApplicationArguments args) {
        BaselineResult result = measure();

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
                """,
                properties.accountIdStart(),
                properties.accountCount(),
                properties.fromInclusive(),
                properties.toExclusive(),
                result.totalSeconds(),
                result.averageMs(),
                result.p95Ms(),
                result.p99Ms(),
                result.checksum());
    }

    // 2. 실제 3만/15만 계좌를 돌면서 시간을 재는 부분
    BaselineResult measure() {

        long[] elapsedNanos = new long[properties.accountCount()];

        long checksum = 0L;
        long totalStart = System.nanoTime();

        for (int i = 0; i < properties.accountCount(); i++) {
            long accountId = properties.accountIdStart() + i;

            long start = System.nanoTime();

            long accumulatedDailyBalance = accumulatedDailyBalanceUseCase.calculate(
                    accountId, properties.fromInclusive(), properties.toExclusive());

            elapsedNanos[i] = System.nanoTime() - start;
            checksum = Math.addExact(checksum, accumulatedDailyBalance);
        }

        long totalElapsedNanos = System.nanoTime() - totalStart;

        long[] sorted = elapsedNanos.clone();
        Arrays.sort(sorted);

        return new BaselineResult(
                totalElapsedNanos / 1_000_000_000.0,
                Arrays.stream(elapsedNanos).average().orElse(0.0) / 1_000_000.0,
                percentile(sorted, 0.95) / 1_000_000.0,
                percentile(sorted, 0.99) / 1_000_000.0,
                checksum);
    }

    // 3. p95, p99 계산용 메서드
    private long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[Math.max(index, 0)];
    }

    // 4. 측정 결과를 묶어두는 객체
    record BaselineResult(double totalSeconds, double averageMs, double p95Ms, double p99Ms, long checksum) {}
}
