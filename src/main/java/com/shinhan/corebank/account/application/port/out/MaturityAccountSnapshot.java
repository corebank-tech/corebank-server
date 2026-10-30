package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import java.time.LocalDate;

/**
 * account 내부에서 사용하는 읽기 전용 계좌 스냅샷.
 * JPA 관리 엔티티가 아니다.
 */
public record MaturityAccountSnapshot(
        Long accountId,
        Long productId,
        AccountType accountType,
        AccountStatus status,
        LocalDate maturityDate,
        boolean withdrawalRegistered) {}
