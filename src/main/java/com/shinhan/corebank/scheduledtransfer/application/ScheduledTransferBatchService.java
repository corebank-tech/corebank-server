package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferBatchUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferBatchQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledTransferBatchService implements ScheduledTransferBatchUseCase {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferBatchService.class);

    private final ScheduledTransferBatchQueryPort scheduledTransferBatchQueryPort;
    private final ScheduledTransferBatchItemProcessor scheduledTransferBatchItemProcessor;

    @Override
    public void executeDaily(LocalDate date) {
        List<ScheduledTransfer> targets = scheduledTransferBatchQueryPort.findDueForExecution(date);
        for (ScheduledTransfer scheduledTransfer : targets) {
            processOne(scheduledTransfer, date);
        }
    }

    @Override
    public void reconcileStuckExecutions(LocalDate date) {
        List<ScheduledTransfer> stuck = scheduledTransferBatchQueryPort.findAllProcessing();
        for (ScheduledTransfer scheduledTransfer : stuck) {
            reconcileOne(scheduledTransfer, date);
        }
    }

    // 건별 예외 격리: 한 건의 실패/중복이 나머지 건 처리를 막으면 안됨
    private void processOne(ScheduledTransfer scheduledTransfer, LocalDate date) {
        boolean claimed;
        try {
            claimed = scheduledTransferBatchItemProcessor.claim(scheduledTransfer.getScheduledTransferId());
        } catch (Exception e) {
            log.error("PROCESSING 선점 실패 - scheduledTransferId={}, date={}",
                    scheduledTransfer.getScheduledTransferId(), date, e);
            return;
        }
        if (!claimed) {
            log.info("이미 다른 배치 실행이 선점함(중복 수행 방어) - scheduledTransferId={}, date={}",
                    scheduledTransfer.getScheduledTransferId(), date);
            return;
        }

        try {
            scheduledTransferBatchItemProcessor.completeProcessing(scheduledTransfer, date);
        } catch (Exception e) {
            log.error("예약이체 배치 처리 실패 - scheduledTransferId={}, date={}",
                    scheduledTransfer.getScheduledTransferId(), date, e);
        }
    }

    // 재확정 건별 예외 격리 - 한 건의 재확정 실패가 나머지 건을 막으면 안됨
    private void reconcileOne(ScheduledTransfer scheduledTransfer, LocalDate date) {
        try {
            scheduledTransferBatchItemProcessor.reconcileStuckExecution(scheduledTransfer);
        } catch (Exception e) {
            log.error("재확정 배치 처리 실패 - scheduledTransferId={}, date={}",
                    scheduledTransfer.getScheduledTransferId(), date, e);
        }
    }
}
