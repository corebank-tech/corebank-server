package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 이체 보류 저장소. 상태 전이는 모두 "지금 상태가 이전 상태일 때만" 바꾸는 조건부 UPDATE다.
 * false면 다른 곳이 먼저 전이했거나 행이 없다는 뜻이다.
 */
public interface TransferHoldPort {

    TransferHold save(TransferHold transferHold);

    Optional<TransferHold> findById(long transferHoldId);

    /** HELD → EXECUTING. 실행을 선점한다. actorId는 스케줄러 실행이면 null이다. */
    boolean markExecuting(long transferHoldId, Long actorId, LocalDateTime executingAt);

    /**
     * EXECUTING → EXECUTED 또는 FAILED. 실행으로 생긴 이체 행을 남긴다.
     *
     * 이체 행 없이 거부됐으면 transferId는 null이라, FAILED면 errorCode를 reason_code에 남겨 다시 물을 때 돌려준다.
     */
    boolean finishExecution(
            long transferHoldId,
            TransferHoldStatus result,
            Long transferId,
            String errorCode,
            LocalDateTime completedAt);

    /** HELD → REJECTED · CANCELLED · EXPIRED. 실행 없이 끝낸다. */
    boolean close(
            long transferHoldId, TransferHoldStatus result, Long actorId, String reasonCode, LocalDateTime completedAt);
}
