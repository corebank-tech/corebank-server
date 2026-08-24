package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountTransactionQueryUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountTransactionQueryService
        implements AccountTransactionQueryUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZE =
            Set.of(5, 10, 20, 30, 50);

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final AccountPersistencePort accountPersistencePort;
    private final LedgerHistoryQueryUseCase ledgerHistoryQueryUseCase;
    private final Clock clock;

    @Override
    public LedgerHistoryResult getTransactions(
            Long customerId,
            Long accountId,
            LocalDate fromDate,
            LocalDate toDate,
            LedgerHistoryDirection direction,
            String keyword,
            LedgerHistorySort sort,
            int page,
            int size,
            boolean all
    ) {
        validateRequired(customerId, accountId);

        validateOwnership(
                customerId,
                accountId
        );

        LocalDate resolvedToDate =
                toDate != null
                        ? toDate
                        : LocalDate.now(
                                clock.withZone(KOREA_ZONE)
                        );

        LocalDate resolvedFromDate =
                fromDate != null
                        ? fromDate
                        : resolvedToDate.minusMonths(1);

        LedgerHistoryDirection resolvedDirection =
                direction != null
                        ? direction
                        : LedgerHistoryDirection.ALL;

        LedgerHistorySort resolvedSort =
                sort != null
                        ? sort
                        : LedgerHistorySort.LATEST;

        validateDateRange(
                resolvedFromDate,
                resolvedToDate
        );

        validatePagination(
                page,
                size,
                all
        );

        LedgerHistoryQuery query =
                LedgerHistoryQuery.builder()
                        .accountId(accountId)
                        .fromDate(resolvedFromDate)
                        .toDate(resolvedToDate)
                        .direction(resolvedDirection)
                        .keyword(normalizeKeyword(keyword))
                        .sort(resolvedSort)
                        .page(all ? 0 : page - 1)
                        .size(all ? 0 : size)
                        .all(all)
                        .build();

        return ledgerHistoryQueryUseCase.query(query);
    }

    private void validateRequired(
            Long customerId,
            Long accountId
    ) {
        if (customerId == null
                || accountId == null) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }
    }

    private void validateOwnership(
            Long customerId,
            Long accountId
    ) {
        accountPersistencePort
                .findByAccountIdAndCustomerId(
                        accountId,
                        customerId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                AccountErrorCode
                                        .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                        )
                );
    }

    private void validateDateRange(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_DATE_RANGE
            );
        }

        if (toDate.isAfter(
                fromDate.plusYears(1)
        )) {
            throw new BusinessException(
                    CommonErrorCode.DATE_RANGE_EXCEEDED
            );
        }
    }

    private void validatePagination(
            int page,
            int size,
            boolean all
    ) {
        if (all) {
            return;
        }

        if (page < 1) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_PAGE_SIZE
            );
        }
    }

    private String normalizeKeyword(
            String keyword
    ) {
        if (keyword == null
                || keyword.isBlank()) {
            return null;
        }

        return keyword.strip();
    }
}