package com.shinhan.corebank.batch.application.service;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.batch.application.port.in.CobRunnerUseCase;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CobRunnerService implements CobRunnerUseCase {
    private static final Logger log = LoggerFactory.getLogger(CobRunnerService.class);
    private static final String JOB_NAME = "COB";

    private final BatchExecutionLockPort batchExecutionLockPort;
    private final BusinessDateProvider businessDateProvider;
    private final BusinessDateAdvancer businessDateAdvancer;
    private final List<CobStep> steps;

    public CobRunnerService(
            BatchExecutionLockPort batchExecutionLockPort,
            BusinessDateProvider businessDateProvider,
            BusinessDateAdvancer businessDateAdvancer,
            List<CobStep> steps) {
        this.batchExecutionLockPort = batchExecutionLockPort;
        this.businessDateProvider = businessDateProvider;
        this.businessDateAdvancer = businessDateAdvancer;
        this.steps =
                steps.stream().sorted(Comparator.comparingInt(CobStep::order)).collect(Collectors.toList());
        rejectDuplicateOrder(this.steps);
    }

    @Override
    public void run(LocalDate calendarDate) {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 COB가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            runSteps(calendarDate);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }

    private void runSteps(LocalDate calendarDate) {
        LocalDate cobDate = businessDateProvider.today();
        if (cobDate.isAfter(calendarDate)) {
            log.info("영업일이 달력보다 미래라 COB를 건너뜀 - businessDate={}, calendarDate={}", cobDate, calendarDate);
            return;
        }

        LocalDate nextBusinessDate = businessDateAdvancer.advanceFrom(cobDate);
        log.info("COB 시작 - cobDate={}, nextBusinessDate={}", cobDate, nextBusinessDate);

        for (CobStep step : steps) {
            try {
                log.info("COB 스텝 시작 - name={}, order={}", step.name(), step.order());
                step.run(cobDate);
                log.info("COB 스텝 종료 - name={}", step.name());
            } catch (Exception e) {
                log.error("COB 스텝 실패 - name={}, cobDate={}", step.name(), cobDate, e);
            }
        }
        log.info("COB 종료 - cobDate={}", cobDate);
    }

    private static void rejectDuplicateOrder(List<CobStep> steps) {
        Map<Integer, List<String>> byOrder = steps.stream()
                .collect(Collectors.groupingBy(CobStep::order, Collectors.mapping(CobStep::name, Collectors.toList())));
        byOrder.values().stream().filter(names -> names.size() > 1).findFirst().ifPresent(dup -> {
            throw new IllegalStateException("CobStep order 중복: " + dup);
        });
    }
}
