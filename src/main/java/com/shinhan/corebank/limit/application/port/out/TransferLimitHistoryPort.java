package com.shinhan.corebank.limit.application.port.out;

import com.shinhan.corebank.limit.domain.TransferLimitHistory;

/**
 * 이체한도 변경 이력 저장 전용 포트. 한도 자체를 다루는 TransferLimitCommandPort 와 분리했다 -
 * 한 어댑터가 transfer_limit·transfer_limit_daily_usage·transfer_limit_history 를 모두
 * 들고 있으면 책임이 과하다는 PR #206 리뷰를 따른 것이다.
 */
public interface TransferLimitHistoryPort {

    /** 변경 직전 값을 이력으로 남긴다. 변경과 같은 트랜잭션에서 호출한다(REQ-TRSF-025). */
    void save(TransferLimitHistory history);
}
