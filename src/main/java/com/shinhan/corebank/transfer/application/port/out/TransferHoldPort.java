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

    /** 시각으로 푸는 HELD를 해제 시각이 지났을 때만 EXECUTING으로 선점한다. executingAt이 이 선점의 표식이다. */
    boolean markDueExecuting(long transferHoldId, LocalDateTime executingAt);

    /** 승인으로 푸는 HELD만 EXECUTING으로 선점한다. */
    boolean markApprovedExecuting(long transferHoldId, long actorId, LocalDateTime executingAt);

    /** staleBefore 전에 선점된 채 멈춘 EXECUTING을 다시 선점한다. 앞선 선점은 결과를 기록하지 못하게 된다. */
    boolean reclaimStuck(long transferHoldId, LocalDateTime staleBefore, LocalDateTime executingAt);

    /**
     * EXECUTING → EXECUTED 또는 FAILED. claimedAt으로 선점한 쪽만 기록한다(펜싱).
     *
     * 이체 행 없이 거부된 FAILED는 errorCode를 reason_code에 남겨 재생에 쓴다.
     */
    boolean finishExecution(
            long transferHoldId,
            LocalDateTime claimedAt,
            TransferHoldStatus result,
            Long transferId,
            String errorCode,
            LocalDateTime completedAt);

    /** HELD → REJECTED · CANCELLED · EXPIRED. 실행 없이 끝낸다. */
    boolean close(
            long transferHoldId, TransferHoldStatus result, Long actorId, String reasonCode, LocalDateTime completedAt);
}
