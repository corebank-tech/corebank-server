package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountType;

public interface IssueAccountNumberUseCase {
    String issue(
            AccountType accountType,
            Long productId
    );
}
