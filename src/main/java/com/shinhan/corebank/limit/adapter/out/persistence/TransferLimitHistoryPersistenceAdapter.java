package com.shinhan.corebank.limit.adapter.out.persistence;

import com.shinhan.corebank.limit.application.port.out.TransferLimitHistoryPort;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** transfer_limit_history 전용 어댑터. 이력은 append-only 라 저장만 있다. */
@Component
@RequiredArgsConstructor
public class TransferLimitHistoryPersistenceAdapter implements TransferLimitHistoryPort {

    private final TransferLimitHistoryJpaRepository historyRepository;

    @Override
    public void save(TransferLimitHistory history) {
        historyRepository.save(TransferLimitHistoryJpaEntity.from(history));
    }
}
