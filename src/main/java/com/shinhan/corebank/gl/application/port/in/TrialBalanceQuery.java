package com.shinhan.corebank.gl.application.port.in;

import java.time.LocalDate;

/** 시산표 조회기간. 양 끝을 포함하고, 기준은 분개의 거래일(trade_date)이다. */
public record TrialBalanceQuery(LocalDate fromDate, LocalDate toDate) {}
