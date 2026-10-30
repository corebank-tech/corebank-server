package com.shinhan.corebank.transfer.adapter.in.baseline;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.service.LedgerReconciliationBatchService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * PH-38 300만 원장 전수 대사 시간 베이스라인을 운영 DB에서 잰다(#744).
 *
 * COB 대사 스텝과 같은 reconcileAll()을 같은 락 안에서 돌린다. runFull()은 락을 못 잡거나 예외가 나도
 * 조용히 넘어가 0초를 측정값으로 남길 수 있어 쓰지 않는다. 결과는 읽기만 하고 불일치 테이블·영업일은 건드리지 않는다.
 */
@Slf4j
@Component
@Profile("ph38-baseline")
@ConditionalOnProperty(name = "app.ph38-baseline.execute", havingValue = "true")
@EnableConfigurationProperties(LedgerFullReconciliationBaselineProperties.class)
@RequiredArgsConstructor
public class LedgerFullReconciliationBaselineRunner implements ApplicationRunner {

    private static final String JOB_NAME = LedgerReconciliationBatchService.JOB_NAME;

    private final LedgerFullReconciliationUseCase ledgerFullReconciliationUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final LedgerFullReconciliationBaselineProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        BaselineResult result = measure();
        log.info(
                """
            PH-38 baseline completed
            rounds={}
            warmupRounds={}
            warmupElapsedMs={}
            measuredElapsedMs={}
            medianMs={}
            minMs={}
            maxMs={}
            mismatchCounts={}
            """,
                properties.rounds(),
                properties.warmupRounds(),
                result.warmupElapsedMs(),
                result.measuredElapsedMs(),
                result.medianMs(),
                result.minMs(),
                result.maxMs(),
                result.mismatchCounts());
    }

    BaselineResult measure() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            throw new IllegalStateException("다른 대사가 실행 중이라 측정하지 않는다 - jobName=" + JOB_NAME);
        }
        List<Long> elapsedMs = new ArrayList<>();
        List<Integer> mismatchCounts = new ArrayList<>();
        try {
            for (int round = 1; round <= properties.rounds(); round++) {
                long startedAt = System.nanoTime();
                int mismatches = ledgerFullReconciliationUseCase.reconcileAll().size();
                long roundMs = (System.nanoTime() - startedAt) / 1_000_000;
                elapsedMs.add(roundMs);
                mismatchCounts.add(mismatches);
                log.info(
                        "PH-38 baseline round {}/{}{} - elapsedMs={}, mismatches={}",
                        round,
                        properties.rounds(),
                        round <= properties.warmupRounds() ? " (예열)" : "",
                        roundMs,
                        mismatches);
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
        return BaselineResult.of(elapsedMs, mismatchCounts, properties.warmupRounds());
    }

    record BaselineResult(
            List<Long> warmupElapsedMs,
            List<Long> measuredElapsedMs,
            double medianMs,
            long minMs,
            long maxMs,
            List<Integer> mismatchCounts) {

        static BaselineResult of(List<Long> elapsedMs, List<Integer> mismatchCounts, int warmupRounds) {
            List<Long> warmup = List.copyOf(elapsedMs.subList(0, warmupRounds));
            List<Long> measured = List.copyOf(elapsedMs.subList(warmupRounds, elapsedMs.size()));
            List<Long> sorted = measured.stream().sorted().toList();
            int middle = sorted.size() / 2;
            double median =
                    sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
            return new BaselineResult(
                    warmup, measured, median, sorted.getFirst(), sorted.getLast(), List.copyOf(mismatchCounts));
        }
    }
}
