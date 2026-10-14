package com.shinhan.corebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountAvailableBalanceTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    private Account account(AccountStatus status, long holdAmount, boolean passwordLocked) {

        return Account.reconstitute(
                101L,
                "088100000001",
                1L,
                null,
                AccountType.DEMAND_DEPOSIT,
                100_000L,
                holdAmount,
                status,
                "test-password-hash",
                passwordLocked ? 5 : 0,
                passwordLocked,
                null,
                null,
                false,
                null,
                OPENED_AT,
                null,
                status == AccountStatus.CLOSED ? OPENED_AT.plusDays(1) : null,
                null,
                0L,
                OPENED_AT,
                OPENED_AT);
    }

    @Test
    void subtractsHoldAmountFromBalance() {
        Account account = account(AccountStatus.ACTIVE, 30_000L, false);

        assertThat(account.getBalance()).isEqualTo(100_000L);
        assertThat(account.getHoldAmount()).isEqualTo(30_000L);
        assertThat(account.getAvailableBalance()).isEqualTo(70_000L);
    }

    @Test
    void returnsZeroForSuspendedAccount() {
        Account account = account(AccountStatus.SUSPENDED, 30_000L, false);

        assertThat(account.getAvailableBalance()).isZero();
        assertThat(account.getBalance()).isEqualTo(100_000L);
    }

    @Test
    void returnsZeroForMaturedAccount() {
        Account account = account(AccountStatus.MATURED, 0L, false);

        assertThat(account.getAvailableBalance()).isZero();
    }

    @Test
    void passwordLockDoesNotReduceAvailableBalance() {
        Account account = account(AccountStatus.ACTIVE, 30_000L, true);

        assertThat(account.isPasswordLocked()).isTrue();
        assertThat(account.getAvailableBalance()).isEqualTo(70_000L);
    }

    @Test
    void rejectsHoldAmountGreaterThanBalance() {
        assertThatThrownBy(() -> account(AccountStatus.ACTIVE, 100_001L, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNegativeHoldAmount() {
        assertThatThrownBy(() -> account(AccountStatus.ACTIVE, -1L, false)).isInstanceOf(IllegalStateException.class);
    }
}
