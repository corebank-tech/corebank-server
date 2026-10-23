package com.shinhan.corebank.account.api;

import java.time.LocalDate;

/**
 * 만기해지 사전 검증을 통과한 계좌 정보.
 *
 * 계좌 상태와 유형은 account 내부에서 검증하며,
 * 다른 도메인에는 검증에 필요한 결과만 공개한다.
 */
public record MaturityAccountView(Long accountId, Long productId, LocalDate maturityDate, boolean timeDeposit) {}
