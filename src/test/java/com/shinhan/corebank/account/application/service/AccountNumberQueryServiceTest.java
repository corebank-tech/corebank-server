package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountNumberQueryServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String ACCOUNT_NUMBER = "088100000001";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Mock
    private AccountPersistencePort
            accountPersistencePort;

    private AccountNumberQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountNumberQueryService(
                accountPersistencePort
        );
    }

    @Test
    @DisplayName("본인 계좌면 계좌번호를 반환한다")
    void findAccountNumber() {
        // given
        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(createAccount()));

        // when
        Optional<String> accountNumber =
                service.findAccountNumber(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                );

        // then
        assertThat(accountNumber)
                .contains(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("본인 계좌가 아니거나 없으면 빈 값을 반환한다")
    void findAccountNumberNotOwned() {
        // given
        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.empty());

        // when
        Optional<String> accountNumber =
                service.findAccountNumber(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                );

        // then
        assertThat(accountNumber).isEmpty();
    }

    private Account createAccount() {
        return Account.reconstitute(
                ACCOUNT_ID,
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                false,
                null,
                LocalDateTime.of(
                        2026, 8, 1, 10, 0
                ),
                null,
                null,
                LocalDateTime.of(
                        2026, 8, 10, 15, 0
                ),
                0L,
                LocalDateTime.of(
                        2026, 8, 1, 10, 0
                ),
                LocalDateTime.of(
                        2026, 8, 10, 15, 0
                )
        );
    }
}
