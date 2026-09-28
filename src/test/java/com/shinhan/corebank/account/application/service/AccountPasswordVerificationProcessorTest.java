package com.shinhan.corebank.account.application.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountPasswordVerificationProcessorTest {

    private final AccountPersistencePort persistencePort = mock(AccountPersistencePort.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountPasswordVerificationProcessor processor =
            new AccountPasswordVerificationProcessor(persistencePort, passwordEncoder);

    @Test
    @DisplayName("더미 검증은 BCrypt 비교만 수행하고 계좌 상태를 조회하거나 변경하지 않는다")
    void performsDummyVerificationWithoutChangingAccountState() {
        processor.performDummyVerification("1234");

        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("1234"), anyString());
        verify(persistencePort, never())
                .findByAccountIdAndCustomerIdForUpdate(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(persistencePort, never()).updatePasswordState(org.mockito.ArgumentMatchers.any());
    }
}
