package com.shinhan.corebank.account.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum AccountType {
    DEMAND_DEPOSIT(false),
    TIME_DEPOSIT(true),
    INSTALLMENT_SAVINGS(true);

    private final boolean maturityEligible;

    AccountType(boolean maturityEligible) {
        this.maturityEligible = maturityEligible;
    }

    public boolean isMaturityEligible() {
        return maturityEligible;
    }

    public static Set<AccountType> maturityEligibleTypes() {
        return Arrays.stream(values()).filter(AccountType::isMaturityEligible).collect(Collectors.toUnmodifiableSet());
    }
}
