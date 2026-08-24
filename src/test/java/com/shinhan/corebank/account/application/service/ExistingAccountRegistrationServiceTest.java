package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.RegisterExistingAccountsCommand;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 기존 은행 원장의 전체 계좌가 신규 고객 소유로 등록되는지 검증한다.
@ExtendWith(MockitoExtension.class)
class ExistingAccountRegistrationServiceTest {

    @Mock AccountPersistencePort accountPersistencePort;

    @Test
    void importsAllAccountsForNewCustomer() {
        ExistingAccountRegistrationService service =
                new ExistingAccountRegistrationService(
                        accountPersistencePort
                );
        RegisterExistingAccountsCommand.AccountData first = account(
                "110123456789",
                1_000_000L,
                LocalDate.of(2024, 1, 10)
        );
        RegisterExistingAccountsCommand.AccountData second = account(
                "110987654321",
                500_000L,
                LocalDate.of(2025, 3, 20)
        );

        service.registerAll(new RegisterExistingAccountsCommand(
                101L,
                List.of(first, second)
        ));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountPersistencePort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Account::getAccountNumber)
                .containsExactly("110123456789", "110987654321");
        assertThat(captor.getAllValues())
                .allSatisfy(account -> {
                    assertThat(account.getCustomerId()).isEqualTo(101L);
                    assertThat(account.getProductId()).isNull();
                    assertThat(account.getMaturityDate()).isNull();
                });
    }

    @Test
    void rejectsAlreadyRegisteredAccountNumberWithBusinessError() {
        ExistingAccountRegistrationService service =
                new ExistingAccountRegistrationService(
                        accountPersistencePort
                );
        given(accountPersistencePort.existsByAccountNumber("110123456789"))
                .willReturn(true);

        assertThatThrownBy(() -> service.registerAll(
                new RegisterExistingAccountsCommand(
                        101L,
                        List.of(account(
                                "110123456789",
                                1_000_000L,
                                LocalDate.of(2024, 1, 10)
                        ))
                )
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(AccountErrorCode.DUPLICATE_EXISTING_ACCOUNT));

        verify(accountPersistencePort, never()).save(any());
    }

    private RegisterExistingAccountsCommand.AccountData account(
            String accountNumber,
            long balance,
            LocalDate openedDate
    ) {
        return new RegisterExistingAccountsCommand.AccountData(
                accountNumber,
                "DEMAND_DEPOSIT",
                null,
                balance,
                "ACTIVE",
                "$2y$10$hash",
                openedDate,
                null
        );
    }
}
