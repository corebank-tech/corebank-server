package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferQueryPort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentTransferQueryService implements RecentTransferQuery {

    private final RecentTransferQueryPort recentTransferQueryPort;

    @Override
    public RecentTransferAggregate aggregateSucceeded(
            long customerId, String depositAccountNumber, LocalDateTime since) {
        return recentTransferQueryPort.aggregateSucceeded(customerId, depositAccountNumber, since);
    }

    @Override
    public boolean hasSucceededBefore(long customerId, String depositAccountNumber, LocalDateTime before) {
        return recentTransferQueryPort.existsSucceededBefore(customerId, depositAccountNumber, before);
    }
}
