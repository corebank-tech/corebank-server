package com.shinhan.corebank.info.application.port.out;

import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import java.time.LocalDate;
import java.time.YearMonth;

// 정보계 API(온프레미스 마트) 조회 포트(PH-77 ③). from·to·month 는 생략하면 정보계 API가 asOf 기준으로 채운다.
public interface InfoMartPort {

    DepositBalanceTrend depositBalances(LocalDate from, LocalDate to);

    ProductSubscriptionSummary productSubscriptions(YearMonth month);
}
