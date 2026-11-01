package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseBatchUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseSummary;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.config.DelayedTransferProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지연이체 해제(PH-06). ID만 꺼내고 실행은 한 건씩 TransferHoldService에 맡겨, 꺼낸 뒤 취소된 건은 조건부 UPDATE가 거른다.
 *
 * 멈춘 실행을 먼저 마무리한다. 같은 고객의 앞선 건이 먼저 끝나야 잔액 판정이 시간 순서와 맞는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferHoldReleaseBatchService implements TransferHoldReleaseBatchUseCase {

    private final TransferHoldPort transferHoldPort;
    private final TransferHoldService transferHoldService;
    private final DelayedTransferProperties properties;
    private final Clock clock;

    @Override
    public TransferHoldReleaseSummary releaseDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> stuck =
                transferHoldPort.findStuckExecutingIds(now.minus(properties.stuckAfter()), properties.batchSize());
        int resumed = runEach(stuck, transferHoldService::resume);
        List<Long> due = transferHoldPort.findReleasableIds(now, properties.batchSize());
        int released = runEach(due, transferHoldService::releaseAsSystem);
        return new TransferHoldReleaseSummary(resumed, released, stuck.size() - resumed + due.size() - released);
    }

    // 한 건의 예외가 나머지를 막지 않는다. 실패한 건은 상태가 그대로라 다음 회차에 다시 본다.
    private static int runEach(List<Long> ids, LongConsumer action) {
        int done = 0;
        for (long id : ids) {
            try {
                action.accept(id);
                done++;
            } catch (RuntimeException e) {
                log.warn("이체 보류 해제를 건너뜀 - transferHoldId={}", id, e);
            }
        }
        return done;
    }
}
