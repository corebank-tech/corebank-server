package com.shinhan.corebank.limit.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferLimitPersistenceAdapter implements TransferLimitQueryPort, TransferLimitCommandPort {

    private final TransferLimitJpaRepository limitRepository;
    private final TransferLimitDailyUsageJpaRepository usageRepository;
    private final TransferLimitHistoryJpaRepository historyRepository;

    @Override
    public Optional<TransferLimit> findByCustomerId(Long customerId) {
        return limitRepository.findById(customerId).map(TransferLimitJpaEntity::toDomain);
    }

    @Override
    public Optional<TransferLimitDailyUsage> findUsage(Long customerId, LocalDate usageDate) {
        return usageRepository.findById(new TransferLimitDailyUsageId(customerId, usageDate))
                .map(TransferLimitDailyUsageJpaEntity::toDomain);
    }

    @Override
    public TransferLimit save(TransferLimit limit) {
        TransferLimitJpaEntity entity = limitRepository.findById(limit.getCustomerId())
                .orElseGet(() -> TransferLimitJpaEntity.from(limit));
        entity.apply(limit.getOneTimeLimit(), limit.getDailyLimit());
        return limitRepository.save(entity).toDomain();
    }

    @Override
    public TransferLimitHistory saveHistory(TransferLimitHistory history) {
        return historyRepository.save(TransferLimitHistoryJpaEntity.from(history)).toDomain();
    }
}
