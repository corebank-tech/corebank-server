package com.shinhan.infoapi.application.service;

import com.shinhan.infoapi.application.port.in.MartQueryUseCase;
import com.shinhan.infoapi.application.port.out.MartReadPort;
import com.shinhan.infoapi.domain.DepositBalanceTrend;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import com.shinhan.infoapi.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public class MartQueryService implements MartQueryUseCase {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final MartReadPort martReadPort;

    public MartQueryService(MartReadPort martReadPort) {
        this.martReadPort = martReadPort;
    }

    // to 가 없으면 asOf, from 이 없으면 to 에서 30일 전이다. asOf 뒤는 아직 빠짐없이 적재되지 않았으므로 asOf 까지만 준다.
    @Override
    public DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
        Optional<LocalDate> asOf = martReadPort.latestAsOf();
        if (asOf.isEmpty()) {
            return DepositBalanceTrend.noLoadHistory();
        }
        LocalDate end = (to == null || to.isAfter(asOf.get())) ? asOf.get() : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_WINDOW_DAYS) : from;
        if (start.isAfter(end)) {
            return new DepositBalanceTrend(asOf.get(), List.of());
        }
        return new DepositBalanceTrend(asOf.get(), martReadPort.depositBalances(start, end));
    }

    // month 가 없으면 asOf 가 속한 달이고, 그 달 안에서 asOf 까지만 센다.
    @Override
    public ProductSubscriptionSummary productSubscriptions(YearMonth month) {
        Optional<LocalDate> asOf = martReadPort.latestAsOf();
        if (asOf.isEmpty()) {
            return ProductSubscriptionSummary.noLoadHistory(month);
        }
        YearMonth target = month == null ? YearMonth.from(asOf.get()) : month;
        LocalDate start = target.atDay(1);
        LocalDate monthEnd = target.atEndOfMonth();
        LocalDate end = monthEnd.isAfter(asOf.get()) ? asOf.get() : monthEnd;
        if (start.isAfter(end)) {
            return new ProductSubscriptionSummary(asOf.get(), target, 0L, 0L, List.of());
        }
        List<ProductSubscriptionLine> lines = martReadPort.productSubscriptions(start, end);
        long subscribed = lines.stream()
                .mapToLong(ProductSubscriptionLine::subscribedCount)
                .sum();
        long closed =
                lines.stream().mapToLong(ProductSubscriptionLine::closedCount).sum();
        return new ProductSubscriptionSummary(asOf.get(), target, subscribed, closed, lines);
    }
}
