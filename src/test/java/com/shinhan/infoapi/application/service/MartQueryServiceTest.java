package com.shinhan.infoapi.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoapi.application.port.out.MartReadPort;
import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.DepositBalanceTrend;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import com.shinhan.infoapi.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MartQueryServiceTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 26);

    @Test
    void 적재_이력이_없으면_잔액_추이는_asOf_null과_빈_목록이다() {
        FakeMart mart = new FakeMart(null);

        DepositBalanceTrend trend = new MartQueryService(mart).depositBalances(null, null);

        assertThat(trend.asOf()).isNull();
        assertThat(trend.items()).isEmpty();
        assertThat(mart.balanceCalls).isEmpty();
    }

    @Test
    void 기간을_생략하면_asOf부터_30일_거슬러_조회한다() {
        FakeMart mart = new FakeMart(AS_OF);

        DepositBalanceTrend trend = new MartQueryService(mart).depositBalances(null, null);

        assertThat(trend.asOf()).isEqualTo(AS_OF);
        assertThat(mart.balanceCalls).containsExactly(List.of(AS_OF.minusDays(30), AS_OF));
    }

    @Test
    void from만_주면_asOf까지_조회한다() {
        FakeMart mart = new FakeMart(AS_OF);
        LocalDate from = LocalDate.of(2026, 10, 1);

        new MartQueryService(mart).depositBalances(from, null);

        assertThat(mart.balanceCalls).containsExactly(List.of(from, AS_OF));
    }

    @Test
    void to만_주면_to에서_30일_거슬러_조회한다() {
        FakeMart mart = new FakeMart(AS_OF);
        LocalDate to = LocalDate.of(2026, 10, 20);

        new MartQueryService(mart).depositBalances(null, to);

        assertThat(mart.balanceCalls).containsExactly(List.of(to.minusDays(30), to));
    }

    @Test
    void to가_asOf_뒤면_asOf까지만_조회한다() {
        FakeMart mart = new FakeMart(AS_OF);

        new MartQueryService(mart).depositBalances(LocalDate.of(2026, 10, 1), AS_OF.plusDays(5));

        assertThat(mart.balanceCalls).containsExactly(List.of(LocalDate.of(2026, 10, 1), AS_OF));
    }

    @Test
    void from이_asOf_뒤면_마트를_읽지_않고_빈_목록이다() {
        FakeMart mart = new FakeMart(AS_OF);

        DepositBalanceTrend trend = new MartQueryService(mart).depositBalances(AS_OF.plusDays(1), null);

        assertThat(trend.asOf()).isEqualTo(AS_OF);
        assertThat(trend.items()).isEmpty();
        assertThat(mart.balanceCalls).isEmpty();
    }

    @Test
    void 적재_이력이_없으면_가입해지는_건수_null과_빈_목록이고_month는_그대로다() {
        YearMonth month = YearMonth.of(2026, 10);

        ProductSubscriptionSummary summary = new MartQueryService(new FakeMart(null)).productSubscriptions(month);

        assertThat(summary.asOf()).isNull();
        assertThat(summary.month()).isEqualTo(month);
        assertThat(summary.subscribedCount()).isNull();
        assertThat(summary.closedCount()).isNull();
        assertThat(summary.items()).isEmpty();
    }

    @Test
    void month를_생략하면_asOf가_속한_달의_1일부터_asOf까지다() {
        FakeMart mart = new FakeMart(AS_OF);

        ProductSubscriptionSummary summary = new MartQueryService(mart).productSubscriptions(null);

        assertThat(summary.month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(mart.subscriptionCalls).containsExactly(List.of(LocalDate.of(2026, 10, 1), AS_OF));
    }

    @Test
    void 지난_달은_그_달_말일까지_전부_센다() {
        FakeMart mart = new FakeMart(AS_OF);

        new MartQueryService(mart).productSubscriptions(YearMonth.of(2026, 9));

        assertThat(mart.subscriptionCalls)
                .containsExactly(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void asOf_뒤의_달은_마트를_읽지_않고_0건이다() {
        FakeMart mart = new FakeMart(AS_OF);

        ProductSubscriptionSummary summary = new MartQueryService(mart).productSubscriptions(YearMonth.of(2026, 11));

        assertThat(summary.asOf()).isEqualTo(AS_OF);
        assertThat(summary.subscribedCount()).isZero();
        assertThat(summary.closedCount()).isZero();
        assertThat(summary.items()).isEmpty();
        assertThat(mart.subscriptionCalls).isEmpty();
    }

    @Test
    void 상품별_건수를_합산해_전체_건수로_돌려준다() {
        FakeMart mart = new FakeMart(AS_OF);
        mart.lines = List.of(
                new ProductSubscriptionLine(3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT", 80, 3),
                new ProductSubscriptionLine(4, "SAV-12M", "쏠편한 정기적금", "SAVINGS", 40, 2));

        ProductSubscriptionSummary summary = new MartQueryService(mart).productSubscriptions(null);

        assertThat(summary.subscribedCount()).isEqualTo(120L);
        assertThat(summary.closedCount()).isEqualTo(5L);
        assertThat(summary.items()).hasSize(2);
    }

    private static final class FakeMart implements MartReadPort {

        private final LocalDate asOf;
        private final List<List<LocalDate>> balanceCalls = new ArrayList<>();
        private final List<List<LocalDate>> subscriptionCalls = new ArrayList<>();
        private List<ProductSubscriptionLine> lines = List.of();

        private FakeMart(LocalDate asOf) {
            this.asOf = asOf;
        }

        @Override
        public Optional<LocalDate> latestAsOf() {
            return Optional.ofNullable(asOf);
        }

        @Override
        public List<DepositBalancePoint> depositBalances(LocalDate from, LocalDate to) {
            balanceCalls.add(List.of(from, to));
            return List.of();
        }

        @Override
        public List<ProductSubscriptionLine> productSubscriptions(LocalDate from, LocalDate to) {
            subscriptionCalls.add(List.of(from, to));
            return lines;
        }
    }
}
