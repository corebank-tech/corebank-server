package com.shinhan.corebank.batch.application.service;

import com.shinhan.corebank.batch.api.BatchExecutionHistoryPort;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.BatchStepStatus;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.batch.api.CobStepHaltException;
import com.shinhan.corebank.batch.application.port.in.CobRunnerUseCase;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final BatchExecutionHistoryPort batchExecutionHistoryPort;
    private final Clock clock;
    private final List<CobStep> steps;

    public CobRunnerService(
            BatchExecutionLockPort batchExecutionLockPort,
            BusinessDateProvider businessDateProvider,
            BusinessDateAdvancer businessDateAdvancer,
            BatchExecutionHistoryPort batchExecutionHistoryPort,
            Clock clock,
            List<CobStep> steps) {
        this.batchExecutionLockPort = batchExecutionLockPort;
        this.businessDateProvider = businessDateProvider;
        this.businessDateAdvancer = businessDateAdvancer;
        this.batchExecutionHistoryPort = batchExecutionHistoryPort;
        this.clock = clock;
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
            LocalDateTime startedAt = LocalDateTime.now(clock);
            BatchStepStatus status;
            String failureReason = null;
            boolean halt = false;
            try {
                log.info("COB 스텝 시작 - name={}, order={}", step.name(), step.order());
                step.run(cobDate);
                log.info("COB 스텝 종료 - name={}", step.name());
                status = BatchStepStatus.SUCCESS;
            } catch (CobStepHaltException e) {
                log.error("COB 스텝이 마감 중단을 요청함 - name={}, cobDate={}, 이후 스텝 생략", step.name(), cobDate, e);
                status = BatchStepStatus.HALTED;
                failureReason = e.getMessage();
                halt = true;
            } catch (Exception e) {
                log.error("COB 스텝 실패 - name={}, cobDate={}", step.name(), cobDate, e);
                status = BatchStepStatus.FAILURE;
                failureReason = e.getMessage();
            }
            // 기록은 try/catch 바깥에서 한 번만 - 안에 두면 record() 자체의 실패가 "스텝 실패"로 잘못 다시 잡힌다
            batchExecutionHistoryPort.record(
                    JOB_NAME, step.name(), cobDate, startedAt, LocalDateTime.now(clock), status, failureReason);
            if (halt) {
                break;
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
