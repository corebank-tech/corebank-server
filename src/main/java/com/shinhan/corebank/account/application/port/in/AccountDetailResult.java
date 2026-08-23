package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// 계좌상세 화면에 필요한 계좌 상태와 잔액정보를 반환한다.
public record AccountDetailResult(
        OffsetDateTime asOf,
        Long accountId,
        String accountName,
        String accountNumber,
        long balance,
        long availableBalance,
        LocalDate openedDate,
        AccountStatus status,
        int passwordFailureCount,
        boolean passwordLocked
) {
}
