package com.shinhan.corebank.subscription.api;

import java.time.LocalDate;

/**
 * 이자 지급 결과.
 *
 * transactionNumber와 tradeDate는 원장 기표 결과를 그대로 사용한다.
 * 지급 이자가 0원이면 원장을 생성하지 않으므로 두 값은 null일 수 있다.
 */
public record InterestPaymentResult(long gross, long tax, long net, String transactionNumber, LocalDate tradeDate) {}
