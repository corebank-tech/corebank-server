package com.shinhan.corebank.info.application.port.in;

import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;

// 관리자 정보계 조회(PH-77, ADM-03). 응답 스키마 정본은 docs/phase2/info_mart.md §5다.
public interface InfoQueryUseCase {

    DepositBalanceTrend depositBalances(DepositBalancesQuery query);

    ProductSubscriptionSummary productSubscriptions(ProductSubscriptionsQuery query);
}
