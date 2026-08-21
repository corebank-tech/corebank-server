package com.shinhan.corebank.limit.application.port.out;

import java.util.Optional;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;

/** 한도 상태를 바꾸는 오퍼레이션. */
public interface TransferLimitCommandPort {

    TransferLimit save(TransferLimit limit);

    /** 변경을 위해 X-Lock 을 잡고 읽는다. 조회 전용 경로는 TransferLimitQueryPort 를 쓴다. */
    Optional<TransferLimit> findByCustomerIdForUpdate(Long customerId);

    /** 변경 직전 값을 이력으로 남긴다. 변경과 같은 트랜잭션에서 호출한다(REQ-TRSF-025). */
    void saveHistory(TransferLimitHistory history);
}
