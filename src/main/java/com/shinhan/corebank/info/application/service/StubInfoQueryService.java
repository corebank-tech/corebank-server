package com.shinhan.corebank.info.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.InfoQueryUseCase;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

// 마트가 아직 없어 적재 이력 없음 응답을 돌려준다(PH-77 ①). 실제 구현(#704)이 이 서비스를 대신한다.
@Service
public class StubInfoQueryService implements InfoQueryUseCase {

    private static final int MAX_RANGE_DAYS = 365;

    @Override
    public DepositBalanceTrend depositBalances(DepositBalancesQuery query) {
        validatePeriod(query.from(), query.to());
        return DepositBalanceTrend.noLoadHistory();
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(ProductSubscriptionsQuery query) {
        return ProductSubscriptionSummary.noLoadHistory(query.month());
    }

    // 기간 상한은 마트 조회와 무관한 입력 규칙이라 스텁에서도 막는다(CMN0003·CMN0004). 하나만 준 경우는 asOf 기본값이 필요해 #704에서 정한다.
    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
        }
    }
}
