package com.shinhan.corebank.product.application.service;

import com.shinhan.corebank.product.application.port.in.TermsViewHistoryCleanupUseCase;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 한 번에 다 지우면 긴 트랜잭션이 되어 열람 upsert를 오래 막으므로 1,000건씩 나눠 지운다.
@Service
@RequiredArgsConstructor
public class TermsViewHistoryCleanupService implements TermsViewHistoryCleanupUseCase {
    private static final int BATCH_SIZE = 1000;

    private final TermsViewHistoryPort termsViewHistoryPort;

    @Override
    public int deleteExpired() {
        int totalDeleted = 0;
        int deletedThisRound;
        do {
            deletedThisRound = termsViewHistoryPort.deleteExpired(BATCH_SIZE);
            totalDeleted += deletedThisRound;
        } while (deletedThisRound == BATCH_SIZE);
        return totalDeleted;
    }
}
