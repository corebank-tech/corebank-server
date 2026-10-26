package com.shinhan.corebank.info.application.port.in;

import java.time.YearMonth;

// month 를 생략하면 asOf 가 속한 달이다(info_mart.md §5-3).
public record ProductSubscriptionsQuery(YearMonth month) {}
