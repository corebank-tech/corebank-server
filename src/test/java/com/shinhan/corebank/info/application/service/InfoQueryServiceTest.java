package com.shinhan.corebank.info.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정보계 조회 서비스")
class InfoQueryServiceTest {

    private final FakeInfoMart mart = new FakeInfoMart();
    private final InfoQueryService service = new InfoQueryService(mart);

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CMN0003 이고 정보계를 호출하지 않는다")
    void fromAfterToIsRejectedBeforeCall() {
        DepositBalancesQuery query = new DepositBalancesQuery(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> service.depositBalances(query))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));
        assertThat(mart.calls).isEmpty();
    }

    @Test
    @DisplayName("기간이 365일을 넘으면 CMN0004 이고 정보계를 호출하지 않는다")
    void rangeOver365DaysIsRejectedBeforeCall() {
        DepositBalancesQuery query = new DepositBalancesQuery(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 2));

        assertThatThrownBy(() -> service.depositBalances(query))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));
        assertThat(mart.calls).isEmpty();
    }

    @Test
    @DisplayName("정확히 365일 차이는 통과한다")
    void exactly365DaysPasses() {
        service.depositBalances(new DepositBalancesQuery(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 1)));

        assertThat(mart.calls).containsExactly("deposit 2025-10-01 2026-10-01");
    }

    @Test
    @DisplayName("한쪽만 주면 검증하지 않고 그대로 넘긴다")
    void singleBoundIsPassedThrough() {
        service.depositBalances(new DepositBalancesQuery(null, LocalDate.of(2026, 10, 1)));
        service.depositBalances(new DepositBalancesQuery(LocalDate.of(2026, 10, 1), null));

        assertThat(mart.calls).containsExactly("deposit null 2026-10-01", "deposit 2026-10-01 null");
    }

    @Test
    @DisplayName("상품 가입 요약은 월을 그대로 넘긴다")
    void productSubscriptionsPassesMonth() {
        service.productSubscriptions(new ProductSubscriptionsQuery(YearMonth.of(2026, 10)));
        service.productSubscriptions(new ProductSubscriptionsQuery(null));

        assertThat(mart.calls).containsExactly("product 2026-10", "product null");
    }

    private static final class FakeInfoMart implements InfoMartPort {

        final List<String> calls = new ArrayList<>();

        @Override
        public DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
            calls.add("deposit " + from + " " + to);
            return DepositBalanceTrend.noLoadHistory();
        }

        @Override
        public ProductSubscriptionSummary productSubscriptions(YearMonth month) {
            calls.add("product " + month);
            return ProductSubscriptionSummary.noLoadHistory(month);
        }
    }
}
