package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class InterestCalculator {

    private static final BigDecimal INTEREST_DIVISOR = BigDecimal.valueOf(36_500L);

    private static final BigDecimal TEN = BigDecimal.TEN;

    private InterestCalculator() {}

    // 단리: 전체 기간 적수를 기준으로 마지막에 한 번 절사
    public static InterestResult calculateSimple(
            long accumulatedDailyBalance, BigDecimal appliedRate, TaxRates taxRates) {

        validateInput(appliedRate, taxRates);

        if (accumulatedDailyBalance < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        try {
            long gross = calculateGross(BigDecimal.valueOf(accumulatedDailyBalance), appliedRate);

            return calculateResult(gross, appliedRate, taxRates);
        } catch (ArithmeticException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, e);
        }
    }

    // 월복리: 각 월 이자를 절사해 다음 월 계산에 누적
    public static InterestResult calculateMonthlyCompound(
            List<MonthlyPeriod> periods, BigDecimal appliedRate, TaxRates taxRates) {

        validateInput(appliedRate, taxRates);

        if (periods == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        long accumulatedInterest = 0L;

        try {
            for (MonthlyPeriod period : periods) {
                if (period == null) {
                    throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
                }

                // PH-11의 실제 원장 적수
                BigDecimal ledgerSum = BigDecimal.valueOf(period.accumulatedDailyBalance());

                // 이전 달까지의 미기표 복리 이자가 만드는 적수
                BigDecimal interestSum =
                        BigDecimal.valueOf(accumulatedInterest).multiply(BigDecimal.valueOf(period.days()));

                BigDecimal compoundSum = ledgerSum.add(interestSum);

                long monthlyInterest = calculateGross(compoundSum, appliedRate);

                accumulatedInterest = Math.addExact(accumulatedInterest, monthlyInterest);
            }

            return calculateResult(accumulatedInterest, appliedRate, taxRates);

        } catch (ArithmeticException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, e);
        }
    }

    private static long calculateGross(BigDecimal accumulatedDailyBalance, BigDecimal appliedRate) {

        return accumulatedDailyBalance
                .multiply(appliedRate)
                .divide(INTEREST_DIVISOR, 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private static InterestResult calculateResult(long gross, BigDecimal appliedRate, TaxRates taxRates) {

        long incomeTax = truncateToTen(BigDecimal.valueOf(gross).multiply(taxRates.incomeTaxRate()));

        long localTax = truncateToTen(BigDecimal.valueOf(incomeTax).multiply(taxRates.localTaxRatio()));

        long tax = Math.addExact(incomeTax, localTax);
        long net = Math.subtractExact(gross, tax);

        return new InterestResult(gross, tax, net, appliedRate);
    }

    private static long truncateToTen(BigDecimal amount) {
        return amount.divide(TEN, 0, RoundingMode.DOWN).multiply(TEN).longValueExact();
    }

    private static void validateInput(BigDecimal appliedRate, TaxRates taxRates) {

        if (appliedRate == null || taxRates == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (appliedRate.signum() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 한 월 구간의 PH-11 적수와 실제 일수
    public record MonthlyPeriod(long accumulatedDailyBalance, long days) {

        public MonthlyPeriod {
            if (accumulatedDailyBalance < 0 || days <= 0) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
    }

    // 0.14 = 소득세 14%, 0.10 = 소득세 대비 지방소득세 10%
    public record TaxRates(BigDecimal incomeTaxRate, BigDecimal localTaxRatio) {

        public TaxRates {
            if (incomeTaxRate == null || localTaxRatio == null) {
                throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
            }

            if (incomeTaxRate.signum() < 0
                    || localTaxRatio.signum() < 0
                    || incomeTaxRate.compareTo(BigDecimal.ONE) > 0
                    || localTaxRatio.compareTo(BigDecimal.ONE) > 0) {

                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
    }
}
