package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.domain.InterestCalculator;
import com.shinhan.corebank.subscription.domain.InterestResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InterestCalculationService implements InterestCalculationUseCase {

    private final AccumulatedDailyBalanceUseCase balanceUseCase;
    private final InterestCalculator.TaxRates taxRates;

    public InterestCalculationService(
            AccumulatedDailyBalanceUseCase balanceUseCase,
            @Value("${app.interest.income-tax-rate}") BigDecimal incomeTaxRate,
            @Value("${app.interest.local-tax-ratio}") BigDecimal localTaxRatio) {

        this.balanceUseCase = balanceUseCase;
        this.taxRates = new InterestCalculator.TaxRates(incomeTaxRate, localTaxRatio);
    }

    @Override
    public InterestResult calculate(InterestCalculationCommand command) {
        validate(command);

        if (command.openedDate().equals(command.toExclusive())) {
            return InterestCalculator.calculateSimple(0L, command.appliedRate(), taxRates);
        }

        return switch (command.mode()) {
            case SIMPLE -> calculateSimple(command);
            case MONTHLY_COMPOUND -> calculateMonthlyCompound(command);
        };
    }

    private InterestResult calculateSimple(InterestCalculationCommand command) {

        long accumulatedDailyBalance =
                balanceUseCase.calculate(command.accountId(), command.openedDate(), command.toExclusive());

        return InterestCalculator.calculateSimple(accumulatedDailyBalance, command.appliedRate(), taxRates);
    }

    private InterestResult calculateMonthlyCompound(InterestCalculationCommand command) {

        List<InterestCalculator.MonthlyPeriod> periods = new ArrayList<>();

        LocalDate start = command.openedDate();
        long monthIndex = 1L;

        while (start.isBefore(command.toExclusive())) {

            // 직전 경계가 아니라 최초 가입일을 기준으로 계산한다.
            LocalDate monthlyBoundary = command.openedDate().plusMonths(monthIndex++);

            LocalDate end = monthlyBoundary.isAfter(command.toExclusive()) ? command.toExclusive() : monthlyBoundary;

            long days = ChronoUnit.DAYS.between(start, end);

            long accumulatedDailyBalance = balanceUseCase.calculate(command.accountId(), start, end);

            periods.add(new InterestCalculator.MonthlyPeriod(accumulatedDailyBalance, days));

            start = end;
        }

        return InterestCalculator.calculateMonthlyCompound(periods, command.appliedRate(), taxRates);
    }

    private static void validate(InterestCalculationCommand command) {

        if (command == null
                || command.accountId() == null
                || command.openedDate() == null
                || command.toExclusive() == null
                || command.appliedRate() == null
                || command.mode() == null) {

            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (command.accountId() <= 0 || command.appliedRate().signum() < 0) {

            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (command.openedDate().isAfter(command.toExclusive())) {

            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
    }
}
