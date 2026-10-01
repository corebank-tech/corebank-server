package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.LedgerBalanceEntry;
import com.shinhan.corebank.transfer.api.LedgerBalanceHistory;
import com.shinhan.corebank.transfer.api.LedgerBalanceQuery;
import com.shinhan.corebank.transfer.application.port.out.LedgerBalanceQueryPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerBalanceQueryPort.LedgerBalancePoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerBalanceQueryService implements LedgerBalanceQuery {

    private final LedgerBalanceQueryPort ledgerBalanceQueryPort;

    @Override
    public LedgerBalanceHistory query(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        validate(accountId, fromInclusive, toExclusive);

        LocalDateTime from = fromInclusive.atStartOfDay();
        LocalDateTime to = toExclusive.atStartOfDay();

        long openingBalance = ledgerBalanceQueryPort
                .findLatestBefore(accountId, from)
                .map(LedgerBalancePoint::balanceAfter)
                .orElse(0L);

        List<LedgerBalanceEntry> entries = ledgerBalanceQueryPort.findBetween(accountId, from, to).stream()
                .map(this::toApiEntry)
                .toList();

        return new LedgerBalanceHistory(openingBalance, entries);
    }

    private LedgerBalanceEntry toApiEntry(LedgerBalancePoint point) {
        return new LedgerBalanceEntry(
                point.occurredAt().toLocalDate(), point.occurredAt(), point.ledgerEntryId(), point.balanceAfter());
    }

    private void validate(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        if (accountId == null || fromInclusive == null || toExclusive == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (fromInclusive.isAfter(toExclusive)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
    }
}
