package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccumulatedDailyBalanceSqlPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryResult;
import com.shinhan.corebank.subscription.domain.AccumulatedDailyBalanceCalculator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccumulatedDailyBalanceService implements AccumulatedDailyBalanceUseCase {

    private final LedgerBalanceHistoryPort ledgerBalanceHistoryPort;
    private final AccumulatedDailyBalanceSqlPort sqlPort;
    private final boolean sqlEnabled;

    public AccumulatedDailyBalanceService(
            LedgerBalanceHistoryPort ledgerBalanceHistoryPort,
            AccumulatedDailyBalanceSqlPort sqlPort,
            @Value("${app.ph13.sql-enabled:false}") boolean sqlEnabled) {

        this.ledgerBalanceHistoryPort = ledgerBalanceHistoryPort;
        this.sqlPort = sqlPort;
        this.sqlEnabled = sqlEnabled;
    }

    @Override
    public long calculate(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        if (accountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (sqlEnabled) {
            return calculateWithSql(accountId, fromInclusive, toExclusive);
        }

        return calculateLegacy(accountId, fromInclusive, toExclusive);
    }

    private long calculateWithSql(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        Map<Long, Long> result = sqlPort.calculate(List.of(accountId), fromInclusive, toExclusive);

        Long balance = result.get(accountId);

        if (balance == null) {
            throw new IllegalStateException("적수 SQL 집계 결과가 없습니다. accountId=" + accountId);
        }

        return balance;
    }

    private long calculateLegacy(Long accountId, LocalDate fromInclusive, LocalDate toExclusive) {

        LedgerBalanceHistoryResult history = ledgerBalanceHistoryPort.load(accountId, fromInclusive, toExclusive);

        return AccumulatedDailyBalanceCalculator.calculate(
                history.openingBalance(), fromInclusive, toExclusive, history.points());
    }
}
