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

        // TODO(#516, #515): trade_date 대입과 기존 행 보정 완료 후
        // 시작 잔액·기간 조회를 함께 trade_date 기준으로 전환한다.
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
        // TODO(#516, #515): 위 조회 기준 전환과 함께 날짜도 실제 trade_date로 교체한다.
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
