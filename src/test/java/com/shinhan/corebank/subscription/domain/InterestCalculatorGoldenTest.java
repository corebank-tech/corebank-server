package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PH-12 상품 12종 스프레드시트 골든데이터 대조")
class InterestCalculatorGoldenTest {

    private static final InterestCalculator.TaxRates TAX_RATES =
            new InterestCalculator.TaxRates(new BigDecimal("0.14"), new BigDecimal("0.10"));

    private static final LocalDate OPENED_DATE = LocalDate.of(2026, 1, 1);

    @ParameterizedTest(name = "{index}. {0}")
    @CsvSource({
        // 상품코드, 상품군, 계산방식, 12개월 금리,
        // 원금/월납입액, 기대 세전이자, 기대 세금, 기대 세후이자
        "PRD_YOUTH_SAVE, SAVINGS, SIMPLE, 3.35, 200000, 37024, 5690, 31334",
        "PRD_BASIC_DEP, DEPOSIT, SIMPLE, 2.95, 1000000, 29500, 4540, 24960",
        "PRD_CORE_DEP, DEPOSIT, SIMPLE, 3.45, 2000000, 69000, 10620, 58380",
        "PRD_FREE_SAVE, SAVINGS, SIMPLE, 3.70, 150000, 30669, 4710, 25959",
        "PRD_LARGE_DEP, DEPOSIT, SIMPLE, 3.50, 10000000, 350000, 53900, 296100",
        "PRD_REGULAR_SAVE, SAVINGS, SIMPLE, 3.95, 200000, 43655, 6720, 36935",
        "PRD_SHORT_DEP, DEPOSIT, SIMPLE, 3.15, 1000000, 31500, 4850, 26650",
        "PRD_GOAL_SAVE, SAVINGS, SIMPLE, 3.95, 100000, 21827, 3350, 18477",
        "PRD_HOUSING_SAVE, SAVINGS, COMPOUND, 3.40, 150000, 28445, 4370, 24075",
        "PRD_SENIOR_DEP, DEPOSIT, SIMPLE, 3.00, 5000000, 150000, 23100, 126900",
        "PRD_SALARY_SAVE, SAVINGS, SIMPLE, 3.80, 100000, 20998, 3220, 17778",
        "PRD_PRIME_DEP, DEPOSIT, COMPOUND, 3.20, 10000000, 324729, 50000, 274729"
    })
    void matchesSpreadsheet(
            String productCode,
            String productGroup,
            String interestType,
            String rate,
            long amount,
            long expectedGross,
            long expectedTax,
            long expectedNet) {

        // 시드 상품별 12개월 가입을 동일 조건으로 검증한다.
        // 적금은 가입월(1월)에 초입금이 없고 2월부터 납입한다.
        List<InterestCalculator.MonthlyPeriod> periods = new ArrayList<>();

        long totalAccumulatedDailyBalance = 0L;

        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            LocalDate from = OPENED_DATE.plusMonths(monthIndex);
            LocalDate to = OPENED_DATE.plusMonths(monthIndex + 1);

            long days = ChronoUnit.DAYS.between(from, to);

            long ledgerBalance = productGroup.equals("SAVINGS") ? Math.multiplyExact(amount, monthIndex) : amount;

            long monthlyAccumulatedDailyBalance = Math.multiplyExact(ledgerBalance, days);

            periods.add(new InterestCalculator.MonthlyPeriod(monthlyAccumulatedDailyBalance, days));

            totalAccumulatedDailyBalance = Math.addExact(totalAccumulatedDailyBalance, monthlyAccumulatedDailyBalance);
        }

        BigDecimal appliedRate = new BigDecimal(rate);

        InterestResult result = interestType.equals("COMPOUND")
                ? InterestCalculator.calculateMonthlyCompound(periods, appliedRate, TAX_RATES)
                : InterestCalculator.calculateSimple(totalAccumulatedDailyBalance, appliedRate, TAX_RATES);

        assertThat(result.gross()).as("%s 세전이자", productCode).isEqualTo(expectedGross);

        assertThat(result.tax()).as("%s 원천징수세액", productCode).isEqualTo(expectedTax);

        assertThat(result.net()).as("%s 세후이자", productCode).isEqualTo(expectedNet);

        assertThat(result.appliedRate()).isEqualByComparingTo(appliedRate);
    }
}
