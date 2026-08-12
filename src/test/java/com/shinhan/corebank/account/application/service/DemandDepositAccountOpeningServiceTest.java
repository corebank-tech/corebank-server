package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.DemandDepositAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.IssueAccountNumberUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandDepositAccountOpeningServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;

    private static final String ACCOUNT_NUMBER =
            "088100000001";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final LocalDateTime OPENED_DATE =
            LocalDateTime.of(
                    2026,
                    8,
                    12,
                    13,
                    0
            );

    @Mock
    private IssueAccountNumberUseCase issueAccountNumberUseCase;

    @Mock
    private AccountPersistencePort accountPersistencePort;

    private DemandDepositAccountOpeningService
            demandDepositAccountOpeningService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T13:00:00Z"),
                ZoneOffset.UTC
        );

        demandDepositAccountOpeningService =
                new DemandDepositAccountOpeningService(
                        issueAccountNumberUseCase,
                        accountPersistencePort,
                        clock
                );
    }

    @Test
    @DisplayName("회원가입용 입출금계좌를 개설한다")
    void openDemandDepositAccount() {
        // given
        DemandDepositAccountOpeningCommand command =
                new DemandDepositAccountOpeningCommand(
                        CUSTOMER_ID,
                        PASSWORD_HASH
                );

        when(issueAccountNumberUseCase.issue(
                AccountType.DEMAND_DEPOSIT,
                null
        )).thenReturn(ACCOUNT_NUMBER);

        when(accountPersistencePort.save(any(Account.class)))
                .thenAnswer(invocation ->
                        savedAccount(
                                invocation.getArgument(0)
                        )
                );

        // when
        AccountOpeningResult result =
                demandDepositAccountOpeningService.open(command);

        // then
        assertThat(result.accountId())
                .isEqualTo(ACCOUNT_ID);

        assertThat(result.accountNumber())
                .isEqualTo(ACCOUNT_NUMBER);

        verify(issueAccountNumberUseCase)
                .issue(
                        AccountType.DEMAND_DEPOSIT,
                        null
                );

        ArgumentCaptor<Account> accountCaptor =
                ArgumentCaptor.forClass(Account.class);

        verify(accountPersistencePort)
                .save(accountCaptor.capture());

        Account account = accountCaptor.getValue();

        assertThat(account.getCustomerId())
                .isEqualTo(CUSTOMER_ID);

        assertThat(account.getProductId())
                .isNull();

        assertThat(account.getAccountType())
                .isEqualTo(AccountType.DEMAND_DEPOSIT);

        assertThat(account.getPasswordHash())
                .isEqualTo(PASSWORD_HASH);

        assertThat(account.getOpenedDate())
                .isEqualTo(OPENED_DATE);

        assertThat(account.getMaturityDate())
                .isNull();

        assertThat(account.getBalance())
                .isZero();

        assertThat(account.getStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.getPasswordFailureCount())
                .isZero();

        assertThat(account.isPasswordLocked())
                .isFalse();

        assertThat(account.isWithdrawalRegistered())
                .isFalse();
    }

    private Account savedAccount(Account account) {
        return Account.reconstitute(
                ACCOUNT_ID,
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getProductId(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getPasswordHash(),
                account.getPasswordFailureCount(),
                account.isPasswordLocked(),
                account.getAlias(),
                account.getDisplayOrder(),
                account.isWithdrawalRegistered(),
                account.getWithdrawalRegisteredAt(),
                account.getOpenedDate(),
                account.getMaturityDate(),
                account.getClosedDate(),
                account.getLastTransactionAt(),
                0L,
                OPENED_DATE,
                OPENED_DATE
        );
    }
}