package com.shinhan.corebank.limit.application.port.out;

import java.util.Optional;

import com.shinhan.corebank.limit.domain.TransferLimit;

/** 한도 상태를 바꾸는 오퍼레이션. */
public interface TransferLimitCommandPort {

    TransferLimit save(TransferLimit limit);

    /** 변경을 위해 X-Lock 을 잡고 읽는다. 조회 전용 경로는 TransferLimitQueryPort 를 쓴다. */
    Optional<TransferLimit> findByCustomerIdForUpdate(Long customerId);
}
