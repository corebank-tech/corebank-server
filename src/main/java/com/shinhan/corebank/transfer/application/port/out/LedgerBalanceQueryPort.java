package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LedgerBalanceQueryPort {

    Optional<LedgerBalancePoint> findLatestBefore(Long accountId, LocalDateTime beforeExclusive);

    List<LedgerBalancePoint> findBetween(Long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    record LedgerBalancePoint(LocalDateTime occurredAt, long ledgerEntryId, long balanceAfter) {}
}
