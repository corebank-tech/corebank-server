package com.shinhan.corebank.info.adapter.out.api;

import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 정보계 API 주소가 설정되지 않았을 때 적재 이력 없음 응답을 돌려준다(PH-77 ③). 설정 누락을 놓치지 않게 시작할 때 경고를 남긴다.
public class NoLoadHistoryInfoMart implements InfoMartPort {

    private static final Logger log = LoggerFactory.getLogger(NoLoadHistoryInfoMart.class);

    public NoLoadHistoryInfoMart() {
        log.warn("app.info-api.base-url 이 설정되지 않아 정보계 조회는 적재 이력 없음 응답만 돌려줍니다.");
    }

    @Override
    public DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
        return DepositBalanceTrend.noLoadHistory();
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(YearMonth month) {
        return ProductSubscriptionSummary.noLoadHistory(month);
    }
}
