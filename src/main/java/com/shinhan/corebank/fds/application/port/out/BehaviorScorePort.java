package com.shinhan.corebank.fds.application.port.out;

import com.shinhan.corebank.fds.api.BehaviorScoreView;
import java.time.LocalDateTime;
import java.util.List;

/** 점수의 PENDING·AVAILABLE·UNAVAILABLE 저장과 복구 조회 계약이다. */
public interface BehaviorScorePort {
    void createIfAbsent(long transferHoldId, String flowId, LocalDateTime now);

    boolean markStarted(long transferHoldId, LocalDateTime now);

    boolean available(long transferHoldId, BehaviorScoreInferencePort.Score score, LocalDateTime now);

    boolean unavailable(long transferHoldId, String reason, LocalDateTime now);

    BehaviorScoreView find(long transferHoldId);

    List<Long> startedBefore(LocalDateTime cutoff, int limit);
}
