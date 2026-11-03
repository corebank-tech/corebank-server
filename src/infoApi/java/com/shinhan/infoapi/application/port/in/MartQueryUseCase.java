package com.shinhan.infoapi.application.port.in;

import com.shinhan.infoapi.domain.DepositBalanceTrend;
import com.shinhan.infoapi.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;

public interface MartQueryUseCase {

    DepositBalanceTrend depositBalances(LocalDate from, LocalDate to);

    ProductSubscriptionSummary productSubscriptions(YearMonth month);
}
