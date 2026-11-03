package com.shinhan.corebank.account.api;

import java.time.LocalDate;

/** 중도해지할 ACTIVE 예적금 계좌의 소유권, 유형, 만기 전 여부 검증 계약. */
public interface EarlyTerminationAccountQueryUseCase {
    EarlyTerminationAccountView requireEarlyTerminable(Long accountId, Long customerId, LocalDate terminationDate);
}
