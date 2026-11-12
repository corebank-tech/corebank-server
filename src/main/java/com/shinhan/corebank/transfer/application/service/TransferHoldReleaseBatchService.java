package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseBatchUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseSummary;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.config.DelayedTransferProperties;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지연이체 해제(PH-06). ID만 꺼내고 실행은 한 건씩 TransferHoldService에 맡겨, 꺼낸 뒤 취소된 건은 조건부 UPDATE가 거른다.
 *
 * 멈춘 실행을 먼저 마무리한다. 같은 고객의 앞선 건이 먼저 끝나야 잔액 판정이 시간 순서와 맞는다.
 * 마지막으로 보류한 날이 지난 승인 대기(FDS) 보류를 EXPIRED로 닫는다(#998).
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
        int resumed = runEach(stuck, id -> transferHoldService.resume(id, properties.stuckAfter()));
        List<Long> due = transferHoldPort.findReleasableIds(now, properties.batchSize());
        int released = runEach(due, transferHoldService::releaseAsSystem);
        // 보류한 날이 지난 승인 대기를 닫는다. 한 건씩 닫아 나중에 종료 이벤트를 붙일 자리를 남긴다(#998)
        List<Long> overdue =
                transferHoldPort.findOverdueApprovalIds(now.toLocalDate().atStartOfDay(), properties.batchSize());
        int expired = countEach(overdue, id -> transferHoldPort.close(id, TransferHoldStatus.EXPIRED, null, null, now));
        int skipped = stuck.size() - resumed + due.size() - released + overdue.size() - expired;
        return new TransferHoldReleaseSummary(resumed, released, expired, skipped);
    }

    // false는 꺼낸 뒤 다른 곳이 먼저 닫은 정상 경합이라 로그 없이 건너뛴다
    private static int countEach(List<Long> ids, LongPredicate action) {
        int done = 0;
        for (long id : ids) {
            try {
                if (action.test(id)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("이체 보류 만료를 건너뜀 - transferHoldId={}", id, e);
            }
        }
        return done;
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
