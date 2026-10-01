package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistory;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryPort;
import com.shinhan.corebank.subscription.domain.AccumulatedDailyBalanceCalculator;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccumulatedDailyBalanceService implements AccumulatedDailyBalanceUseCase {

    private final LedgerBalanceHistoryPort ledgerBalanceHistoryPort;

    @Override
    public long calculate(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        if (accountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        LedgerBalanceHistory history = ledgerBalanceHistoryPort.load(accountId, fromInclusive, toExclusive);

        return AccumulatedDailyBalanceCalculator.calculate(
                history.openingBalance(), fromInclusive, toExclusive, history.points());
    }
}
