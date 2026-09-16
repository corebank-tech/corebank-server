package com.shinhan.corebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountWithdrawalRegistrationTest {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Test
    @DisplayName("출금계좌 등록을 삭제하면 등록 여부와 등록 시각을 초기화한다")
    void unregisterWithdrawalAccount() {
        // given
        LocalDateTime registeredAt = LocalDateTime.of(2026, 8, 19, 14, 30);

        Account account = createAccount(true, registeredAt);

        // when
        account.unregisterWithdrawalAccount();

        // then
        assertThat(account.isWithdrawalRegistered()).isFalse();

        assertThat(account.getWithdrawalRegisteredAt()).isNull();
    }

    @Test
    @DisplayName("이미 미등록 상태인 출금계좌 삭제는 아무 변경 없이 성공한다")
    void unregisterAlreadyUnregisteredAccount() {
        // given
        Account account = createAccount(false, null);

        // when
        account.unregisterWithdrawalAccount();

        // then
        assertThat(account.isWithdrawalRegistered()).isFalse();

        assertThat(account.getWithdrawalRegisteredAt()).isNull();
    }

    private Account createAccount(boolean withdrawalRegistered, LocalDateTime withdrawalRegisteredAt) {
        return Account.reconstitute(
                10L,
                "088100000001",
                1L,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                withdrawalRegistered,
                withdrawalRegisteredAt,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null,
                null,
                null,
                0L,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 10, 15, 0));
    }
}
