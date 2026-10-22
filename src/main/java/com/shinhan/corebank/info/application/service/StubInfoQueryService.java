package com.shinhan.corebank.info.application.service;

import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.InfoQueryUseCase;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.util.List;
import org.springframework.stereotype.Service;

// 마트가 아직 없어 적재 이력 없음 응답을 돌려준다(PH-77 ①). 실제 구현(#704)이 이 서비스를 대신한다.
@Service
public class StubInfoQueryService implements InfoQueryUseCase {

    @Override
    public DepositBalanceTrend depositBalances(DepositBalancesQuery query) {
        return new DepositBalanceTrend(null, List.of());
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(ProductSubscriptionsQuery query) {
        return new ProductSubscriptionSummary(null, query.month(), null, null, List.of());
    }
}
