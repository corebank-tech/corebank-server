package com.shinhan.corebank.account.api;

import java.time.LocalDate;

/** 중도해지 가능한 ACTIVE 상품 계좌를 account 도메인이 검증한 결과. */
public record EarlyTerminationAccountView(
        Long accountId, Long productId, LocalDate maturityDate, boolean timeDeposit) {}
