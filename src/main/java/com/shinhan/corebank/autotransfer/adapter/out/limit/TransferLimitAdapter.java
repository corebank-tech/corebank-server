package com.shinhan.corebank.autotransfer.adapter.out.limit;

import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("prod")
public class TransferLimitAdapter implements TransferLimitPort {
    private static final long DEFAULT_ONE_TIME_LIMIT = 1_000_000L;

    private final TransferLimitJpaRepository transferLimitJpaRepository;

    @Override
    public long findOneTimeLimit(Long customerId) {
        return transferLimitJpaRepository.findById(customerId)
                .map(TransferLimitJpaEntity::getOneTimeLimit)
                .orElse(DEFAULT_ONE_TIME_LIMIT);
    }
}
