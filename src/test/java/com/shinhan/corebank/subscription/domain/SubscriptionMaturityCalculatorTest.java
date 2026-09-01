package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.product.domain.ProductGroup;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionMaturityCalculatorTest {

    @Test
    @DisplayName("정기예금: 원금 600만원·12개월·3.70%면 이자 22.2만원, 만기수령액 622.2만원")
    void deposit_cleanDivision() {
        SubscriptionMaturityCalculator.MaturityCalculation result =
                SubscriptionMaturityCalculator.calculate(ProductGroup.DEPOSIT, 6_000_000L, 12, new BigDecimal("3.70"));

        assertThat(result.expectedPrincipal()).isEqualTo(6_000_000L);
        assertThat(result.expectedInterest()).isEqualTo(222_000L);
        assertThat(result.expectedMaturityAmount()).isEqualTo(6_222_000L);
    }

    @Test
    @DisplayName("정기예금: 나누어떨어지지 않는 이자는 원 단위 절사(내림)한다")
    void deposit_truncatesRemainder() {
        SubscriptionMaturityCalculator.MaturityCalculation result =
                SubscriptionMaturityCalculator.calculate(ProductGroup.DEPOSIT, 1_000_000L, 5, new BigDecimal("2.00"));

        // 1,000,000 × 0.02 × 5 / 12 = 8,333.333... → 8,333
        assertThat(result.expectedInterest()).isEqualTo(8_333L);
    }

    @Test
    @DisplayName("정기적금: 월 50만원·12개월·3.70%면 가중개월수 78 기준으로 이자 120,250원")
    void savings_installmentWeighting() {
        SubscriptionMaturityCalculator.MaturityCalculation result =
                SubscriptionMaturityCalculator.calculate(ProductGroup.SAVINGS, 500_000L, 12, new BigDecimal("3.70"));

        assertThat(result.expectedPrincipal()).isEqualTo(6_000_000L);
        assertThat(result.expectedInterest()).isEqualTo(120_250L);
        assertThat(result.expectedMaturityAmount()).isEqualTo(6_120_250L);
    }
}
