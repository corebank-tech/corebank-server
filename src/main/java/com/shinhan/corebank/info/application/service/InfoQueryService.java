package com.shinhan.corebank.info.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.InfoQueryUseCase;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InfoQueryService implements InfoQueryUseCase {

    private static final int MAX_RANGE_DAYS = 365;

    private final InfoMartPort infoMartPort;

    @Override
    public DepositBalanceTrend depositBalances(DepositBalancesQuery query) {
        validatePeriod(query.from(), query.to());
        return infoMartPort.depositBalances(query.from(), query.to());
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(ProductSubscriptionsQuery query) {
        return infoMartPort.productSubscriptions(query.month());
    }

    // 기간 상한은 정보계 API 호출 전에 막는다(CMN0003·CMN0004). 하나만 준 경우는 정보계 API가 asOf 기준으로 채운다.
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
