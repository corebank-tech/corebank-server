package com.shinhan.corebank.account.api;

import java.time.LocalDate;
import java.util.List;

// 기존 은행 원장의 전체 계좌를 신규 고객에게 등록하도록 전달한다.
public record RegisterExistingAccountsCommand(
        Long customerId,
        List<AccountData> accounts
) {
    public RegisterExistingAccountsCommand {
        accounts = List.copyOf(accounts);
    }

    // 기존 은행 원장의 계좌 한 건을 로컬 계좌 등록 형식으로 표현한다.
    public record AccountData(
            String accountNumber,
            String accountType,
            Long productId,
            long balance,
            String status,
            String passwordHash,
            LocalDate openedDate,
            LocalDate maturityDate
    ) {
    }
}
