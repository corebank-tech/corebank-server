package com.shinhan.corebank.info.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스텁은 빈 응답만 돌려주므로, 실제 구현(#704)이 채울 항목 변환을 여기서 미리 검증한다.
@DisplayName("정보계 조회 응답 변환")
class InfoResponseMappingTest {

    @Test
    @DisplayName("일별 수신 잔액 추이를 날짜와 총 잔액 그대로 옮긴다")
    void mapsDepositBalanceTrend() {
        DepositBalanceTrend trend = new DepositBalanceTrend(
                LocalDate.of(2026, 10, 28),
                List.of(
                        new DepositBalanceTrend.Point(LocalDate.of(2026, 10, 1), 1_523_400_000_000L),
                        new DepositBalanceTrend.Point(LocalDate.of(2026, 10, 2), 1_524_100_000_000L)));

        DepositBalancesResponse response = DepositBalancesResponse.from(trend);

        assertThat(response.asOf()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).baseDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.items().get(0).totalBalance()).isEqualTo(1_523_400_000_000L);
        assertThat(response.items().get(1).totalBalance()).isEqualTo(1_524_100_000_000L);
    }

    @Test
    @DisplayName("적재 이력이 없으면 asOf 가 null 이고 항목이 비어 있다")
    void mapsEmptyDepositBalanceTrend() {
        DepositBalancesResponse response = DepositBalancesResponse.from(new DepositBalanceTrend(null, List.of()));

        assertThat(response.asOf()).isNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("적재 이력 없음 생성 메서드는 asOf·건수를 null 로, 항목을 비워 두고 달만 유지한다")
    void noLoadHistoryFactoriesLeaveCountsNull() {
        DepositBalanceTrend trend = DepositBalanceTrend.noLoadHistory();
        ProductSubscriptionSummary summary = ProductSubscriptionSummary.noLoadHistory(YearMonth.of(2026, 10));

        assertThat(trend.asOf()).isNull();
        assertThat(trend.items()).isEmpty();
        assertThat(summary.asOf()).isNull();
        assertThat(summary.month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(summary.subscribedCount()).isNull();
        assertThat(summary.closedCount()).isNull();
        assertThat(summary.items()).isEmpty();
    }

    @Test
    @DisplayName("상품별 가입·해지를 달은 yyyy-MM 문자열로, 항목은 상품 필드 그대로 옮긴다")
    void mapsProductSubscriptionSummary() {
        ProductSubscriptionSummary summary = new ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 28),
                YearMonth.of(2026, 10),
                120L,
                5L,
                List.of(new ProductSubscriptionSummary.Line(3L, "DEP-12M", "쏠편한 정기예금", "DEPOSIT", 80L, 3L)));

        ProductSubscriptionsResponse response = ProductSubscriptionsResponse.from(summary);

        assertThat(response.asOf()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.subscribedCount()).isEqualTo(120L);
        assertThat(response.closedCount()).isEqualTo(5L);
        assertThat(response.items()).hasSize(1);
        ProductSubscriptionsResponse.ProductSubscriptionItem item =
                response.items().get(0);
        assertThat(item.productId()).isEqualTo(3L);
        assertThat(item.productCode()).isEqualTo("DEP-12M");
        assertThat(item.productName()).isEqualTo("쏠편한 정기예금");
        assertThat(item.productGroup()).isEqualTo("DEPOSIT");
        assertThat(item.subscribedCount()).isEqualTo(80L);
        assertThat(item.closedCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("적재 이력도 요청한 달도 없으면 month·건수가 null 이다")
    void mapsEmptyProductSubscriptionSummary() {
        ProductSubscriptionsResponse response =
                ProductSubscriptionsResponse.from(new ProductSubscriptionSummary(null, null, null, null, List.of()));

        assertThat(response.asOf()).isNull();
        assertThat(response.month()).isNull();
        assertThat(response.subscribedCount()).isNull();
        assertThat(response.closedCount()).isNull();
        assertThat(response.items()).isEmpty();
    }
}
