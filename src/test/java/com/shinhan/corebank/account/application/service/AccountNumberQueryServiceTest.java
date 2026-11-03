package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNumberQueryServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String ACCOUNT_NUMBER = "088100000001";

    @Mock
    private AccountPersistencePort accountPersistencePort;

    private AccountNumberQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountNumberQueryService(accountPersistencePort);
    }

    @Test
    @DisplayName("본인 계좌면 엔티티 로딩 없이 계좌번호만 반환한다")
    void findAccountNumber() {
        when(accountPersistencePort.findAccountNumberByIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(ACCOUNT_NUMBER));

        Optional<String> accountNumber = service.findAccountNumber(ACCOUNT_ID, CUSTOMER_ID);

        assertThat(accountNumber).contains(ACCOUNT_NUMBER);

        verify(accountPersistencePort, never()).findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID);
    }

    @Test
    @DisplayName("본인 계좌가 아니거나 없으면 빈 값을 반환한다")
    void findAccountNumberNotOwned() {
        when(accountPersistencePort.findAccountNumberByIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        Optional<String> accountNumber = service.findAccountNumber(ACCOUNT_ID, CUSTOMER_ID);

        assertThat(accountNumber).isEmpty();
    }
}
