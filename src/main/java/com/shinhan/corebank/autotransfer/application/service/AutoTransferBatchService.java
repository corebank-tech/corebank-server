package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferBatchQueryPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.StuckExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// 반복문 각 건이 AutoTransferBatchItemProcessor의 REQUIRES_NEW로 독립적으로 처리된다.
// 여기에 트랜잭션을 걸면 배치가 도는 내내 커넥션 하나를 붙잡고 있게 되어 커넥션 풀을 고갈시킬 수 잇다.
@Service
@RequiredArgsConstructor
public class AutoTransferBatchService implements AutoTransferBatchUseCase {
    private static final Logger log = LoggerFactory.getLogger(AutoTransferBatchService.class);

    private final AutoTransferBatchQueryPort autoTransferBatchQueryPort;
    private final AutoTransferBatchItemProcessor autoTransferBatchItemProcessor;
    private final AutoTransferExecutionPersistencePort autoTransferExecutionPersistencePort;

    @Override
    public void executeDaily(LocalDate date) {
        List<AutoTransfer> targets = autoTransferBatchQueryPort.findDueForExecution(date);
        for (AutoTransfer autoTransfer : targets) {
            processOne(autoTransfer, date);
        }
    }

    @Override
    public void reconcileStuckExecutions(LocalDate date) {
        List<StuckExecution> stuckExecutions = autoTransferExecutionPersistencePort.findAllProcessing();
        for (StuckExecution stuckExecution : stuckExecutions) {
            reconcileOne(stuckExecution, date);
        }
    }

    // 건별 예외 격리: 한 건의 실패/중복이 나머지 건 처리를 막으면 안됨
    private void processOne(AutoTransfer autoTransfer, LocalDate date) {
        AutoTransferExecution saved;
        try {
            saved = autoTransferBatchItemProcessor.saveProcessing(autoTransfer);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_ate_dup")) {
                log.info("이미 처리된 회차 - autoTransferId={}, date={}", autoTransfer.getAutoTransferId(), date);
            } else {
                log.error("PROCESSING 저장 실패 - autoTransferId={}, date={}", autoTransfer.getAutoTransferId(), date, e);
            }
            return;
        } catch (Exception e) {
            log.error("PROCESSING 저장 실패 - autoTransferId={}, date={}", autoTransfer.getAutoTransferId(), date, e);
            return;
        }

        try {
            autoTransferBatchItemProcessor.completeProcessing(autoTransfer, saved, date);
        } catch (Exception e) {
            log.error("자동이체 배치 처리 실패 - autoTransferId={}, date={}", autoTransfer.getAutoTransferId(), date, e);
        }
    }

    // 재확정 건별 예외 격리 - 한 건의 재확정 실패가 나머지 건을 막으면 안됨
    private void reconcileOne(StuckExecution stuckExecution, LocalDate date) {
        Long autoTransferId = stuckExecution.autoTransfer().getAutoTransferId();
        try {
            autoTransferBatchItemProcessor.reconcileStuckExecution(stuckExecution);
        } catch (Exception e) {
            log.error("재확정 배치 처리 실패 - autoTransferId={}, executionId={}, date={}", autoTransferId,
                    stuckExecution.execution().getExecutionId(), date, e);
        }
    }
}
