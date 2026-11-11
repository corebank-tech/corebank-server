package com.shinhan.corebank.fds.api;

/** 관리자와 조사 모듈에 저장된 행동 점수만 제공한다. */
public interface BehaviorScoreQuery {
    BehaviorScoreView findByTransferHoldId(long transferHoldId);
}
