package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.AccountNumberSequence;
import com.shinhan.corebank.account.domain.AccountType;

import java.util.Optional;

public interface AccountNumberSequencePort {
    Optional<AccountNumberSequence> findForUpdate(
            String bankCode,
            AccountType accountType,
            Long productId
    );
    void update(
            AccountNumberSequence accountNumberSequence
    );
}
