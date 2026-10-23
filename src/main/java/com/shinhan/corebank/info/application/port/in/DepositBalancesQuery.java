package com.shinhan.corebank.info.application.port.in;

import java.time.LocalDate;

// from·to 는 생략할 수 있다. 생략하면 asOf 부터 거슬러 30일이다(info_mart.md §5-2).
public record DepositBalancesQuery(LocalDate from, LocalDate to) {}
