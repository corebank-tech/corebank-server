package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.product.domain.ProductGroup;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class SubscriptionMaturityCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private SubscriptionMaturityCalculator() {}

    public static MaturityCalculation calculate(
            ProductGroup productGroup, long subscriptionAmount, int termsMonths, BigDecimal appliedRate) {
        return productGroup == ProductGroup.SAVINGS
                ? calculateInstallment(subscriptionAmount, termsMonths, appliedRate)
                : calculateLumpSum(subscriptionAmount, termsMonths, appliedRate);
    }

    private static MaturityCalculation calculateLumpSum(long principal, int termMonths, BigDecimal appliedRate) {
        long interest = BigDecimal.valueOf(principal)
                .multiply(appliedRate)
                .divide(HUNDRED)
                .multiply(BigDecimal.valueOf(termMonths))
                .divide(TWELVE, 0, RoundingMode.DOWN)
                .longValueExact();
        return new MaturityCalculation(principal, interest, principal + interest);
    }

    private static MaturityCalculation calculateInstallment(
            long monthlyAmount, int termMonths, BigDecimal appliedRate) {
        long principal = monthlyAmount * termMonths;
        long weightedMonths = (long) termMonths * (termMonths + 1) / 2;
        long interest = BigDecimal.valueOf(monthlyAmount)
                .multiply(appliedRate)
                .divide(HUNDRED)
                .multiply(BigDecimal.valueOf(weightedMonths))
                .divide(TWELVE, 0, RoundingMode.DOWN)
                .longValueExact();
        return new MaturityCalculation(principal, interest, principal + interest);
    }

    public record MaturityCalculation(long expectedPrincipal, long expectedInterest, long expectedMaturityAmount) {}
}
