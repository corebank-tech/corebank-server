package com.shinhan.corebank.limit.application.port.out;

import java.util.Optional;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;

/**
 * 한도 상태를 바꾸는 오퍼레이션. 변경을 위해 도메인을 읽어오는 조회도 여기 둔다 -
 * 이체 실행 경로에서 쓸 잠금 조회가 그 예다.
 */
public interface TransferLimitCommandPort {

    Optional<TransferLimit> findByCustomerId(Long customerId);

    TransferLimit save(TransferLimit limit);

    TransferLimitHistory saveHistory(TransferLimitHistory history);
}
