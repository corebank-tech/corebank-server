package com.shinhan.corebank.subscription.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예적금 만기해지 결과.
 *
 * @param principal 이자 지급 전 상품 계좌 원금 잔액
 * @param grossInterest 세전이자
 * @param tax 원천징수 세금 합계
 * @param netInterest 세후이자
 * @param totalTransferred 고객 출금계좌로 이체된 원리금 합계
 * @param transactionNumber MATURITY 원리금 이체 거래번호
 * @param tradeDate MATURITY 출금 원장의 거래일
 * @param closedAt 실제 해지 처리 시각(KST)
 */
public record MaturityTerminationResult(
        Long accountId,
        Long withdrawalAccountId,
        long principal,
        long grossInterest,
        long tax,
        long netInterest,
        long totalTransferred,
        String transactionNumber,
        LocalDate tradeDate,
        LocalDateTime closedAt) {}
