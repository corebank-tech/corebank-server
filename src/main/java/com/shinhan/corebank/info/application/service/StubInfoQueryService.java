package com.shinhan.corebank.info.application.service;

import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.InfoQueryUseCase;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

// 마트를 읽지 않고 설계서 §5의 예시값을 그대로 돌려준다(PH-77 ①, #675). 조회 조건은 무시한다.
// FE 가 응답 모양으로 먼저 연동하게 하는 임시 구현이고, 실제 구현(#704)이 이 서비스를 대신한다.
@Service
public class StubInfoQueryService implements InfoQueryUseCase {

    private static final LocalDate SAMPLE_AS_OF = LocalDate.of(2026, 10, 28);

    @Override
    public DepositBalanceTrend depositBalances(DepositBalancesQuery query) {
        return new DepositBalanceTrend(
                SAMPLE_AS_OF,
                List.of(
                        new DepositBalanceTrend.Point(LocalDate.of(2026, 10, 1), 1_523_400_000_000L),
                        new DepositBalanceTrend.Point(LocalDate.of(2026, 10, 2), 1_524_100_000_000L)));
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(ProductSubscriptionsQuery query) {
        return new ProductSubscriptionSummary(
                SAMPLE_AS_OF,
                YearMonth.of(2026, 10),
                120L,
                5L,
                List.of(new ProductSubscriptionSummary.Line(3L, "DEP-12M", "쏠편한 정기예금", "DEPOSIT", 80L, 3L)));
    }
}
